// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.BaseOutputReader;
import git4idea.config.GitExecutable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * The handler for git commands with text outputs
 */
public abstract class GitTextHandler extends GitHandler {
  private static final int WAIT_TIMEOUT_MS = 50;
  private static final int TERMINATION_TIMEOUT_MS = 1000 * 60;
  // note that access is safe because it accessed in unsynchronized block only after process is started, and it does not change after that
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private OSProcessHandler myHandler;
  private volatile boolean myIsDestroyed;
  private final Object myProcessStateLock = new Object();

  protected boolean myWithMediator = true;
  private int myTerminationTimeoutMs = TERMINATION_TIMEOUT_MS;

  protected GitTextHandler(@Nullable Project project, @NotNull File directory, @NotNull GitCommand command) {
    super(project, directory, command, Collections.emptyList());
  }

  protected GitTextHandler(@Nullable Project project, @NotNull VirtualFile vcsRoot, @NotNull GitCommand command) {
    super(project, vcsRoot, command, Collections.emptyList());
  }

  protected GitTextHandler(@Nullable Project project,
                           @NotNull VirtualFile vcsRoot,
                           @NotNull GitCommand command,
                           List<String> configParameters) {
    super(project, vcsRoot, command, configParameters);
  }

  public GitTextHandler(@Nullable Project project,
                        @NotNull File directory,
                        @NotNull GitExecutable executable,
                        @NotNull GitCommand command,
                        @NotNull List<String> configParameters) {
    super(project, directory, executable, command, configParameters);
  }

  public void setWithMediator(boolean value) {
    myWithMediator = value;
  }

  public void setTerminationTimeout(int timeoutMs) {
    myTerminationTimeoutMs = timeoutMs;
  }

  @Override
  protected @Nullable Process startProcess() throws ExecutionException {
    synchronized (myProcessStateLock) {
      if (myIsDestroyed) {
        return null;
      }
      myHandler = createProcess(myCommandLine);
      return myHandler.getProcess();
    }
  }

  @Override
  protected void startHandlingStreams() {
    myHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(final @NotNull ProcessEvent event) {
        final int exitCode = event.getExitCode();
        OUTPUT_LOG.debug(String.format("%s %% %s terminated (%s)", getCommand(), GitTextHandler.this.hashCode(), exitCode));
        try {
          setExitCode(exitCode);
          GitTextHandler.this.processTerminated(exitCode);
        }
        finally {
          listeners().processTerminated(exitCode);
        }
      }
    });
    myHandler.startNotify();
  }

  /**
   * Notification for handler to handle process exit event
   *
   * @param exitCode a exit code.
   */
  protected abstract void processTerminated(int exitCode);

  @Override
  protected void waitForProcess() {
    if (myHandler != null) {
      while (!myHandler.waitFor(WAIT_TIMEOUT_MS)) {
        try {
          ProgressManager.checkCanceled();
        }
        catch (ProcessCanceledException pce) {
          ProgressManager.getInstance().executeNonCancelableSection(() -> {
            if (!tryKill()) {
              LOG.warn("Could not terminate [" + printableCommandLine() + "].");
            }
          });
          throw pce;
        }
      }
    }
  }

  private boolean tryKill() {
    myHandler.destroyProcess();

    // signal was sent, but we still need to wait for process to finish its dark deeds
    if (myHandler.waitFor(myTerminationTimeoutMs)) {
      return true;
    }

    LOG.warn("Soft-kill failed for [" + printableCommandLine() + "].");

    ExecutionManagerImpl.stopProcess(myHandler);
    return myHandler.waitFor(myTerminationTimeoutMs);
  }

  protected OSProcessHandler createProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return new MyOSProcessHandler(commandLine, myWithMediator && myExecutable.isLocal() && Registry.is("git.execute.with.mediator"));
  }

  protected static class MyOSProcessHandler extends KillableProcessHandler {
    protected MyOSProcessHandler(@NotNull GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException {
      super(commandLine, withMediator);
    }

    @Override
    protected @NotNull BaseOutputReader.Options readerOptions() {
      return Registry.is("git.blocking.read") ? BaseOutputReader.Options.BLOCKING : BaseOutputReader.Options.NON_BLOCKING;
    }
  }
}
