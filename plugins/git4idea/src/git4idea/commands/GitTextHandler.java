// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.application.ApplicationManager;
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

  /** @deprecated always {@code false} since a mediator is no longer used */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  protected boolean myWithMediator = false;
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

  /** @deprecated no-op since a mediator is no longer used */
  @Deprecated(forRemoval = true)
  @SuppressWarnings({"DeprecatedIsStillUsed", "unused"})
  public void setWithMediator(boolean value) { }

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
      listeners().processStarted();
      return myHandler.getProcess();
    }
  }

  @Override
  protected void startHandlingStreams() {
    myHandler.addProcessListener(new ProcessListener() {
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
          tryKillProcess();
          throw pce;
        }
      }
    }
  }

  private void tryKillProcess() {
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      myHandler.destroyProcess();
    });

    if (ApplicationManager.getApplication().isReadAccessAllowed() ||
        shouldSuppressReadLocks()) {
      // Some Git operations are called while holding the global locks.
      // Ex: access to the 'GitIndexVirtualFile' or 'GitDirectoryVirtualFile'.
      // In this case, we should not delay the current thread cancellation.
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        waitAndHardKillProcess();
      });
    }
    else {
      ProgressManager.getInstance().executeNonCancelableSection(() -> {
        waitAndHardKillProcess();
      });
    }
  }

  private void waitAndHardKillProcess() {
    // The signal was sent, but we still need to wait for the process to finish its dark deeds
    if (myHandler.waitFor(myTerminationTimeoutMs)) {
      return;
    }

    LOG.warn("Soft-kill failed for [" + printableCommandLine() + "].");

    ExecutionManagerImpl.stopProcess(myHandler);
    if (myHandler.waitFor(myTerminationTimeoutMs)) {
      return;
    }

    LOG.warn("Could not terminate [" + printableCommandLine() + "].");
  }

  protected OSProcessHandler createProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return new MyOSProcessHandler(commandLine);
  }

  protected static class MyOSProcessHandler extends KillableProcessHandler {
    protected MyOSProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
      super(commandLine);
    }

    /** @deprecated a mediator is no longer used; replace with {@link #MyOSProcessHandler(GeneralCommandLine)} */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("unused")
    protected MyOSProcessHandler(@NotNull GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException {
      this(commandLine);
    }

    @Override
    protected @NotNull BaseOutputReader.Options readerOptions() {
      return Registry.is("git.blocking.read") ? BaseOutputReader.Options.BLOCKING : BaseOutputReader.Options.NON_BLOCKING;
    }
  }
}
