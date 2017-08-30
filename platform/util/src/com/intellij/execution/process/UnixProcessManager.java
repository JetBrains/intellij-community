/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.ReflectionUtil;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

/**
 * @author traff
 */
public class UnixProcessManager {
  private static final Logger LOG = Logger.getInstance(UnixProcessManager.class);

  public static final int SIGINT = 2;
  public static final int SIGKILL = 9;
  public static final int SIGTERM = 15;

  private static CLib C_LIB;

  static {
    try {
      if (!Platform.isWindows()) {
        C_LIB = Native.loadLibrary("c", CLib.class);
      }
    }
    catch (Throwable e) {
      Logger log = Logger.getInstance(UnixProcessManager.class);
      log.warn("Can't load c library", e);
      C_LIB = null;
    }
  }

  private UnixProcessManager() { }

  public static int getProcessPid(@NotNull Process process) {
    try {
      Integer pid = ReflectionUtil.getField(process.getClass(), process, int.class, "pid");
      return ObjectUtils.assertNotNull(pid);
    }
    catch (Exception e) {
      throw new IllegalStateException("Cannot get PID from instance of " + process.getClass() + ", OS: " + SystemInfo.OS_NAME, e);
    }
  }

  public static int getCurrentProcessId() {
    return C_LIB != null ? C_LIB.getpid() : 0;
  }

  public static int sendSignal(int pid, int signal) {
    checkCLib();
    return C_LIB.kill(pid, signal);
  }

  private static void checkCLib() {
    if (C_LIB == null) {
      throw new IllegalStateException("Couldn't load c library, OS: " + SystemInfo.OS_NAME + ", isUnix: " + SystemInfo.isUnix);
    }
  }

  public static boolean sendSigIntToProcessTree(Process process) {
    return sendSignalToProcessTree(process, SIGINT);
  }

  public static boolean sendSigKillToProcessTree(Process process) {
    return sendSignalToProcessTree(process, SIGKILL);
  }

  private static boolean doSendSignalToProcessTree(int process_pid, int signal) {
    checkCLib();

    final int our_pid = C_LIB.getpid();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Sending signal " + signal + " to process tree with root PID " + process_pid);
    }

    final Ref<Integer> foundPid = new Ref<Integer>();
    final ProcessInfo processInfo = new ProcessInfo();
    final List<Integer> childrenPids = new ArrayList<Integer>();

    findChildProcesses(our_pid, process_pid, foundPid, processInfo, childrenPids);

    // result is true if signal was sent to at least one process
    final boolean result;
    if (!foundPid.isNull()) {
      processInfo.killProcTree(foundPid.get(), signal, UNIX_KILLER);
      result = true;
    }
    else {
      for (Integer pid : childrenPids) {
        processInfo.killProcTree(pid, signal, UNIX_KILLER);
      }
      result = !childrenPids.isEmpty(); //we've tried to kill at least one process
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Done sending signal " + signal + "; found: " + foundPid.get() + ", children: " + childrenPids + ", result: " + result);
    }

