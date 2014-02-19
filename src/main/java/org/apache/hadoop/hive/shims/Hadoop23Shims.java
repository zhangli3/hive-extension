/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.shims;

import java.io.IOException;
import java.lang.Integer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.Trash;
import org.apache.hadoop.hive.shims.HadoopShims.JobTrackerState;
import org.apache.hadoop.hive.shims.HadoopShimsSecure;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.mapred.MiniMRCluster;
import org.apache.hadoop.mapred.ClusterStatus;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.task.JobContextImpl;
import org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl;
import org.apache.hadoop.mapreduce.util.HostUtil;
import org.apache.hadoop.util.Progressable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
/**
 * Implemention of shims against Hadoop 0.23.0.
 */
public class Hadoop23Shims extends HadoopShimsSecure {

  @Override
  public String getTaskAttemptLogUrl(JobConf conf,
	        String taskTrackerHttpAddress, String taskAttemptId)
	        throws MalformedURLException {
	        if (isMR2(conf)) {
	            // if the cluster is running in MR2 mode, return null
	            LOG.warn(
	                "Can't fetch tasklog: TaskLogServlet is not supported in MR2 mode.");

	            return null;
	        } else {
	            // MR2 doesn't have TaskLogServlet class, so need to
	            String taskLogURL = null;

	            try {
	                Class<?> taskLogClass = Class.forName("TaskLogServlet");
	                Method taskLogMethod = taskLogClass.getDeclaredMethod("getTaskLogUrl",
	                        String.class, String.class, String.class);
	                URL taskTrackerHttpURL = new URL(taskTrackerHttpAddress);
	                taskLogURL = (String) taskLogMethod.invoke(null,
	                        taskTrackerHttpURL.getHost(),
	                        Integer.toString(taskTrackerHttpURL.getPort()),
	                        taskAttemptId);
	            } catch (IllegalArgumentException e) {
	                throw new MalformedURLException(
	                    "Could not execute getTaskLogUrl " + e.getCause());
	            } catch (IllegalAccessException e) {
	                throw new MalformedURLException(
	                    "Could not execute getTaskLogUrl " + e.getCause());
	            } catch (InvocationTargetException e) {
	                throw new MalformedURLException(
	                    "Could not execute getTaskLogUrl " + e.getCause());
	            } catch (SecurityException e) {
	                throw new MalformedURLException(
	                    "Could not execute getTaskLogUrl " + e.getCause());
	            } catch (NoSuchMethodException e) {
	                throw new MalformedURLException(
	                    "Method getTaskLogUrl not found " + e.getCause());
	            } catch (ClassNotFoundException e) {
	                LOG.warn(
	                    "Can't fetch tasklog: TaskLogServlet is not supported in MR2 mode.");
	            }

	            return taskLogURL;
	        }
	    }

  @Override
  public JobTrackerState getJobTrackerState(ClusterStatus clusterStatus) throws Exception {
    JobTrackerState state;
    switch (clusterStatus.getJobTrackerStatus()) {
    case INITIALIZING:
      return JobTrackerState.INITIALIZING;
    case RUNNING:
      return JobTrackerState.RUNNING;
    default:
      String errorMsg = "Unrecognized JobTracker state: " + clusterStatus.getJobTrackerStatus();
      throw new Exception(errorMsg);
    }
  }

  @Override
  public org.apache.hadoop.mapreduce.TaskAttemptContext newTaskAttemptContext(Configuration conf, final Progressable progressable) {
    return new TaskAttemptContextImpl(conf, new TaskAttemptID()) {
      @Override
      public void progress() {
        progressable.progress();
      }
    };
  }

  @Override
  public org.apache.hadoop.mapreduce.JobContext newJobContext(Job job) {
    return new JobContextImpl(job.getConfiguration(), job.getJobID());
  }

  @Override
  public boolean isLocalMode(Configuration conf) {
      if (isMR2(conf)) {
          return "local".equals(conf.get("mapreduce.framework.name"));
      }

      return "local".equals(conf.get("mapred.job.tracker"));
  }

  @Override
  public String getJobLauncherRpcAddress(Configuration conf) {
      if (isMR2(conf)) {
          return conf.get("yarn.resourcemanager.address");
      } else {
          return conf.get("mapred.job.tracker");
      }
  }

