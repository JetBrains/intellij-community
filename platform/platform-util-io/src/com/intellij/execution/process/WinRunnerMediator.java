// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class to start a process on Windows with a runner mediator (runnerw.exe) injected into a command line,
 * which adds a capability to terminate process tree gracefully by sending it Ctrl+Break or Ctrl+C signals through the stdin.
 *
 * @deprecated processes are killed softly on Windows be default now, see {@link KillableProcessHandler#canTerminateGracefullyWithWinP()}
 */
@Deprecated(forRemoval = true)
public final class WinRunnerMediator {
  private static final Logger LOG = Logger.getInstance(WinRunnerMediator.class);

  private static final char IAC = (char)5;
  private static final char BRK = (char)3;
  private static final char C = (char)5;
  private static final String RUNNERW = "runnerw.exe";
  private static final String IDEA_RUNNERW = "IDEA_RUNNERW";
  private static final Key<Boolean> MEDIATOR_KEY = Key.create("KillableProcessHandler.Mediator.Process");

  private WinRunnerMediator() {}

  /**
   * Sends sequence of two chars(codes 5 and {@code event}) to a process output stream
   */
  private static boolean sendCtrlEventThroughStream(@NotNull Process process, char event) {
    OutputStream os = process.getOutputStream();
    if (os != null) {
      try {
        os.write(IAC);
        os.write(event);
        os.flush();
        return true;
      }
      catch (IOException e) {
        LOG.info("Cannot send " + IAC + "+" + event + " to runnerw", e);
      }
    }
    return false;
  }

  private static @Nullable String getRunnerPath() {
    if (!SystemInfo.isWindows) {
      throw new IllegalStateException("There is no need of runner under unix based OS");
    }

    String path = System.getenv(IDEA_RUNNERW);
    if (path != null) {
      if (new File(path).exists()) {
        return path;
      }
      LOG.warn("Cannot locate a runner at " + path + " (as told by " + IDEA_RUNNERW + ')');
    }

    Path runnerw = PathManager.findBinFile(RUNNERW);
    if (runnerw != null && Files.exists(runnerw)) {
      return runnerw.toString();
    }
    LOG.warn("Cannot locate " + RUNNERW + " in " + PathManager.getBinPath());

    return null;
  }

  static void injectRunnerCommand(@NotNull GeneralCommandLine commandLine, boolean showConsole) {
    if (!SystemInfo.isWindows || isRunnerCommandInjected(commandLine)) {
      return;
    }
    final String path = getRunnerPath();
    if (path != null) {
      commandLine.getParametersList().addAt(0, commandLine.getExePath());
      if (showConsole) {
        commandLine.getParametersList().addAt(0, "/C");
      }
      commandLine.setExePath(path);
      MEDIATOR_KEY.set(commandLine, true);
    }
  }

  static boolean isRunnerCommandInjected(@NotNull GeneralCommandLine commandLine) {
    return MEDIATOR_KEY.get(commandLine) == Boolean.TRUE;
  }

  /**
   * Sends Ctrl+C or Ctrl+Break signals through stdin to runnerw.exe which will generate
   * corresponding events to all console processes attached to the console.
   *
   * @param process to kill with all sub-processes.
   */
  static boolean destroyProcess(final @NotNull Process process, @SuppressWarnings("SameParameterValue") boolean softKill) {
    if (SystemInfo.isWindows) {
      return sendCtrlEventThroughStream(process, softKill ? C : BRK);
    }
    return false;
  }

  /**
   * Show external console, only used by CLion, but CLion will migrate to its own implementation soon
   */
  public static void withExternalConsole(@NotNull GeneralCommandLine commandLine) {
    injectRunnerCommand(commandLine, true);
  }
}