    return result;
  }

  /**
   * Sends signal to every child process of a tree root process
   *
   * @param process tree root process
   */
  public static boolean sendSignalToProcessTree(@NotNull Process process, int signal) {
    try {
      final int process_pid = getProcessPid(process);
      return doSendSignalToProcessTree(process_pid, signal);
    }
    catch (Exception e) {
      //If we fail somehow just return false
      LOG.warn("Error killing the process", e);
      return false;
    }
  }

  private static void findChildProcesses(final int our_pid,
                                         final int process_pid,
                                         final Ref<Integer> foundPid,
                                         final ProcessInfo processInfo,
                                         final List<Integer> childrenPids) {
    final Ref<Boolean> ourPidFound = Ref.create(false);
    processPSOutput(getPSCmd(false), new Processor<String>() {
      @Override
      public boolean process(String s) {
        StringTokenizer st = new StringTokenizer(s, " ");

        int parent_pid = Integer.parseInt(st.nextToken());
        int pid = Integer.parseInt(st.nextToken());

        processInfo.register(pid, parent_pid);

        if (parent_pid == process_pid) {
          childrenPids.add(pid);
        }

        if (pid == our_pid) {
          ourPidFound.set(true);
        }
        else if (pid == process_pid) {
          if (parent_pid == our_pid || our_pid == -1) {
            foundPid.set(pid);
          }
          else {
            throw new IllegalStateException("Process (pid=" + process_pid + ") is not our child(our pid = " + our_pid + ")");
          }
        }
        return false;
      }
    });
    if (our_pid != -1 && !ourPidFound.get()) {
      throw new IllegalStateException("IDE pid is not found in ps list(" + our_pid + ")");
    }
  }

  public static void processPSOutput(String[] cmd, Processor<String> processor) {
    processCommandOutput(cmd, processor, true, true);
  }

  public static void processCommandOutput(String[] cmd, Processor<String> processor, boolean skipFirstLine, boolean throwOnError) {
    try {
      Process p = Runtime.getRuntime().exec(cmd);
      processCommandOutput(p, processor, skipFirstLine, throwOnError);
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void processCommandOutput(Process process, Processor<String> processor, boolean skipFirstLine, boolean throwOnError) throws IOException {
    BufferedReader stdOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
    try {
      BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      try {
        if (skipFirstLine) {
          stdOutput.readLine(); //ps output header
        }
        String s;
        while ((s = stdOutput.readLine()) != null) {
          processor.process(s);
        }

        StringBuilder errorStr = new StringBuilder();
        while ((s = stdError.readLine()) != null) {
          if (s.contains("environment variables being ignored")) {  // PY-8160
            continue;
          }
          errorStr.append(s).append("\n");
        }
        if (throwOnError && errorStr.length() > 0) {
          throw new IOException("Error reading ps output:" + errorStr.toString());
        }
      }
      finally {
        stdError.close();
      }
    }
    finally {
      stdOutput.close();
    }
  }

  public static String[] getPSCmd(boolean commandLineOnly) {
    return getPSCmd(commandLineOnly, false);
  }

  public static String[] getPSCmd(boolean commandLineOnly, boolean isShortenCommand) {
    String psCommand = "/bin/ps";
    if (!new File(psCommand).isFile()) {
      psCommand = "ps";
    }
    if (SystemInfo.isLinux) {
      return new String[]{psCommand, "-e", "--format", commandLineOnly ? "%a" : "%P%p%a"};
    }
    else if (SystemInfo.isMac || SystemInfo.isFreeBSD) {
      final String command = isShortenCommand ? "comm" : "command";
      return new String[]{psCommand, "-ax", "-o", commandLineOnly ? command : "ppid,pid," + command};
    }
    else {
      throw new IllegalStateException(System.getProperty("os.name") + " is not supported.");
    }
  }

  private interface CLib extends Library {
    int getpid();
    int kill(int pid, int signal);
  }

  public static class ProcessInfo {
    private Map<Integer, List<Integer>> BY_PARENT = new TreeMap<Integer, List<Integer>>(); // pid -> list of children pids

    public void register(Integer pid, Integer parentPid) {
      List<Integer> children = BY_PARENT.get(parentPid);
      if (children == null) children = new LinkedList<Integer>();
      children.add(pid);
      BY_PARENT.put(parentPid, children);
    }

    public void killProcTree(int pid, int signal, ProcessKiller killer) {
      List<Integer> children = BY_PARENT.get(pid);
      if (children != null) {
        for (int child : children) killProcTree(child, signal, killer);
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Sending signal " + signal + " to PID " + pid);
      }
      killer.kill(pid, signal);
    }
  }

  public interface ProcessKiller {
    void kill(int pid, int signal);
  }

  private static final ProcessKiller UNIX_KILLER = new ProcessKiller() {
    @Override
    public void kill(int pid, int signal) {
      sendSignal(pid, signal);
    }
  };
}