// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.CurrentJavaVersion;
import com.intellij.util.Processor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.system.OS;
import com.sun.jna.Library;
import com.sun.jna.Native;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * Do not call this class directly - use [OSProcessUtil] or [Process] API instead.
 */
@ApiStatus.Internal
@SuppressWarnings("DeprecatedIsStillUsed")
public final class UnixProcessManager {
  private static final Logger LOG = Logger.getInstance(UnixProcessManager.class);

  private static final MethodHandle signalStringToIntConverter;

  static {
    try {
      Class<?> signalClass = Class.forName("sun.misc.Signal");
      MethodHandle signalConstructor =
        MethodHandles.publicLookup().findConstructor(signalClass, MethodType.methodType(void.class, String.class));
      MethodHandle getNumber = MethodHandles.publicLookup().findVirtual(signalClass, "getNumber", MethodType.methodType(int.class));
      signalStringToIntConverter = MethodHandles.filterReturnValue(signalConstructor, getNumber);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // We assume that the following 7 signals have portable number.
  // At least the Open Group mentions them in Shell & Utilities of the Base Specifications:
  //   - https://pubs.opengroup.org/onlinepubs/9699919799/utilities/kill.html
  //   - https://pubs.opengroup.org/onlinepubs/000095399/utilities/trap.html

  public static final int SIGHUP = 1;
  public static final int SIGINT = 2;
  public static final int SIGQUIT = 3;
  public static final int SIGABRT = 6;
  public static final int SIGKILL = 9;
  public static final int SIGALRM = 14;
  public static final int SIGTERM = 15;

  private UnixProcessManager() { }

  /** @deprecated use `Process#pid`*/
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static int getProcessId(@NotNull Process process) {
    try {
      if (CurrentJavaVersion.currentJavaVersion().feature >= 9 &&
          ("java.lang.ProcessImpl".equals(process.getClass().getName()) ||
           "com.pty4j.unix.UnixPtyProcess".equals(process.getClass().getName())
          )
      ) {
        return ((Long)Process.class.getMethod("pid").invoke(process)).intValue();
      }
      else {
        return Objects.requireNonNull(ReflectionUtil.getField(process.getClass(), process, int.class, "pid"));
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException("Failed to get PID from an instance of " + process.getClass() + ", OS: " + SystemInfo.OS_NAME, t);
    }
  }

  /** @deprecated use `ProcessHandle.current().pid()`*/
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static int getCurrentProcessId() {
    return Java8Helper.C_LIB != null ? Java8Helper.C_LIB.getpid() : 0;
  }

  /**
   * Retrieve the number value of a signal if it's one of those believed to have
   * the same constant value across all the systems.
   *
   * @param signalName without the 'SIG' prefix ('INT', not 'SIGINT')
   * @return -1 for unknown signal
   */
  public static int getPortableSignalNumber(@NotNull String signalName) {
    switch (signalName) {
      case "HUP":
        return SIGHUP;
      case "INT":
        return SIGINT;
      case "QUIT":
        return SIGQUIT;
      case "ABRT":
        return SIGABRT;
      case "KILL":
        return SIGKILL;
      case "ALRM":
        return SIGALRM;
      case "TERM":
        return SIGTERM;
      default:
        return -1;
    }
  }

  /**
   * @param signalName without the 'SIG' prefix ('INT', not 'SIGINT')
   * @return -1 for unknown signal
   */
  public static int getSignalNumber(@NotNull String signalName) {
    try {
      return (int)signalStringToIntConverter.invokeExact(signalName);
    }
    catch (IllegalArgumentException e) {
      return -1;
    }
    catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static int sendSignal(int pid, @NotNull String signalName) {
    final int signalNumber = getSignalNumber(signalName);
    return (signalNumber == -1) ? -1 : sendSignal(pid, signalNumber);
  }

  public static int sendSignal(int pid, int signal) {
    if (pid <= 0) {
      throw new IllegalArgumentException("Invalid PID: " + pid + " (killing all user processes in one shot is prohibited here)");
    }
    return sendSignalImpl(pid, signal);
  }

  /**
   * Same as {@link #sendSignal(int, int)}, but sends signal to the process group
   */
  @ApiStatus.Internal
  public static int sendSignalToGroup(int pid, int signal) {
    if (pid == 0) {
      throw new IllegalArgumentException("Pid 0 is prohibited");
    }
    return sendSignalImpl(-pid, signal);
  }

  private static int sendSignalImpl(int pid, int signal) {
    checkCLib();
    return Java8Helper.C_LIB.kill(pid, signal);
  }

  private static void checkCLib() {
    if (Java8Helper.C_LIB == null) {
      throw new IllegalStateException("Couldn't load c library, OS: " + OS.CURRENT + ", isUnix: " + (OS.CURRENT != OS.Windows));
    }
  }

  /** @deprecated iterate over {@link Process#descendants()} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean sendSigIntToProcessTree(@NotNull Process process) {
    return sendSignalToProcessTree(process, SIGINT);
  }

  /** @deprecated use {@link com.intellij.execution.process.OSProcessUtil#killProcessTree} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean sendSigKillToProcessTree(@NotNull Process process) {
    return sendSignalToProcessTree(process, SIGKILL);
  }

  /** @deprecated iterate over {@link Process#descendants()} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean sendSignalToProcessTree(@NotNull Process process, int signal) {
    try {
      return sendSignalToProcessTree(getProcessId(process), signal);
    }
    catch (Exception e) {
      LOG.warn("Error killing the process", e);
      return false;
    }
  }

  /** @deprecated iterate over {@link Process#descendants()} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean sendSignalToProcessTree(int processId, int signal) {
    checkCLib();

    int ourPid = Java8Helper.C_LIB.getpid();
    return sendSignalToProcessTree(processId, signal, ourPid);
  }

  /** @deprecated iterate over {@link Process#descendants()} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean sendSignalToProcessTree(int processId, int signal, int ourPid) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Sending signal " + signal + " to process tree with root PID " + processId);
    }

    final Ref<Integer> foundPid = new Ref<>();
    final ProcessInfo processInfo = new ProcessInfo();
    final List<Integer> childrenPids = new ArrayList<>();

    findChildProcesses(ourPid, processId, foundPid, processInfo, childrenPids);

    // result is true if signal was sent to at least one process
    final boolean result;
    if (!foundPid.isNull()) {
      processInfo.killProcTree(foundPid.get(), signal);
      result = true;
    }
    else {
      for (Integer pid : childrenPids) {
        processInfo.killProcTree(pid, signal);
      }
      result = !childrenPids.isEmpty(); //we've tried to kill at least one process
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Done sending signal " + signal + "; found: " + foundPid.get() + ", children: " + childrenPids + ", result: " + result);
    }

    return result;
  }

  private static void findChildProcesses(int our_pid, int process_pid, Ref<Integer> foundPid, ProcessInfo processInfo, List<Integer> childrenPids) {
    Ref<Boolean> ourPidFound = Ref.create(false);
    processPSOutput(getPSCmd(false), s -> {
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
    });
    if (our_pid != -1 && !ourPidFound.get()) {
      throw new IllegalStateException("IDE pid is not found in ps list(" + our_pid + ")");
    }
  }

  public static void processPSOutput(String[] cmd, Processor<? super String> processor) {
    processCommandOutput(cmd, processor, true, true);
  }

  public static void processCommandOutput(String[] cmd, Processor<? super String> processor, boolean skipFirstLine, boolean throwOnError) {
    try {
      Process p = Runtime.getRuntime().exec(cmd);
      processCommandOutput(p, processor, skipFirstLine, throwOnError);
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void processCommandOutput(Process process,
                                           Processor<? super String> processor,
                                           boolean skipFirstLine,
                                           boolean throwOnError) throws IOException {
    try (BufferedReader stdOutput = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      try (BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
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
          throw new IOException("Error reading ps output:" + errorStr);
        }
      }
    }
  }

  public static String[] getPSCmd(boolean commandLineOnly) {
    return getPSCmd(commandLineOnly, false);
  }

  public static String[] getPSCmd(boolean commandLineOnly, boolean isShortenCommand) {
    String psCommand = "/bin/ps";
    if (!Files.isRegularFile(Paths.get(psCommand))) {
      psCommand = "ps";
    }
    if (OS.CURRENT == OS.Linux) {
      return new String[]{psCommand, "-e", "--format", commandLineOnly ? "%a" : "%P%p%a"};
    }
    else if (OS.CURRENT == OS.macOS || OS.CURRENT == OS.FreeBSD) {
      String command = isShortenCommand ? "comm" : "command";
      return new String[]{psCommand, "-ax", "-o", commandLineOnly ? command : "ppid,pid," + command};
    }
    else {
      throw new IllegalStateException(System.getProperty("os.name") + " is not supported.");
    }
  }

  private static final class ProcessInfo {
    private final Map<Integer, List<Integer>> BY_PARENT = new TreeMap<>(); // pid -> list of children pids

    public void register(Integer pid, Integer parentPid) {
      List<Integer> children = BY_PARENT.get(parentPid);
      if (children == null) BY_PARENT.put(parentPid, children = new LinkedList<>());
      children.add(pid);
    }

    public void killProcTree(int pid, int signal) {
      List<Integer> children = BY_PARENT.get(pid);
      if (children != null) {
        for (int child : children) {
          killProcTree(child, signal);
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Sending signal " + signal + " to PID " + pid);
      }
      sendSignal(pid, signal);
    }
  }
}

final class Java8Helper {
  interface CLib extends Library {
    int getpid();

    int kill(int pid, int signal);
  }

  static final CLib C_LIB;

  static {
    CLib lib = null;
    try {
      if (OS.CURRENT != OS.Windows && JnaLoader.isLoaded()) {
        lib = Native.load("c", CLib.class);
      }
    }
    catch (Throwable t) {
      Logger.getInstance(UnixProcessManager.class).warn("Can't load standard library", t);
    }
    C_LIB = lib;
  }
}
