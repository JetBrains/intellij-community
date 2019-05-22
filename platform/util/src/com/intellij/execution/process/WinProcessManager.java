// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

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
  public static ProcessMachineType getProcessMachineType(int pid) {
    final WinNT.HANDLE hProcess = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
    if (hProcess == WinBase.INVALID_HANDLE_VALUE) return ProcessMachineType.UNKNOWN;

    final IntByReference isWow64Ref = new IntByReference(0);
    try {
      if (!Kernel32.INSTANCE.IsWow64Process(hProcess, isWow64Ref)) return ProcessMachineType.UNKNOWN;
    }
    catch (UnsatisfiedLinkError ignored) {
    }
    finally {
      Kernel32.INSTANCE.CloseHandle(hProcess);
    }
    boolean isWow64 = (isWow64Ref.getValue() == 1);
    return isWow64 ? ProcessMachineType.I386
                   : getOsArch();
  }

  @NotNull
  private static ProcessMachineType getOsArch() {
    final WinBase.SYSTEM_INFO systemInfo = new WinBase.SYSTEM_INFO();
    Kernel32.INSTANCE.GetNativeSystemInfo(systemInfo);

    final WinDef.WORD processorArchitecture = systemInfo.processorArchitecture.dwOemID.getLow();
    if (processorArchitecture.intValue() == 0 /* PROCESSOR_ARCHITECTURE_INTEL */) return ProcessMachineType.I386;
    if (processorArchitecture.intValue() == 9 /* PROCESSOR_ARCHITECTURE_AMD64 */) return ProcessMachineType.AMD64;
    return ProcessMachineType.UNKNOWN;
  }

  @NotNull
  public static ProcessMachineType readPeMachineType(@NotNull String path) throws IOException {
    final File file = new File(path);
    return readPeMachineType(file);
  }

  @NotNull
  public static ProcessMachineType readPeMachineType(@NotNull File file) throws IOException {
    if (!file.isFile() || !file.canRead()) {
      throw new IOException("Not a readable file");
    }
    final long len = file.length();
    if (len < 0) throw new IOException("File length reported negative");

    try (FileInputStream stream = new FileInputStream(file)) {
      final FileChannel channel = stream.getChannel();

      final int DOS_HEADER_LEN = 64;
      final ByteBuffer dosHeader = ByteBuffer.allocate(DOS_HEADER_LEN);
      if (channel.read(dosHeader) < DOS_HEADER_LEN) throw new IOException("Not a valid PE executable: DOS header is too short");
      dosHeader.flip();
      if (dosHeader.getShort() != 0x4D5A /* MZ */) throw new IOException("Not a valid PE executable: missing DOS magic");
      dosHeader.position(DOS_HEADER_LEN - 4);
      final int peOffset = dosHeader.order(ByteOrder.LITTLE_ENDIAN).getInt();

      channel.position(peOffset);

      final int PE_HEADER_LEN = 6;  // we only need 6 bytes
      final ByteBuffer peHeader = ByteBuffer.allocate(PE_HEADER_LEN);
      if (channel.read(peHeader) < PE_HEADER_LEN) throw new IOException("Not a valid PE executable: PE header is too short");
      peHeader.flip();
      if (peHeader.getInt() != 0x50450000 /* PE\0\0 */) throw new IOException("Not a valid PE executable: missing PE magic");
      final short peMachineType = peHeader.order(ByteOrder.LITTLE_ENDIAN).getShort();

      return ProcessMachineType.forPeMachineTypeCode(peMachineType);
    }
  }
}