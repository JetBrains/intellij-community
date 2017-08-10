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
package com.intellij.util;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.ide.actions.CreateDesktopEntryAction;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Restarter {
  private Restarter() { }

  public static boolean isSupported() {
    return ourRestartSupported.getValue();
  }

  private static final NotNullLazyValue<Boolean> ourRestartSupported = new AtomicNotNullLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      String problem;

      if (SystemInfo.isWindows) {
        if (!JnaLoader.isLoaded()) {
          problem = "JNA not loaded";
        }
        else {
          problem = checkRestarter("restarter.exe");
        }
      }
      else if (SystemInfo.isMac) {
        if (getMacOsAppDir() == null) {
          problem = "not a bundle: " + PathManager.getHomePath();
        }
        else {
          problem = checkRestarter("restarter");
        }
      }
      else if (SystemInfo.isUnix) {
        if (UnixProcessManager.getCurrentProcessId() <= 0) {
          problem = "cannot detect process ID";
        }
        else if (CreateDesktopEntryAction.getLauncherScript() == null) {
          problem = "cannot find launcher script in " + PathManager.getBinPath();
        }
        else if (PathEnvironmentVariableUtil.findInPath("python") == null) {
          problem = "cannot find 'python' in PATH";
        }
        else {
          problem = checkRestarter("restart.py");
        }
      }
      else {
        problem = "unknown platform: " + SystemInfo.OS_NAME;
      }

      if (problem == null) {
        return true;
      }
      else {
        Logger.getInstance(Restarter.class).info("not supported: " + problem);
        return false;
      }
    }
  };

  private static String checkRestarter(String restarterName) {
    File restarter = new File(PathManager.getBinPath(), restarterName);
    return restarter.isFile() && restarter.canExecute() ? null : "not an executable file: " + restarter;
  }

  public static void scheduleRestart(@NotNull String... beforeRestart) throws IOException {
    Logger.getInstance(Restarter.class).info("restart: " + Arrays.toString(beforeRestart));
    if (SystemInfo.isWindows) {
      restartOnWindows(beforeRestart);
    }
    else if (SystemInfo.isMac) {
      restartOnMac(beforeRestart);
    }
    else if (SystemInfo.isUnix) {
      restartOnUnix(beforeRestart);
    }
    else {
      throw new IOException("Cannot restart application: not supported.");
    }
  }

  private static void restartOnWindows(String... beforeRestart) throws IOException {
    Kernel32 kernel32 = (Kernel32)Native.loadLibrary("kernel32", Kernel32.class);
    Shell32 shell32 = (Shell32)Native.loadLibrary("shell32", Shell32.class);

    int pid = kernel32.GetCurrentProcessId();
    IntByReference argc = new IntByReference();
    Pointer argvPtr = shell32.CommandLineToArgvW(kernel32.GetCommandLineW(), argc);
    String[] argv = getRestartArgv(argvPtr.getWideStringArray(0, argc.getValue()));
    kernel32.LocalFree(argvPtr);

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

    List<String> args = new ArrayList<>();
    args.add(String.valueOf(pid));
    args.add(String.valueOf(beforeRestart.length));
    Collections.addAll(args, beforeRestart);
    args.add(String.valueOf(argv.length));
    Collections.addAll(args, argv);
    runRestarter(new File(PathManager.getBinPath(), "restarter.exe"), args);

    // Since the process ID is passed through the command line, we want to make sure that we don't exit before the "restarter"
    // process has a chance to open the handle to our process, and that it doesn't wait for the termination of an unrelated
    // process which happened to have the same process ID.
    TimeoutUtil.sleep(500);
  }

  private static String[] getRestartArgv(String[] argv) {
    String mainClass = System.getProperty("idea.main.class.name", "com.intellij.idea.Main");

    int countArgs = argv.length;
    for (int i = argv.length-1; i >=0; i--) {
      if (argv[i].equals(mainClass) || argv[i].endsWith(".exe")) {
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

  private static void restartOnMac(String... beforeRestart) throws IOException {
    File appDir = getMacOsAppDir();
    if (appDir == null) throw new IOException("Application bundle not found: " + PathManager.getHomePath());
    List<String> args = new ArrayList<>();
    args.add(appDir.getPath());
    Collections.addAll(args, beforeRestart);
    runRestarter(new File(PathManager.getBinPath(), "restarter"), args);
  }

  private static File getMacOsAppDir() {
    File appDir = new File(PathManager.getHomePath()).getParentFile();
    return appDir != null && appDir.getName().endsWith(".app") && appDir.isDirectory() ? appDir : null;
  }

  private static void restartOnUnix(String... beforeRestart) throws IOException {
    String launcherScript = CreateDesktopEntryAction.getLauncherScript();
    if (launcherScript == null) throw new IOException("Launcher script not found in " + PathManager.getBinPath());

    int pid = UnixProcessManager.getCurrentProcessId();
    if (pid <= 0) throw new IOException("Invalid process ID: " + pid);

    List<String> args = new ArrayList<>();
    args.add(String.valueOf(pid));
    args.add(launcherScript);
    Collections.addAll(args, beforeRestart);
    runRestarter(new File(PathManager.getBinPath(), "restart.py"), args);
  }

  private static void runRestarter(File restarterFile, List<String> restarterArgs) throws IOException {
    restarterArgs.add(0, createTempExecutable(restarterFile).getPath());
    Runtime.getRuntime().exec(ArrayUtil.toStringArray(restarterArgs));
  }

  @NotNull
  public static File createTempExecutable(@NotNull File executable, @NotNull final File ...filesToCopyToSameFolder) throws IOException {
    File tempDir = new File(PathManager.getSystemPath(), "restart");
    if (!FileUtilRt.createDirectory(tempDir)) {
      throw new IOException("Cannot create directory: " + tempDir);
    }

    File copy = new File(tempDir, executable.getName());
    if (!FileUtilRt.ensureCanCreateFile(copy) || (copy.exists() && !copy.delete())) {
      String prefix = FileUtilRt.getNameWithoutExtension(copy.getName());
      String ext = FileUtilRt.getExtension(executable.getName());
      String suffix = StringUtil.isEmptyOrSpaces(ext) ? ".tmp" : ("." + ext);
      copy = FileUtilRt.createTempFile(tempDir, prefix, suffix, true, false);
    }
    FileUtilRt.copy(executable, copy);
    final File directory = copy.getParentFile();
    for (final File fileToCopy : filesToCopyToSameFolder) {
      FileUtilRt.copy(fileToCopy, new File(directory, fileToCopy.getName()));
    }


    if (executable.canExecute() && !copy.setExecutable(true)) {
      throw new IOException("Cannot make file executable: " + copy);
    }

    return copy;
  }

  @SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
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