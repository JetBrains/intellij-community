// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

public final class Restarter {
  private static final String DO_NOT_LOCK_INSTALL_FOLDER_PROPERTY = "restarter.do.not.lock.install.folder";
  private static final String SPECIAL_EXIT_CODE_FOR_RESTART_ENV_VAR = "IDEA_RESTART_VIA_EXIT_CODE";

  private Restarter() { }

  public static boolean isSupported() {
    return ourRestartSupported.getValue();
  }

  private static final NullableLazyValue<Path> ourStarter = lazyNullable(() -> {
    if (SystemInfo.isWindows && JnaLoader.isLoaded()) {
      Kernel32 kernel32 = Native.load("kernel32", Kernel32.class);
      char[] buffer = new char[32767];  // using 32,767 as buffer size to avoid limiting ourselves to MAX_PATH (260)
      int result = kernel32.GetModuleFileNameW(null, buffer, new WinDef.DWORD(buffer.length)).intValue();
      if (result != 0) return Path.of(Native.toString(buffer));
    }
    else if (SystemInfo.isMac) {
      File appDir = new File(PathManager.getHomePath()).getParentFile();
      if (appDir != null && appDir.getName().endsWith(".app") && appDir.isDirectory()) return appDir.toPath();
    }
    else if (SystemInfo.isUnix) {
      Path starter = Path.of(PathManager.getBinPath(), ApplicationNamesInfo.getInstance().getScriptName() + ".sh");
      if (Files.exists(starter)) {
        return starter;
      }
    }

    return null;
  });

  private static final NotNullLazyValue<Boolean> ourRestartSupported = NotNullLazyValue.atomicLazy(() -> {
    String problem;

    String restartExitCode = EnvironmentUtil.getValue(SPECIAL_EXIT_CODE_FOR_RESTART_ENV_VAR);
    if (restartExitCode != null) {
      try {
        int code = Integer.parseInt(restartExitCode);
        if (code >= 0 && code <= 255) {
          return true;
        }
        else {
          problem = "Requested exit code out of range (" + code + ")";
        }
      }
      catch (NumberFormatException ex) {
        problem = SPECIAL_EXIT_CODE_FOR_RESTART_ENV_VAR + " contains a value that can't be parsed as an integer (" + restartExitCode + ")";
      }
    }
    else if (SystemInfo.isWindows) {
      if (!JnaLoader.isLoaded()) {
        problem = "JNA not loaded";
      }
      else if (ourStarter.getValue() == null) {
        problem = "GetModuleFileName() failed";
      }
      else {
        problem = checkRestarter("restarter.exe");
      }
    }
    else if (SystemInfo.isMac) {
      if (ourStarter.getValue() == null) {
        problem = "not a bundle: " + PathManager.getHomePath();
      }
      else {
        problem = checkRestarter("restarter");
      }
    }
    else if (SystemInfo.isUnix) {
      if (OSProcessUtil.getCurrentProcessId() <= 0) {
        problem = "cannot detect process ID";
      }
      else if (ourStarter.getValue() == null) {
        problem = "cannot find launcher script in " + PathManager.getBinPath();
      }
      else if (PathEnvironmentVariableUtil.findInPath("python") == null && PathEnvironmentVariableUtil.findInPath("python3") == null) {
        problem = "cannot find neither 'python' nor 'python3' in 'PATH'";
      }
      else {
        problem = checkRestarter("restart.py");
      }
    }
    else {
      problem = "Platform unsupported: " + SystemInfo.OS_NAME;
    }

    if (problem == null) {
      return true;
    }
    else {
      Logger.getInstance(Restarter.class).info("not supported: " + problem);
      return false;
    }
  });

  private static String checkRestarter(String restarterName) {
    Path restarter = PathManager.findBinFile(restarterName);
    return restarter != null && Files.isExecutable(restarter) ? null : "not an executable file: " + restarter;
  }

