// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.MachineType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * Do not call this class directly - use {@link OSProcessUtil} instead.
 *
 * @author Alexey.Ushakov
 */
public class WinProcessManager {
  private static final Logger LOG = Logger.getInstance(WinProcessManager.class);

  private WinProcessManager() { }

  public static int getProcessId(Process process) {
    String processClassName = process.getClass().getName();
    if (processClassName.equals("java.lang.Win32Process") || processClassName.equals("java.lang.ProcessImpl")) {
      try {
        if (SystemInfo.IS_AT_LEAST_JAVA9) {
          //noinspection JavaReflectionMemberAccess
          return ((Long)Process.class.getMethod("pid").invoke(process)).intValue();
        }

        long handle = assertNotNull(ReflectionUtil.getField(process.getClass(), process, long.class, "handle"));
        return Kernel32.INSTANCE.GetProcessId(new WinNT.HANDLE(Pointer.createConstant(handle)));
      }
      catch (Throwable t) {
        throw new IllegalStateException("Failed to get PID from instance of " + process.getClass() + ", OS: " + SystemInfo.OS_NAME, t);
      }
    }

    throw new IllegalStateException("Unable to get PID from instance of " + process.getClass() + ", OS: " + SystemInfo.OS_NAME);
  }

  public static int getCurrentProcessId() {
    return Kernel32.INSTANCE.GetCurrentProcessId();
  }

  public static boolean kill(Process process, boolean tree) {
    return kill(-1, process, tree);
  }

  public static boolean kill(int pid, boolean tree) {
    return kill(pid, null, tree);
  }

  private static boolean kill(int pid, Process process, boolean tree) {
    LOG.assertTrue(pid > 0 || process != null);
    try {
      if (process != null) {
        pid = getProcessId(process);
      }
      String[] cmdArray = {"taskkill", "/f", "/pid", String.valueOf(pid), tree ? "/t" : ""};
      if (LOG.isDebugEnabled()) {
        LOG.debug(StringUtil.join(cmdArray, " "));
      }
      Process p = new ProcessBuilder(cmdArray).redirectErrorStream(true).start();
      String output = FileUtil.loadTextAndClose(p.getInputStream());
      int res = p.waitFor();

      if (res != 0 && (process == null || isAlive(process))) {
        LOG.warn(StringUtil.join(cmdArray, " ") + " failed: " + output);
        return false;
      }
      else if (LOG.isDebugEnabled()) {
        LOG.debug(output);
      }

      return true;
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return false;
  }

  private static boolean isAlive(Process process) {
    try {
      process.exitValue();
      return false;
    } catch(IllegalThreadStateException e) {
      return true;
    }
  }

  @NotNull
  public static MachineType getProcessMachineType(int pid) {
    final WinNT.HANDLE hProcess = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
    if (hProcess == WinBase.INVALID_HANDLE_VALUE) return MachineType.UNKNOWN;

    final IntByReference isWow64Ref = new IntByReference(0);
    try {
      if (!Kernel32.INSTANCE.IsWow64Process(hProcess, isWow64Ref)) return MachineType.UNKNOWN;
    }
    catch (UnsatisfiedLinkError ignored) {
    }
    finally {
      Kernel32.INSTANCE.CloseHandle(hProcess);
    }
    boolean isWow64 = (isWow64Ref.getValue() == 1);
    return isWow64 ? MachineType.I386 : getOsArch();
  }

  @NotNull
  private static MachineType getOsArch() {
    final WinBase.SYSTEM_INFO systemInfo = new WinBase.SYSTEM_INFO();
    Kernel32.INSTANCE.GetNativeSystemInfo(systemInfo);

    final WinDef.WORD processorArchitecture = systemInfo.processorArchitecture.dwOemID.getLow();
    if (processorArchitecture.intValue() == 0 /* PROCESSOR_ARCHITECTURE_INTEL */) return MachineType.I386;
    if (processorArchitecture.intValue() == 9 /* PROCESSOR_ARCHITECTURE_AMD64 */) return MachineType.AMD64;
    return MachineType.UNKNOWN;
  }
}