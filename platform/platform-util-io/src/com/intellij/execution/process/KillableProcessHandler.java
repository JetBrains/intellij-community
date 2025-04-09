// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.google.common.base.Ascii;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;

/**
 * This process handler supports the "soft-kill" / "graceful termination" feature.
 * For example, a process is terminated gracefully ({@link #destroyProcessGracefully()}) when "Stop" button is clicked for the first time,
 * and the process is terminated forcibly ({@link #killProcess()}) on subsequent clicks.
 * <p>
 * On Unix, graceful termination corresponds to sending SIGINT signal.
 * On Windows, graceful termination executes GenerateConsoleCtrlEvent under the hood.
 */
public class KillableProcessHandler extends OSProcessHandler implements KillableProcess {
  private static final Logger LOG = Logger.getInstance(KillableProcessHandler.class);

  private boolean myShouldKillProcessSoftly = true;
  private boolean myShouldKillProcessSoftlyWithWinP = SystemInfo.isWin10OrNewer && Registry.is("use.winp.for.graceful.process.termination");

  public KillableProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
  }

  protected KillableProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
    super(process, commandLine.getCommandLineString(), commandLine.getCharset());
  }

  /** @deprecated the mediator is retired; use {@link #KillableProcessHandler(GeneralCommandLine)} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public KillableProcessHandler(@NotNull GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException {
    this(commandLine);
  }

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public KillableProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine) {
    super(process, commandLine);
  }

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public KillableProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @NotNull Charset charset) {
    this(process, commandLine, charset, null);
  }

  /**
   * {@code commandLine} must not be empty (for correct thread attribution in the stacktrace)
   */
  public KillableProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @NotNull Charset charset, @Nullable Set<File> filesToDelete) {
    super(process, commandLine, charset, filesToDelete);
  }

  /**
   * @return {@code true} if graceful process termination should be attempted first
   */
  public boolean shouldKillProcessSoftly() {
    return myShouldKillProcessSoftly;
  }

  /**
   * Sets whether the process will be terminated gracefully.
   *
   * @param shouldKillProcessSoftly {@code true} if graceful process termination should be attempted first (i.e. "soft kill")
   */
  public void setShouldKillProcessSoftly(boolean shouldKillProcessSoftly) {
    myShouldKillProcessSoftly = shouldKillProcessSoftly;
  }

  /**
   * This method shouldn't be overridden, see {@link #shouldKillProcessSoftly}
   * @see #destroyProcessGracefully
   */
  protected final boolean canDestroyProcessGracefully() {
    if (processCanBeKilledByOS(myProcess)) {
      if (SystemInfo.isWindows) {
        return hasPty() || canTerminateGracefullyWithWinP();
      }
      if (SystemInfo.isUnix) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void destroyProcessImpl() {
    // Don't close streams, because a process may survive graceful termination.
    // Streams will be closed after the process is really terminated.
    try {
      myProcess.getOutputStream().flush();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    finally {
      doDestroyProcess();
    }
  }

  @Override
  protected void doDestroyProcess() {
    if (myProcess instanceof SelfKiller selfKiller) {
      if (!selfKiller.tryDestroyGracefully()) {
        super.doDestroyProcess();
      }
      return;
    }
    boolean gracefulTerminationAttempted = shouldKillProcessSoftly() && canDestroyProcessGracefully() && destroyProcessGracefully();
    if (!gracefulTerminationAttempted) {
      // execute default process destroy
      super.doDestroyProcess();
    }
  }

  /**
   * Enables sending Ctrl+C to a Windows-process on first termination attempt.
   * This is an experimental API which will be removed in future releases once stabilized.
   * Please do not use this API.
   * @param shouldKillProcessSoftlyWithWinP true to use
   * @deprecated graceful termination with WinP is enabled by default; please don't use this method
   */
  @ApiStatus.Experimental
  @Deprecated(forRemoval = true)
  public void setShouldKillProcessSoftlyWithWinP(boolean shouldKillProcessSoftlyWithWinP) {
    myShouldKillProcessSoftlyWithWinP = shouldKillProcessSoftlyWithWinP;
  }

  private boolean canTerminateGracefullyWithWinP() {
    return myShouldKillProcessSoftlyWithWinP && SystemInfo.isWin10OrNewer && !isWslProcess();
  }

  /**
   * Checks if the process is WSL.
   * WinP's graceful termination doesn't work for Linux processes started inside WSL, like
   * "wsl.exe -d Ubuntu-20.04 --exec <linux command>", because WinP's `org.jvnet.winp.WinProcess.sendCtrlC`
   * uses `GenerateConsoleCtrlEvent` under the hood and `GenerateConsoleCtrlEvent` doesn't terminate Linux
   * processes running in WSL. Instead, it terminates wsl.exe process only.
   * See <a href="https://github.com/microsoft/WSL/issues/7301">WSL issue #7301</a> for the details.
   */
  private boolean isWslProcess() {
    List<String> command = getProcessService().getCommand(myProcess);
    String executable = ContainerUtil.getFirstItem(command);
    boolean wsl = executable != null && PathUtil.getFileName(executable).equals("wsl.exe");
    if (wsl) {
      LOG.info("WinP graceful termination does not work for WSL process: " + command);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("[graceful termination with WinP] WSL process: " + wsl + ", process: " + myProcess.getClass() + ", command: " + command);
    }
    return wsl;
  }

  protected boolean destroyProcessGracefully() {
    LocalProcessService processService = getProcessService();
    if (SystemInfo.isWindows) {
      if (processService.hasControllingTerminal(myProcess) &&
          WinProcessTerminator.terminateWinProcessGracefully(this, processService, this::sendInterruptToPtyProcess)) {
        return true;
      }
      if (canTerminateGracefullyWithWinP() && !Registry.is("disable.winp")) {
        try {
          if (!myProcess.isAlive()) {
            OSProcessUtil.logSkippedActionWithTerminatedProcess(myProcess, "destroy", getCommandLineForLog());
            return true;
          }
          return WinProcessTerminator.terminateWinProcessGracefully(this, processService);
        }
        catch (Throwable e) {
          if (!myProcess.isAlive()) {
            OSProcessUtil.logSkippedActionWithTerminatedProcess(myProcess, "destroy", getCommandLineForLog());
            return true;
          }
          String message = e.getMessage();
          if (message != null && message.contains(".exe terminated with exit code 6,")) {
            // https://github.com/kohsuke/winp/blob/ec4ac6a988f6e3909c57db0abc4b02ff1b1d2e05/native/sendctrlc/main.cpp#L18
            // WinP uses AttachConsole(pid) which might fail if the specified process does not have a console.
            // In this case, the error code returned is ERROR_INVALID_HANDLE (6).
            // Let's fall back to the default termination without logging an error.
            String msg = "Cannot send Ctrl+C to process without a console (fallback to default termination)";
            if (LOG.isDebugEnabled()) {
              LOG.debug(msg + " " + getCommandLineForLog());
            }
            else {
              LOG.info(msg);
            }
          }
          else {
            LOG.error("Cannot send Ctrl+C (fallback to default termination) " + getCommandLineForLog(), e);
          }
        }
      }
    }
    else if (SystemInfo.isUnix) {
      if (processService.hasControllingTerminal(myProcess) && sendInterruptToPtyProcess()) {
        return true;
      }
      if (shouldDestroyProcessRecursively()) {
        return UnixProcessManager.sendSigIntToProcessTree(myProcess);
      }
      return UnixProcessManager.sendSignal(UnixProcessManager.getProcessId(myProcess), UnixProcessManager.SIGINT) == 0;
    }
    return false;
  }

  private static @NotNull LocalProcessService getProcessService() {
    // Without non-cancelable section "ProcessService.getInstance()" will fail under a canceled progress.
    return ProgressManager.getInstance().computeInNonCancelableSection(LocalProcessService::getInstance);
  }

  /**
   * Writes the INTR (interrupt) character to process's stdin (PTY). When a PTY receives the INTR character,
   * it raises a SIGINT signal for all processes in the foreground job associated with the PTY. The character itself is then discarded.
   * <p>A proper way to get INTR is `termios.c_cc[VINTR]`. However, unlikely, the default (003, ETX) is changed.
   * <p>Works on Unix and Windows.
   * 
   * @see <a href="https://man7.org/linux/man-pages/man3/tcflow.3.html">termios(3)</a>
   * @see <a href="https://www.gnu.org/software/libc/manual/html_node/Signal-Characters.html">Characters that Cause Signals</a>
   * 
   * @return true if the character has been written successfully
   */
  private boolean sendInterruptToPtyProcess() {
    OutputStream outputStream = myProcess.getOutputStream();
    if (outputStream != null) {
      try {
        outputStream.write(Ascii.ETX);
        outputStream.flush();
        return true;
      }
      catch (IOException e) {
        LOG.info("Failed to send Ctrl+C to PTY process. Fallback to default graceful termination.", e);
      }
    }
    return false;
  }

  @Override
  public boolean canKillProcess() {
    return processCanBeKilledByOS(getProcess()) || getProcess() instanceof ProcessTreeKiller;
  }

  @Override
  public void killProcess() {
    if (processCanBeKilledByOS(getProcess())) {
      // execute 'kill -SIGKILL <pid>' on Unix
      killProcessTree(getProcess());
    }
    else if (getProcess() instanceof ProcessTreeKiller) {
      ((ProcessTreeKiller)getProcess()).killProcessTree();
    }
  }
}
