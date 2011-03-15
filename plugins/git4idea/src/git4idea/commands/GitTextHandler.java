/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.config.GitVersionSpecialty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;

/**
 * The handler for git commands with text outputs
 */
public abstract class GitTextHandler extends GitHandler {
  // note that access is safe because it accessed in unsynchronized block only after process is started, and it does not change after that
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private OSProcessHandler myHandler;
  private volatile boolean myIsDestroyed;
  private final Object myProcessStateLock = new Object();
  private static final Logger LOG = Logger.getInstance(GitTextHandler.class.getName());

  protected GitTextHandler(@NotNull Project project, @NotNull File directory, @NotNull GitCommand command) {
    super(project, directory, command);
  }

  protected GitTextHandler(final Project project, final VirtualFile vcsRoot, final GitCommand command) {
    super(project, vcsRoot, command);
  }

  @Nullable
  @Override
  protected Process startProcess() throws ExecutionException {
    synchronized (myProcessStateLock) {
      if (myIsDestroyed) {
        return null;
      }
      final ProcessHandler processHandler = new MyRunnerMediator().createProcess(myCommandLine);
      myHandler = (OSProcessHandler)processHandler;
      return myHandler.getProcess();
    }
  }

  protected void startHandlingStreams() {
    myHandler.addProcessListener(new ProcessListener() {
      public void startNotified(final ProcessEvent event) {
        // do nothing
      }

      public void processTerminated(final ProcessEvent event) {
        final int exitCode = event.getExitCode();
        setExitCode(exitCode);
        cleanupEnv();
        GitTextHandler.this.processTerminated(exitCode);
        listeners().processTerminated(exitCode);
      }

      public void processWillTerminate(final ProcessEvent event, final boolean willBeDestroyed) {
        // do nothing
      }

      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
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
      myHandler.waitFor();
    }
  }

  /**
   * RunnerMediator that attaches our MyProcessHandler to the process instead of the standard CustomDestroyProcessHandler.
   */
  private class MyRunnerMediator extends RunnerMediator {
    private boolean canUseRunnerMediator;

    MyRunnerMediator() {
      canUseRunnerMediator = !GitVersionSpecialty.DOESNT_GET_PARAMETERS_FROM_RUNNERW.existsIn(myVcs.getVersion());
    }

    @Override
    public ProcessHandler createProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
      if (!canUseRunnerMediator) {
        // not injecting runnerw in case of cygwin, because runnerw doesn't pass parameters to cygwin git.
        // the reason of this is under investigation, until then we're running cygwin git directly.
        Process process = commandLine.createProcess();
        return createProcessHandler(process, commandLine);
      } else {
        return super.createProcess(commandLine);
      }
    }

    @Override
    protected ProcessHandler createProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
      return new MyOSProcessHandler(process, commandLine, getCharset());
    }
  }

  private static class MyOSProcessHandler extends OSProcessHandler {
    private final Charset myCharset;

    public MyOSProcessHandler(Process process, GeneralCommandLine commandLine, Charset charset) {
      super(process, commandLine.getCommandLineString());
      myCharset = charset;
    }

    @Override
    public Charset getCharset() {
      Charset charset = myCharset;
      return charset == null ? super.getCharset() : charset;
    }

    @Override
    protected void destroyProcessImpl() {
      if (!RunnerMediator.destroyProcess(getProcess())) {
        super.destroyProcessImpl();
      }
    }
  }

}
