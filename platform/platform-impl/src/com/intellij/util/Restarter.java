/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Restarter {
  private Restarter() { }

  private static int getRestartCode() {
    return SystemProperties.getIntProperty("jb.restart.code", 0);
  }

  public static boolean isSupported() {
    if (getRestartCode() != 0) {
      return true;
    }
    if (SystemInfo.isWindows) {
      return JnaLoader.isLoaded() && new File(PathManager.getBinPath(), "restarter.exe").exists();
    }
    if (SystemInfo.isMac) {
      return PathManager.getHomePath().contains(".app");
    }
    return false;
  }

  public static int scheduleRestart(@NotNull String... beforeRestart) throws IOException {
    try {
      int restartCode = getRestartCode();
      if (restartCode != 0) {
        runCommand(beforeRestart);
        return restartCode;
      }
      else if (SystemInfo.isWindows) {
        restartOnWindows(beforeRestart);
        return 0;
      }
      else if (SystemInfo.isMac) {
        restartOnMac(beforeRestart);
        return 0;
      }
    }
    catch (Throwable t) {
      throw new IOException("Cannot restart application: " + t.getMessage(), t);
    }

    runCommand(beforeRestart);
    throw new IOException("Cannot restart application: not supported.");
  }

  private static void runCommand(String... beforeRestart) throws IOException {
    if (beforeRestart.length == 0) return;

    File restartDir = new File(getRestarterDir());
    String systemPath = new File(System.getProperty("user.home") + "/." + System.getProperty("idea.paths.selector") + "/system/restart").getPath();
    if (! systemPath.equals(restartDir.getPath())){
      throw new IOException("idea.system.path was changed. Restart is not supported.");
    }
    if (!FileUtilRt.createDirectory(restartDir)) {
      throw new IOException("Cannot create dir: " + restartDir);
    }

    File restarter = new File(restartDir, "restarter.sh");
    try (BufferedWriter output = new BufferedWriter(new FileWriter(restarter))) {
      output.write("#!/bin/sh\n");
      for (int i = 0; i < beforeRestart.length; i++) {
        output.write(beforeRestart[i]);
        if (i <= beforeRestart.length - 2) output.write(' ');
        if (i >= beforeRestart.length - 2) output.write('"');
      }
      output.write('\n');
    }

    if (!restarter.setExecutable(true, true)) {
      throw new IOException("Cannot make file executable: " + restarter);
    }
  }

  private static void restartOnWindows(@NotNull String... beforeRestart) throws IOException {
    Kernel32 kernel32 = (Kernel32)Native.loadLibrary("kernel32", Kernel32.class);
    Shell32 shell32 = (Shell32)Native.loadLibrary("shell32", Shell32.class);

    int pid = kernel32.GetCurrentProcessId();
    IntByReference argc = new IntByReference();
    Pointer argv_ptr = shell32.CommandLineToArgvW(kernel32.GetCommandLineW(), argc);
    String[] argv = getRestartArgv(argv_ptr.getWideStringArray(0, argc.getValue()));
    kernel32.LocalFree(argv_ptr);

    // See https://blogs.msdn.microsoft.com/oldnewthing/20060515-07/?p=31203
    // argv[0] as the program name is only a convention, i.e. there is no guarantee
    // the name is the full path to the executable.
    //
    // See https://msdn.microsoft.com/en-us/library/windows/desktop/ms683197(v=vs.85).aspx
    // To retrieve the full path to the executable, use "GetModuleFileName(NULL, ...)".
    //
    // Note: We use 32,767 as buffer size to avoid limiting ourselves to MAX_PATH (260).
    char[] buffer = new char[32767];
    if (kernel32.GetModuleFileNameW(null, buffer, new WinDef.DWORD(buffer.length)).intValue() > 0) {
      argv[0] = Native.toString(buffer);
    }

    doScheduleRestart(new File(PathManager.getBinPath(), "restarter.exe"), commands -> {
      Collections.addAll(commands, String.valueOf(pid), String.valueOf(beforeRestart.length));
      Collections.addAll(commands, beforeRestart);
      Collections.addAll(commands, String.valueOf(argv.length));
      Collections.addAll(commands, argv);
    });

    // Since the process ID is passed through the command line, we want to make sure that we don't exit before the "restarter"
    // process has a chance to open the handle to our process, and that it doesn't wait for the termination of an unrelated
    // process which happened to have the same process ID.
    TimeoutUtil.sleep(500);
  }

  private static void restartOnMac(@NotNull String... beforeRestart) throws IOException {
    String homePath = PathManager.getHomePath();
    int p = homePath.indexOf(".app");
    if (p < 0) throw new IOException("Application bundle not found: " + homePath);

    String bundlePath = homePath.substring(0, p + 4);
    doScheduleRestart(new File(PathManager.getBinPath(), "restarter"), commands -> {
      Collections.addAll(commands, bundlePath);
      Collections.addAll(commands, beforeRestart);
    });
  }

  private static String[] getRestartArgv(String[] argv) {
    int countArgs = argv.length;
    for (int i = argv.length-1; i >=0; i--) {
      if (argv[i].endsWith("com.intellij.idea.Main") ||
          argv[i].endsWith(".exe")) {
        countArgs = i + 1;
        if (argv[i].endsWith(".exe") && argv[i].indexOf(File.separatorChar) < 0) {
          //absolute path
          argv[i] = new File(PathManager.getBinPath(), argv[i]).getPath();
        }
        break;
      }
    }
    String[] restartArg = new String[countArgs];
    System.arraycopy(argv, 0, restartArg, 0, countArgs);
    return restartArg;
  }

  private static void doScheduleRestart(File restarterFile, Consumer<List<String>> argumentsBuilder) throws IOException {
    List<String> commands = new ArrayList<>();
    commands.add(createTempExecutable(restarterFile).getPath());
    argumentsBuilder.consume(commands);
    Runtime.getRuntime().exec(ArrayUtil.toStringArray(commands));
  }

  public static String getRestarterDir() {
    return PathManager.getSystemPath() + "/restart";
  }

  public static File createTempExecutable(File executable) throws IOException {
    File executableDir = new File(getRestarterDir());
    if (!FileUtilRt.createDirectory(executableDir)) throw new IOException("Cannot create dir: " + executableDir);
    File copy = new File(executableDir.getPath() + "/" + executable.getName());
    if (!FileUtilRt.ensureCanCreateFile(copy) || (copy.exists() && !copy.delete())) {
       String ext = FileUtilRt.getExtension(executable.getName());
       copy = FileUtilRt.createTempFile(executableDir, FileUtilRt.getNameWithoutExtension(copy.getName()),
                                        StringUtil.isEmptyOrSpaces(ext) ? ".tmp" : ("." + ext),
                                        true, false);
    }
    FileUtilRt.copy(executable, copy);
    if (!copy.setExecutable(executable.canExecute())) throw new IOException("Cannot make file executable: " + copy);
    return copy;
  }

  private interface Kernel32 extends StdCallLibrary {
    int GetCurrentProcessId();

    WString GetCommandLineW();

    Pointer LocalFree(Pointer pointer);

    WinDef.DWORD GetModuleFileNameW(WinDef.HMODULE hModule, char[] lpFilename, WinDef.DWORD nSize);
  }

  private interface Shell32 extends StdCallLibrary {
    Pointer CommandLineToArgvW(WString command_line, IntByReference argc);
  }
}