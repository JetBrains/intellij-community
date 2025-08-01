// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.platform.ide.productInfo.IdeProductInfo;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.system.OS;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

public final class Restarter {
  private static final String SPECIAL_EXIT_CODE_FOR_RESTART_ENV_VAR = "IDEA_RESTART_VIA_EXIT_CODE";

  private static volatile boolean copyRestarterFiles = false;
  private static volatile List<String> mainAppArgs = List.of();

  private Restarter() { }

  public static boolean isSupported() {
    return ourRestartSupported.get();
  }

  private static final NullableLazyValue<Path> ourLauncherWithoutRemoteDevOverride = lazyNullable(() -> {
    var baseName = ApplicationNamesInfo.getInstance().getScriptName();
    var launcher = switch (OS.CURRENT) {
      case Windows -> PathManager.getBinDir().resolve(baseName + (Boolean.getBoolean("ide.native.launcher") ? "64.exe" : ".bat"));
      case macOS -> PathManager.getHomeDir().resolve("MacOS").resolve(baseName);
      case Linux -> PathManager.getBinDir().resolve(baseName + (Boolean.getBoolean("ide.native.launcher") ? "" : ".sh"));
      default -> null;
    };
    return launcher != null && Files.exists(launcher) ? launcher : null;
  });

  private static final NullableLazyValue<Path> ourLauncher = lazyNullable(() -> {
    if (Boolean.getBoolean("ide.started.from.remote.dev.launcher")) {
      var launcher = PathManager.getBinDir().resolve("remote-dev-server" + (OS.CURRENT == OS.Windows ? ".exe" : ""));
      if (Files.exists(launcher)) return launcher;
      Logger.getInstance(Restarter.class).error(
        "RemDev starter property is set, but launcher file at " + launcher + " was not found? Will restart using default entry point"
      );
    }

    var launcher = ourLauncherWithoutRemoteDevOverride.getValue();
    if (launcher != null) return launcher;

    if (PlatformUtils.isJetBrainsClient()) {
      // the client launched from a host IDE overrides `ApplicationNamesInfo#getScriptName`
      var launchData = IdeProductInfo.getInstance().getCurrentProductInfo().getLaunch();
      if (launchData.size() == 1) {
        var hostLauncher = PathManager.getHomeDir()
          .resolve(OS.CURRENT == OS.macOS ? ApplicationEx.PRODUCT_INFO_FILE_NAME_MAC : ApplicationEx.PRODUCT_INFO_FILE_NAME)
          .getParent()
          .resolve(launchData.get(0).getLauncherPath())
          .normalize();
        if (Files.exists(hostLauncher)) return hostLauncher;
      }
      Logger.getInstance(Restarter.class).error("Cannot find an actual launcher for the frontend");
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
    else if (OS.CURRENT == OS.Windows) {
      if (ourLauncher.getValue() == null) {
        problem = "cannot find the launcher executable in " + PathManager.getBinDir();
      }
      else {
        problem = checkRestarter("restarter.exe");
      }
    }
    else if (OS.CURRENT == OS.macOS) {
      if (ourLauncher.getValue() == null) {
        problem = "cannot find the launcher executable in " + PathManager.getHomeDir().resolve("MacOS");
      }
      else {
        problem = checkRestarter("restarter");
      }
    }
    else if (OS.CURRENT == OS.Linux) {
      if (ourLauncher.getValue() == null) {
        problem = "cannot find the launcher executable in " + PathManager.getBinDir();
      }
      else {
        problem = checkRestarter("restarter");
      }
    }
    else {
      problem = OS.CURRENT + " (" + System.getProperty("os.name") + ')';
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
    var restarter = PathManager.getBinDir().resolve(restarterName);
    return Files.isExecutable(restarter) ? null : "not an executable file: " + restarter;
  }

  @ApiStatus.Internal
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
    else if (OS.CURRENT == OS.Windows) {
      restartOnWindows(elevate, List.of(beforeRestart), mainAppArgs);
    }
    else if (OS.CURRENT == OS.macOS) {
      restartOnMac(List.of(beforeRestart), mainAppArgs);
    }
    else if (OS.CURRENT == OS.Linux) {
      restartOnLinux(List.of(beforeRestart), mainAppArgs);
    }
    else {
      throw new IOException("Cannot restart application: not supported.");
    }
  }

  public static @Nullable Path getIdeStarter() {
    // The RemDev starter binary is an implementation detail that should not be exposed externally
    return ourLauncherWithoutRemoteDevOverride.getValue();
  }

  private static void restartOnWindows(boolean elevate, List<String> beforeRestart, List<String> args) throws IOException {
    var starter = ourLauncher.getValue();
    if (starter == null) throw new IOException("Starter executable not found in " + PathManager.getBinDir());
    var command = prepareCommand("restarter.exe", beforeRestart);
    command.add(String.valueOf((elevate ? 2 : 1) + args.size()));
    if (elevate) {
      command.add(PathManager.getBinDir().resolve("launcher.exe").toString());
    }
    command.add(starter.toString());
    command.addAll(args);
    runRestarter(command);
  }

  private static void restartOnMac(List<String> beforeRestart, List<String> args) throws IOException {
    var starter = ourLauncher.getValue();
    if (starter == null) throw new IOException("Starter executable not found in: " + PathManager.getHomeDir());
    var command = prepareCommand("restarter", beforeRestart);
    command.add(String.valueOf(args.size() + 1));
    command.add(starter.toString());
    command.addAll(args);
    runRestarter(command);
  }

  private static void restartOnLinux(List<String> beforeRestart, List<String> args) throws IOException {
    var starterScript = ourLauncher.getValue();
    if (starterScript == null) throw new IOException("Starter script not found in " + PathManager.getBinDir());
    var command = prepareCommand("restarter", beforeRestart);
    command.add(String.valueOf(args.size() + 1));
    command.add(starterScript.toString());
    command.addAll(args);
    runRestarter(command);
  }

  @ApiStatus.Internal
  public static void setCopyRestarterFiles() {
    copyRestarterFiles = true;
  }

  @ApiStatus.Internal
  public static void setMainAppArgs(@NotNull List<String> args) {
    mainAppArgs = new ArrayList<>(args);
  }

  private static List<String> prepareCommand(String restarterName, List<String> beforeRestart) throws IOException {
    var restarter = PathManager.getBinDir().resolve(restarterName);
    var command = new ArrayList<String>();
    command.add(copyWhenNeeded(restarter, beforeRestart).toString());
    command.add(String.valueOf(ProcessHandle.current().pid()));
    if (!beforeRestart.isEmpty()) {
      command.add(String.valueOf(beforeRestart.size()));
      command.addAll(beforeRestart);
    }
    return command;
  }

  private static Path copyWhenNeeded(Path binFile, List<String> args) throws IOException {
    if (copyRestarterFiles || args.contains(UpdateInstaller.UPDATER_MAIN_CLASS)) {
      var tempDir = Files.createDirectories(PathManager.getSystemDir().resolve("restart"));
      return Files.copy(binFile, tempDir.resolve(binFile.getFileName()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }
    else {
      return binFile;
    }
  }

  private static void runRestarter(List<String> command) throws IOException {
    Logger.getInstance(Restarter.class).info("run restarter: " + command);

    @SuppressWarnings("IO_FILE_USAGE")
    var processBuilder = new ProcessBuilder(command)
      .directory(Path.of(SystemProperties.getUserHome()).toFile())
      .redirectOutput(ProcessBuilder.Redirect.DISCARD)
      .redirectError(ProcessBuilder.Redirect.DISCARD);
    processBuilder.environment().put("IJ_RESTARTER_LOG", PathManager.getLogDir().resolve("restarter.log").toString());

    if (OS.isGenericUnix()) setDesktopStartupId(processBuilder);

    processBuilder.environment().remove("IJ_LAUNCHER_DEBUG");

    processBuilder.start();
  }

  // this is required to support X server's focus stealing prevention mechanism, see JBR-2503
  private static void setDesktopStartupId(ProcessBuilder processBuilder) {
    if (SystemInfo.isJetBrainsJvm && StartupUiUtil.isXToolkit()) {
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
