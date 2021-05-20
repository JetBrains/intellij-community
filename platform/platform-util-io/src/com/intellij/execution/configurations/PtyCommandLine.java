// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtilRt;
import com.pty4j.PtyProcessBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A flavor of GeneralCommandLine to start processes with Pseudo-Terminal (PTY).
 *
 * Warning: PtyCommandLine works with ProcessHandler only in blocking read mode.
 * Please make sure that you use appropriate ProcessHandler implementation.
 *
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

  private boolean myUseCygwinLaunch = false;
  /**
   * Setting this to true means that process started with this command line will works with our default ConsoleViewImpl.
   * <p>
   * Namely:
   * <ul>
   *   <li>Terminal echo suppressed (like {@code stty -echo}).</li>
   *   <li>Process {@code stderr} will be available separately from process {@code stdout}, unlike regular terminal, when they are merged together.</li>
   * </ul>
   * <p>
   * False means terminal console going to be used, which is working more like regular terminal window.
   */
  private boolean myConsoleMode = true;
  private int myInitialColumns = -1;
  private int myInitialRows = -1;
  private boolean myWindowsAnsiColorEnabled = !Boolean.getBoolean("pty4j.win.disable.ansi.in.console.mode");
  private boolean myUnixOpenTtyToPreserveOutputAfterTermination = true;

  public PtyCommandLine() { }

  public PtyCommandLine withUseCygwinLaunch(boolean useCygwinLaunch) {
    myUseCygwinLaunch = useCygwinLaunch;
    return this;
  }

  /**
   * @see #myConsoleMode
   */
  public PtyCommandLine withConsoleMode(boolean consoleMode) {
    myConsoleMode = consoleMode;
    return this;
  }

  /**
   * @see #myConsoleMode
   */
  public boolean isConsoleMode() {
    return myConsoleMode;
  }

  public PtyCommandLine withInitialColumns(int initialColumns) {
    myInitialColumns = initialColumns;
    return this;
  }

  public PtyCommandLine withInitialRows(int initialRows) {
    myInitialRows = initialRows;
    return this;
  }

  public PtyCommandLine(@NotNull List<String> command) {
    super(command);
  }

  public PtyCommandLine(@NotNull GeneralCommandLine original) {
    super(original);
    if (original instanceof PtyCommandLine) {
      myUseCygwinLaunch = ((PtyCommandLine)original).myUseCygwinLaunch;
      myConsoleMode = ((PtyCommandLine)original).myConsoleMode;
      myInitialColumns = ((PtyCommandLine)original).myInitialColumns;
      myInitialRows = ((PtyCommandLine)original).myInitialRows;
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
   * @see PtyProcessBuilder#setUnixOpenTtyToPreserveOutputAfterTermination(boolean)
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

    String[] command = ArrayUtilRt.toStringArray(commands);
    File workDirectory = getWorkDirectory();
    String directory = workDirectory != null ? workDirectory.getPath() : null;
    boolean cygwin = myUseCygwinLaunch && SystemInfo.isWindows;
    Application app = ApplicationManager.getApplication();
    PtyProcessBuilder builder = new PtyProcessBuilder(command)
      .setEnvironment(env)
      .setDirectory(directory)
      .setInitialColumns(myInitialColumns > 0 ? myInitialColumns : null)
      .setInitialRows(myInitialRows > 0 ? myInitialRows : null)
      .setConsole(myConsoleMode)
      .setCygwin(cygwin)
      .setLogFile(app != null && app.isEAP() ? new File(PathManager.getLogPath(), "pty.log") : null)
      .setRedirectErrorStream(isRedirectErrorStream())
      .setWindowsAnsiColorEnabled(myWindowsAnsiColorEnabled)
      .setUnixOpenTtyToPreserveOutputAfterTermination(myUnixOpenTtyToPreserveOutputAfterTermination);
    return builder.start();
  }
}