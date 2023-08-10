// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.process.ProcessService;
import com.intellij.execution.process.LocalPtyOptions;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A flavor of GeneralCommandLine to start processes with Pseudo-Terminal (PTY).
 * <p>
 * Warning: PtyCommandLine works with ProcessHandler only in blocking read mode.
 * Please make sure that you use appropriate ProcessHandler implementation.
 * <p>
 * Works for Linux, macOS, and Windows.
 * On Windows, PTY is emulated by creating an invisible console window (see Pty4j and WinPty implementation).
 */
public class PtyCommandLine extends GeneralCommandLine {
  private static final Logger LOG = Logger.getInstance(PtyCommandLine.class);
  private static final String RUN_PROCESSES_WITH_PTY = "run.processes.with.pty";

  public static final int MAX_COLUMNS = 2500;

  public static boolean isEnabled() {
    return Registry.is(RUN_PROCESSES_WITH_PTY);
  }

  private final LocalPtyOptions.Builder myOptionsBuilder = getDefaultPtyOptions().builder();
  private boolean myWindowsAnsiColorEnabled = !Boolean.getBoolean("pty4j.win.disable.ansi.in.console.mode");
  private boolean myUnixOpenTtyToPreserveOutputAfterTermination = true;

  public PtyCommandLine() { }

  public PtyCommandLine withUseCygwinLaunch(boolean useCygwinLaunch) {
    myOptionsBuilder.useCygwinLaunch(useCygwinLaunch);
    return this;
  }

  public PtyCommandLine withConsoleMode(boolean consoleMode) {
    myOptionsBuilder.consoleMode(consoleMode);
    return this;
  }

  public boolean isConsoleMode() {
    return myOptionsBuilder.consoleMode();
  }

  public PtyCommandLine withInitialColumns(int initialColumns) {
    myOptionsBuilder.initialColumns(initialColumns);
    return this;
  }

  public PtyCommandLine withInitialRows(int initialRows) {
    myOptionsBuilder.initialRows(initialRows);
    return this;
  }

  public PtyCommandLine withOptions(@NotNull LocalPtyOptions options) {
    myOptionsBuilder.set(options);
    return this;
  }

  public PtyCommandLine(@NotNull List<String> command) {
    super(command);
  }

  public PtyCommandLine(@NotNull GeneralCommandLine original) {
    super(original);
    if (original instanceof PtyCommandLine) {
      myOptionsBuilder.set(((PtyCommandLine)original).myOptionsBuilder.build());
    }
  }

  @NotNull
  PtyCommandLine withWindowsAnsiColorDisabled() {
    myWindowsAnsiColorEnabled = false;
    return this;
  }

  /**
   * Allow to preserve the subprocess output after its termination on certain *nix OSes (notably, macOS).
   * Side effect is that the subprocess won't terminate until all the output has been read from it.
   *
   * @see com.pty4j.PtyProcessBuilder#setUnixOpenTtyToPreserveOutputAfterTermination(boolean)
   */
  @NotNull
  public PtyCommandLine withUnixOpenTtyToPreserveOutputAfterTermination(boolean unixOpenTtyToPreserveOutputAfterTermination) {
    myUnixOpenTtyToPreserveOutputAfterTermination = unixOpenTtyToPreserveOutputAfterTermination;
    return this;
  }

  @NotNull
  @Override
  protected Process startProcess(@NotNull List<String> commands) throws IOException {
    if (getInputFile() == null) {
      try {
        return startProcessWithPty(commands);
      }
      catch (Throwable t) {
        String message = "Couldn't run process with PTY";
        if (LOG.isDebugEnabled()) {
          String logFileContent = loadLogFile();
          if (logFileContent != null) {
            LOG.debug(message, t, logFileContent);
          }
          else {
            LOG.warn(message, t);
          }
        }
        else {
          LOG.warn(message, t);
        }
      }
    }
    return super.startProcess(commands);
  }

  @Nullable
  private static String loadLogFile() {
    Application app = ApplicationManager.getApplication();
    File logFile = app != null && app.isEAP() ? new File(PathManager.getLogPath(), "pty.log") : null;
    if (logFile != null && logFile.exists()) {
      try {
        return FileUtil.loadFile(logFile);
      }
      catch (Exception e) {
        return "Unable to retrieve pty log: " + e.getMessage();
      }
    }
    return null;
  }

  @NotNull
  public Process startProcessWithPty(@NotNull List<String> commands) throws IOException {
    Map<String, String> env = new HashMap<>();
    setupEnvironment(env);
    if (!SystemInfo.isWindows) {
      // Let programs know about the emulator's capabilities to allow them produce appropriate escape sequences.
      // https://www.gnu.org/software/gettext/manual/html_node/The-TERM-variable.html
      // Moreover, some programs require TERM set, e.g. `/usr/bin/clear` or Python code `os.system("clear")`.
      // The following error will be reported if TERM is missing: "TERM environment variable set not set."
      if (!getEnvironment().containsKey("TERM")) {
        env.put("TERM", "xterm-256color");
      }
    }

    String[] command = ArrayUtilRt.toStringArray(commands);
    File workDirectory = getWorkDirectory();
    String directory = workDirectory != null ? workDirectory.getPath() : null;
    LocalPtyOptions options = myOptionsBuilder.build();
    Application app = ApplicationManager.getApplication();
    return ProcessService.getInstance()
      .startPtyProcess(command, directory, env, options, app, isRedirectErrorStream(), myWindowsAnsiColorEnabled,
                       myUnixOpenTtyToPreserveOutputAfterTermination);
  }

  public static @NotNull LocalPtyOptions getDefaultPtyOptions() {
    return LocalPtyOptions.DEFAULT.builder()
      .consoleMode(true)
      .useWinConPty(LocalPtyOptions.shouldUseWinConPty())
      .build();
  }
}