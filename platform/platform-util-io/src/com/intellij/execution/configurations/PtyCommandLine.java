// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.execution.process.LocalPtyOptions;
import com.intellij.execution.process.ProcessService;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A flavor of GeneralCommandLine to start processes with Pseudo-Terminal (PTY).
 * <p>
 * Warning: PtyCommandLine works with {@link com.intellij.execution.process.ProcessHandler} only in blocking read mode.
 * Please make sure that you use an appropriate {@code ProcessHandler} implementation.
 * <p>
 * Works for Linux, macOS, and Windows.
 * On Windows, PTY is emulated by creating an invisible console window (see Pty4J and WinPty implementation).
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
  private boolean myUnixOpenTtyToPreserveOutputAfterTermination = SystemProperties.getBooleanProperty("pty4j.open.child.tty", SystemInfo.isMac);

  public PtyCommandLine() { }

  public PtyCommandLine(@NotNull List<String> command) {
    super(command);
  }

  public PtyCommandLine(@NotNull GeneralCommandLine original) {
    super(original);
    if (original instanceof PtyCommandLine) {
      myOptionsBuilder.set(((PtyCommandLine)original).myOptionsBuilder.build());
    }
  }

  public PtyCommandLine withUseCygwinLaunch(boolean useCygwinLaunch) {
    myOptionsBuilder.useCygwinLaunch(useCygwinLaunch);
    return this;
  }

  public PtyCommandLine withConsoleMode(boolean consoleMode) {
    myOptionsBuilder.consoleMode(consoleMode);
    return this;
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

  @NotNull
  PtyCommandLine withWindowsAnsiColorDisabled() {
    myWindowsAnsiColorEnabled = false;
    return this;
  }

  /**
   * Allow preserving the subprocess output after its termination on certain *nix OSes (notably, macOS).
   * One side effect is that the subprocess won't terminate until all the output has been read from it.
   *
   * @see com.pty4j.PtyProcessBuilder#setUnixOpenTtyToPreserveOutputAfterTermination(boolean)
   */
  public @NotNull PtyCommandLine withUnixOpenTtyToPreserveOutputAfterTermination(boolean unixOpenTtyToPreserveOutputAfterTermination) {
    myUnixOpenTtyToPreserveOutputAfterTermination = unixOpenTtyToPreserveOutputAfterTermination;
    return this;
  }

  @Override
  protected @NotNull Process createProcess(@NotNull ProcessBuilder processBuilder) throws IOException {
    if (getInputFile() == null && !isProcessCreatorSet() && tryGetEel() == null) {
      try {
        return startProcessWithPty(processBuilder.command());
      }
      catch (Throwable t) {
        var message = "Couldn't run process with PTY";
        if (LOG.isDebugEnabled()) {
          var logFileContent = loadLogFile();
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
    return super.createProcess(processBuilder);
  }

  private static @Nullable String loadLogFile() {
    var app = ApplicationManager.getApplication();
    if (app != null && app.isEAP()) {
      var logFile = PathManager.getLogDir().resolve("pty.log");
      if (Files.exists(logFile)) {
        try {
          return Files.readString(logFile);
        }
        catch (Exception e) {
          return "Unable to retrieve PTY log: " + e.getMessage();
        }
      }
    }
    return null;
  }

  public @NotNull LocalPtyOptions getPtyOptions() {
    return myOptionsBuilder.build();
  }

  @ApiStatus.Internal
  public @NotNull Process startProcessWithPty(@NotNull List<String> commands) throws IOException {
    Map<String, String> env = new HashMap<>();
    setupEnvironment(env);
    if (!SystemInfo.isWindows) {
      // Let programs know about the emulator's capabilities to allow them to produce appropriate escape sequences.
      // https://www.gnu.org/software/gettext/manual/html_node/The-TERM-variable.html
      // Moreover, some programs require `$TERM` to be set, e.g. `/usr/bin/clear` or Python code `os.system("clear")`.
      // The following error will be reported if `$TERM` is missing: "TERM environment variable set not set."
      if (!getEnvironment().containsKey("TERM")) {
        env.put("TERM", "xterm-256color");
      }
    }

    String[] command = ArrayUtilRt.toStringArray(commands);
    File workDirectory = getWorkDirectory();
    String directory = workDirectory != null ? workDirectory.getPath() : null;
    LocalPtyOptions options = getPtyOptions();
    Application app = ApplicationManager.getApplication();
    return ProcessService.getInstance()
      .startPtyProcess(command, directory, env, options, app, isRedirectErrorStream(), myWindowsAnsiColorEnabled, myUnixOpenTtyToPreserveOutputAfterTermination);
  }

  @ApiStatus.Internal
  public static @NotNull LocalPtyOptions getDefaultPtyOptions() {
    return LocalPtyOptions.defaults().builder().consoleMode(true).build();
  }
}