  public static void scheduleRestart(boolean elevate, String @NotNull ... beforeRestart) throws IOException {
    String exitCodeVariable = EnvironmentUtil.getValue(SPECIAL_EXIT_CODE_FOR_RESTART_ENV_VAR);
    if (exitCodeVariable != null) {
      if (beforeRestart.length > 0) {
        throw new IOException("Cannot restart application: specific exit code restart mode does not support executing additional commands");
      }
      try {
        int code = Integer.parseInt(exitCodeVariable);
        System.exit(code);
      }
      catch (NumberFormatException ex) {
        throw new IOException("Cannot restart application: can't parse required exit code", ex);
      }
    }
    else if (SystemInfo.isWindows) {
      restartOnWindows(elevate, beforeRestart);
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

  public static @Nullable Path getIdeStarter() {
    return ourStarter.getValue();
  }

  private static void restartOnWindows(boolean elevate, String... beforeRestart) throws IOException {
    Kernel32 kernel32 = Native.load("kernel32", Kernel32.class);
    Shell32 shell32 = Native.load("shell32", Shell32.class);

    int pid = OSProcessUtil.getCurrentProcessId();
    IntByReference argc = new IntByReference();
    Pointer argvPtr = shell32.CommandLineToArgvW(kernel32.GetCommandLineW(), argc);
    String[] argv = getRestartArgv(argvPtr.getWideStringArray(0, argc.getValue()));
    kernel32.LocalFree(argvPtr);

    // See https://blogs.msdn.microsoft.com/oldnewthing/20060515-07/?p=31203
    // argv[0] as the program name is only a convention, i.e. there is no guarantee the name is the full path to the executable
    Path starter = ourStarter.getValue();
    if (starter == null) {
      throw new IOException("GetModuleFileName() failed");
    }
    argv[0] = starter.toString();

    List<String> args = new ArrayList<>();
    args.add(String.valueOf(pid));

    if (beforeRestart.length > 0) {
      args.add(String.valueOf(beforeRestart.length));
      Collections.addAll(args, beforeRestart);
    }

    Path launcher;
    if (elevate && (launcher = PathManager.findBinFile("launcher.exe")) != null) {
      args.add(String.valueOf(argv.length + 1));
      args.add(launcher.toString());
    }
    else {
      args.add(String.valueOf(argv.length));
    }
    Collections.addAll(args, argv);

    Path restarter = PathManager.findBinFile("restarter.exe");
    if (restarter == null) {
      throw new IOException("Can't find restarter.exe; please reinstall the IDE");
    }
    runRestarter(restarter.toFile(), args);

    // Since the process ID is passed through the command line, we want to make sure we don't exit before the "restarter"
    // process has a chance to open the handle to our process, and that it doesn't wait for the termination of an unrelated
    // process that happened to have the same process ID.
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

    return Arrays.copyOf(argv, countArgs);
  }

  private static void restartOnMac(String... beforeRestart) throws IOException {
    Path appDir = ourStarter.getValue();
    if (appDir == null) {
      throw new IOException("Application bundle not found: " + PathManager.getHomePath());
    }
    List<String> args = new ArrayList<>();
    args.add(appDir.toString());
    Collections.addAll(args, beforeRestart);
    runRestarter(new File(PathManager.getBinPath(), "restarter"), args);
  }

  private static void restartOnUnix(String... beforeRestart) throws IOException {
    Path starterScript = ourStarter.getValue();
    if (starterScript == null) {
      throw new IOException("Starter script not found in " + PathManager.getBinPath());
    }

    int pid = OSProcessUtil.getCurrentProcessId();
    if (pid <= 0) throw new IOException("Invalid process ID: " + pid);

    File python = PathEnvironmentVariableUtil.findInPath("python");
    if (python == null) python = PathEnvironmentVariableUtil.findInPath("python3");
    if (python == null) throw new IOException("Cannot find neither 'python' nor 'python3' in 'PATH'");
    File script = new File(PathManager.getBinPath(), "restart.py");

    List<String> args = new ArrayList<>();
    if ("python".equals(python.getName())) {
      args.add(String.valueOf(pid));
      args.add(starterScript.toString());
      Collections.addAll(args, beforeRestart);
      runRestarter(script, args);
    }
    else {
      args.add(script.getPath());
      args.add(String.valueOf(pid));
      args.add(starterScript.toString());
      Collections.addAll(args, beforeRestart);
      runRestarter(python, args);
    }
  }

  public static void doNotLockInstallFolderOnRestart() {
    System.setProperty(DO_NOT_LOCK_INSTALL_FOLDER_PROPERTY, "true");
  }

  private static void runRestarter(File restarterFile, List<String> restarterArgs) throws IOException {
    String restarter = restarterFile.getPath();
    boolean doNotLock = SystemProperties.getBooleanProperty(DO_NOT_LOCK_INSTALL_FOLDER_PROPERTY, false);
    Path tempDir = null;
    if (doNotLock || restarterArgs.contains(UpdateInstaller.UPDATER_MAIN_CLASS)) {
      tempDir = Paths.get(PathManager.getSystemPath(), "restart");
      Files.createDirectories(tempDir);
      Path copy = tempDir.resolve(restarterFile.getName());
      Files.copy(restarterFile.toPath(), copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
      restarter = copy.toString();
    }
    restarterArgs.add(0, restarter);
    Logger.getInstance(Restarter.class).info("run restarter: " + restarterArgs);

    ProcessBuilder processBuilder = new ProcessBuilder(restarterArgs);
    if (doNotLock) processBuilder.directory(tempDir.toFile());
    if (SystemInfo.isXWindow) setDesktopStartupId(processBuilder);
    processBuilder.start();
  }

  // this is required to support X server's focus stealing prevention mechanism, see JBR-2503
  private static void setDesktopStartupId(ProcessBuilder processBuilder) {
    if (!SystemInfo.isJetBrainsJvm || !"sun.awt.X11.XToolkit".equals(Toolkit.getDefaultToolkit().getClass().getName())) return;
    try {
      Long lastUserActionTime = ReflectionUtil.getStaticFieldValue(Class.forName("sun.awt.X11.XBaseWindow"), long.class, "globalUserTime");
      if (lastUserActionTime == null) {
        Logger.getInstance(Restarter.class).warn("Couldn't obtain last user action's timestamp");
      }
      else {
        // this doesn't start 'proper' startup sequence (by sending 'new:' message to the root window),
        // but passing the event timestamp to the started process should be enough to prevent focus stealing
        processBuilder.environment().put("DESKTOP_STARTUP_ID",
                                         ApplicationNamesInfo.getInstance().getProductName() + "-restart_TIME" + lastUserActionTime);
      }
    }
    catch (Exception e) {
      Logger.getInstance(Restarter.class).warn("Couldn't set DESKTOP_STARTUP_ID", e);
    }
  }

  @SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
  private interface Kernel32 extends StdCallLibrary {
    WString GetCommandLineW();
    Pointer LocalFree(Pointer pointer);
    WinDef.DWORD GetModuleFileNameW(WinDef.HMODULE hModule, char[] lpFilename, WinDef.DWORD nSize);
  }

  private interface Shell32 extends StdCallLibrary {
    Pointer CommandLineToArgvW(WString command_line, IntByReference argc);
  }
}
