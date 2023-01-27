// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.ApiStatus;
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
    if (SystemInfo.isWindows) {
      var name = ApplicationNamesInfo.getInstance().getScriptName() + (Boolean.getBoolean("ide.native.launcher") ? "64.exe" : ".bat");
      var starter = Path.of(PathManager.getBinPath(), name);
      if (Files.exists(starter)) {
        return starter;
      }
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
      if (ourStarter.getValue() == null) {
        problem = "cannot find starter executable in " + PathManager.getBinPath();
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
      else if (PathEnvironmentVariableUtil.findInPath("python3") == null && PathEnvironmentVariableUtil.findInPath("python") == null) {
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
    var starter = ourStarter.getValue();
    if (starter == null) throw new IOException("Starter executable not found in " + PathManager.getBinPath());

    var args = new ArrayList<String>();
    args.add(String.valueOf(ProcessHandle.current().pid()));

    if (beforeRestart.length > 0) {
      args.add(String.valueOf(beforeRestart.length));
      Collections.addAll(args, beforeRestart);
    }

    if (elevate) {
      var launcher = PathManager.findBinFile("launcher.exe");
      if (launcher == null) throw new IOException("Cannot find 'launcher.exe'; please reinstall the IDE");
      args.add("2");
      args.add(launcher.toString());
    }
    else {
      args.add("1");
    }
    args.add(starter.toString());

    var restarter = PathManager.findBinFile("restarter.exe");
    if (restarter == null) throw new IOException("Cannot find 'restarter.exe'; please reinstall the IDE");

    runRestarter(restarter.toFile(), args);
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

    File python = PathEnvironmentVariableUtil.findInPath("python3");
    if (python == null) python = PathEnvironmentVariableUtil.findInPath("python");
    if (python == null) throw new IOException("Cannot find neither 'python' nor 'python3' in 'PATH'");
    List<String> args = new ArrayList<>();
    args.add(PathManager.findBinFileWithException("restart.py").toString());
    args.add(String.valueOf(pid));
    args.add(starterScript.toString());
    Collections.addAll(args, beforeRestart);
    runRestarter(python, args);
  }

  @ApiStatus.Internal
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
}
