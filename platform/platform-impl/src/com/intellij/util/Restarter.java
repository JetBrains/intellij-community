// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

public final class Restarter {
  private static final String DO_NOT_LOCK_INSTALL_FOLDER_PROPERTY = "restarter.do.not.lock.install.folder";
  private static final String SPECIAL_EXIT_CODE_FOR_RESTART_ENV_VAR = "IDEA_RESTART_VIA_EXIT_CODE";

  private Restarter() { }

  public static boolean isSupported() {
    return ourRestartSupported.get();
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
      var appDir = Path.of(PathManager.getHomePath()).getParent();
      if (appDir != null && appDir.getFileName().toString().endsWith(".app") && Files.isDirectory(appDir)) {
        return appDir;
      }
    }
    else if (SystemInfo.isUnix) {
      var starter = Path.of(PathManager.getBinPath(), ApplicationNamesInfo.getInstance().getScriptName() + ".sh");
      if (Files.exists(starter)) {
        return starter;
      }
    }

    return null;
  });

  private static final Supplier<Boolean> ourRestartSupported = new SynchronizedClearableLazy<>(() -> {
    String problem;

    var restartExitCode = EnvironmentUtil.getValue(SPECIAL_EXIT_CODE_FOR_RESTART_ENV_VAR);
    if (restartExitCode != null) {
      try {
        var code = Integer.parseInt(restartExitCode);
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
      if (ourStarter.getValue() == null) {
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
    var restarter = PathManager.findBinFile(restarterName);
    return restarter != null && Files.isExecutable(restarter) ? null : "not an executable file: " + restarter;
  }

  public static void scheduleRestart(boolean elevate, String @NotNull ... beforeRestart) throws IOException {
    var exitCodeVariable = EnvironmentUtil.getValue(SPECIAL_EXIT_CODE_FOR_RESTART_ENV_VAR);
    if (exitCodeVariable != null) {
      if (beforeRestart.length > 0) {
        throw new IOException("Cannot restart application: specific exit code restart mode does not support executing additional commands");
      }
      try {
        System.exit(Integer.parseInt(exitCodeVariable));
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
    var restarter = PathManager.findBinFileWithException("restarter.exe");

    var command = new ArrayList<String>();
    command.add(copyWhenNeeded(restarter, beforeRestart).toString());
    command.add(String.valueOf(ProcessHandle.current().pid()));
    if (beforeRestart.length > 0) {
      command.add(String.valueOf(beforeRestart.length));
      Collections.addAll(command, beforeRestart);
    }
    if (elevate) {
      command.add("2");
      command.add(PathManager.findBinFileWithException("launcher.exe").toString());
    }
    else {
      command.add("1");
    }
    command.add(starter.toString());
    runRestarter(command);
  }

  private static void restartOnMac(String... beforeRestart) throws IOException {
    var appDir = ourStarter.getValue();
    if (appDir == null) throw new IOException("Application bundle not found: " + PathManager.getHomePath());
    var restarter = Path.of(PathManager.getBinPath(), "restarter");

    var command = new ArrayList<String>();
    command.add(copyWhenNeeded(restarter, beforeRestart).toString());
    command.add(appDir.toString());
    Collections.addAll(command, beforeRestart);
    runRestarter(command);
  }

  private static void restartOnUnix(String... beforeRestart) throws IOException {
    var starterScript = ourStarter.getValue();
    if (starterScript == null) throw new IOException("Starter script not found in " + PathManager.getBinPath());
    var python = PathEnvironmentVariableUtil.findInPath("python3");
    if (python == null) python = PathEnvironmentVariableUtil.findInPath("python");
    if (python == null) throw new IOException("Cannot find neither 'python' nor 'python3' in 'PATH'");
    var restarter = Path.of(PathManager.getBinPath(), "restart.py");

    var command = new ArrayList<String>();
    command.add(python.getPath());
    command.add(copyWhenNeeded(restarter, beforeRestart).toString());
    command.add(String.valueOf(ProcessHandle.current().pid()));
    command.add(starterScript.toString());
    Collections.addAll(command, beforeRestart);
    runRestarter(command);
  }

  @ApiStatus.Internal
  public static void doNotLockInstallFolderOnRestart() {
    System.setProperty(DO_NOT_LOCK_INSTALL_FOLDER_PROPERTY, "true");
  }

  private static Path copyWhenNeeded(Path binFile, String[] args) throws IOException {
    if (SystemProperties.getBooleanProperty(DO_NOT_LOCK_INSTALL_FOLDER_PROPERTY, false) || ArrayUtil.contains(UpdateInstaller.UPDATER_MAIN_CLASS, args)) {
      var tempDir = Files.createDirectories(PathManager.getSystemDir().resolve("restart"));
      return Files.copy(binFile, tempDir.resolve(binFile.getFileName()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }
    else {
      return binFile;
    }
  }

  private static void runRestarter(List<String> command) throws IOException {
    Logger.getInstance(Restarter.class).info("run restarter: " + command);

    var processBuilder = new ProcessBuilder(command)
      .directory(Path.of(SystemProperties.getUserHome()).toFile());

    if (SystemInfo.isXWindow) setDesktopStartupId(processBuilder);

    processBuilder.start();
  }

  // this is required to support X server's focus stealing prevention mechanism, see JBR-2503
  private static void setDesktopStartupId(ProcessBuilder processBuilder) {
    if (SystemInfo.isJetBrainsJvm && "sun.awt.X11.XToolkit".equals(Toolkit.getDefaultToolkit().getClass().getName())) {
      try {
        var lastUserActionTime = ReflectionUtil.getStaticFieldValue(Class.forName("sun.awt.X11.XBaseWindow"), long.class, "globalUserTime");
        if (lastUserActionTime == null) {
          Logger.getInstance(Restarter.class).warn("Couldn't obtain last user action's timestamp");
        }
        else {
          // this doesn't initiate a "proper" startup sequence (by sending the 'new:' message to the root window),
          // but passing the event timestamp to the started process should be enough to prevent focus stealing
          var restartId = ApplicationNamesInfo.getInstance().getProductName() + "-restart_TIME" + lastUserActionTime;
          processBuilder.environment().put("DESKTOP_STARTUP_ID", restartId);
        }
      }
      catch (Exception e) {
        Logger.getInstance(Restarter.class).warn("Couldn't set DESKTOP_STARTUP_ID", e);
      }
    }
  }
}