  @Override
  public void setJobLauncherRpcAddress(Configuration conf, String val) {
      if (val.equals("local")) {
          // LocalClientProtocolProvider expects both parameters to be 'local'.
          if (isMR2(conf)) {
              conf.set("mapreduce.framework.name", val);
              conf.set("mapreduce.jobtracker.address", val);
          } else {
              conf.set("mapred.job.tracker", val);
          }
      } else {
          if (isMR2(conf)) {
              conf.set("yarn.resourcemanager.address", val);
          } else {
              conf.set("mapred.job.tracker", val);
          }
      }
  }

  @Override
  public String getJobLauncherHttpAddress(Configuration conf) {
      if (isMR2(conf)) {
          return conf.get("yarn.resourcemanager.webapp.address");
      } else {
          return conf.get("mapred.job.tracker.http.address");
      }
  }
  
  private boolean isMR2(Configuration conf) {
      return "yarn".equals(conf.get("mapreduce.framework.name"));
  }

  @Override
  public long getDefaultBlockSize(FileSystem fs, Path path) {
    return fs.getDefaultBlockSize(path);
  }

  @Override
  public short getDefaultReplication(FileSystem fs, Path path) {
    return fs.getDefaultReplication(path);
  }

  @Override
  public boolean moveToAppropriateTrash(FileSystem fs, Path path, Configuration conf)
          throws IOException {
    return Trash.moveToAppropriateTrash(fs, path, conf);
  }

  /**
   * Returns a shim to wrap MiniMrCluster
   */
  public MiniMrShim getMiniMrCluster(Configuration conf, int numberOfTaskTrackers, 
                                     String nameNode, int numDir) throws IOException {
    return new MiniMrShim(conf, numberOfTaskTrackers, nameNode, numDir);
  }

  /**
   * Shim for MiniMrCluster
   */
  public class MiniMrShim implements HadoopShims.MiniMrShim {

    private final MiniMRCluster mr;
    private final Configuration conf;

    public MiniMrShim(Configuration conf, int numberOfTaskTrackers, 
                      String nameNode, int numDir) throws IOException {
      this.conf = conf;

      JobConf jConf = new JobConf(conf);
      jConf.set("yarn.scheduler.capacity.root.queues", "default");
      jConf.set("yarn.scheduler.capacity.root.default.capacity", "100");

      mr = new MiniMRCluster(numberOfTaskTrackers, nameNode, numDir, null, null, jConf);
    }

    @Override
    public int getJobTrackerPort() throws UnsupportedOperationException {
      String address = conf.get("yarn.resourcemanager.address");
      address = StringUtils.substringAfterLast(address, ":");

      if (StringUtils.isBlank(address)) {
        throw new IllegalArgumentException("Invalid YARN resource manager port.");
      }
      
      return Integer.parseInt(address);
    }

    @Override
    public void shutdown() throws IOException {
      mr.shutdown();
    }
    
    @Override
    public void setupConfiguration(Configuration conf) {
      JobConf jConf = mr.createJobConf();
      for (Map.Entry<String, String> pair: jConf) {
	//System.out.println("XXX Var: "+pair.getKey() +"="+pair.getValue());
        //if (conf.get(pair.getKey()) == null) {
          conf.set(pair.getKey(), pair.getValue());
	  //}
      }
    }
  }
  
  // Don't move this code to the parent class. There's a binary
  // incompatibility between hadoop 1 and 2 wrt MiniDFSCluster and we
  // need to have two different shim classes even though they are
  // exactly the same.
  public HadoopShims.MiniDFSShim getMiniDfs(Configuration conf,
      int numDataNodes,
      boolean format,
      String[] racks) throws IOException {
    return new MiniDFSShim(new MiniDFSCluster(conf, numDataNodes, format, racks));
  }

  /**
   * MiniDFSShim.
   *
   */
  public class MiniDFSShim implements HadoopShims.MiniDFSShim {
    private final MiniDFSCluster cluster;

    public MiniDFSShim(MiniDFSCluster cluster) {
      this.cluster = cluster;
    }

    public FileSystem getFileSystem() throws IOException {
      return cluster.getFileSystem();
    }

    public void shutdown() {
      cluster.shutdown();
    }
  }
}
