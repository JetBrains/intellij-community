// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.BaseOutputReader;
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

  protected GitTextHandler(@NotNull Project project, @NotNull File directory, @NotNull GitCommand command) {
    super(project, directory, command, Collections.emptyList());
  }

  protected GitTextHandler(@NotNull Project project, @NotNull VirtualFile vcsRoot, @NotNull GitCommand command) {
    super(project, vcsRoot, command, Collections.emptyList());
  }

  protected GitTextHandler(@NotNull Project project,
                           @NotNull VirtualFile vcsRoot,
                           @NotNull GitCommand command,
                           List<String> configParameters) {
    super(project, vcsRoot, command, configParameters);
  }

  public GitTextHandler(@Nullable Project project,
                        @NotNull File directory,
                        @NotNull String pathToExecutable,
                        @NotNull GitCommand command,
                        @NotNull List<String> configParameters) {
    super(project, directory, pathToExecutable, command, configParameters);
  }

  public void setWithMediator(boolean value) {
    myWithMediator = value;
  }

  @Nullable
  @Override
  protected Process startProcess() throws ExecutionException {
    synchronized (myProcessStateLock) {
      if (myIsDestroyed) {
        return null;
      }
      final ProcessHandler processHandler = createProcess(myCommandLine);
      myHandler = (OSProcessHandler)processHandler;
      return myHandler.getProcess();
    }
  }

  protected void startHandlingStreams() {
    if (myHandler == null) {
      return;
    }
    myHandler.addProcessListener(new ProcessListener() {
      @Override
      public void startNotified(@NotNull final ProcessEvent event) {
        // do nothing
      }

      public void processTerminated(@NotNull final ProcessEvent event) {
        final int exitCode = event.getExitCode();
        try {
          setExitCode(exitCode);
          GitTextHandler.this.processTerminated(exitCode);
        }
        finally {
          listeners().processTerminated(exitCode);
        }
      }

      @Override
      public void processWillTerminate(@NotNull final ProcessEvent event, final boolean willBeDestroyed) {
        // do nothing
      }

      public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final Key outputType) {
        GitTextHandler.this.onTextAvailable(event.getText(), outputType);
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

  /**
   * This method is invoked when some text is available
   *
   * @param text       an available text
   * @param outputType output type
   */
  protected abstract void onTextAvailable(final String text, final Key outputType);

  public void destroyProcess() {
    synchronized (myProcessStateLock) {
      myIsDestroyed = true;
      if (myHandler != null) {
        myHandler.destroyProcess();
      }
    }
  }

  protected void waitForProcess() {
    if (myHandler != null) {
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      while (!myHandler.waitFor(WAIT_TIMEOUT_MS)) {
        try {
          if (indicator != null) {
            indicator.checkCanceled();
          }
        }
        catch (ProcessCanceledException pce) {
          if (!tryKill()) {
            LOG.error("Could not terminate [" + printableCommandLine() + "].");
          }
          throw pce;
        }
      }
    }
  }

  private boolean tryKill() {
    myHandler.destroyProcess();

    // signal was sent, but we still need to wait for process to finish its dark deeds
    if (myHandler.waitFor(TERMINATION_TIMEOUT_MS)) {
      return true;
    }

    LOG.warn("Soft-kill failed for [" + printableCommandLine() + "].");

    ExecutionManagerImpl.stopProcess(myHandler);
    return myHandler.waitFor(TERMINATION_TIMEOUT_MS);
  }

  protected ProcessHandler createProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return new MyOSProcessHandler(commandLine, myWithMediator && Registry.is("git.execute.with.mediator"));
  }

  protected static class MyOSProcessHandler extends KillableProcessHandler {
    protected MyOSProcessHandler(@NotNull GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException {
      super(commandLine, withMediator);
    }

    @NotNull
    @Override
    protected BaseOutputReader.Options readerOptions() {
      return Registry.is("git.blocking.read") ? BaseOutputReader.Options.BLOCKING : BaseOutputReader.Options.NON_BLOCKING;
    }
  }
}
