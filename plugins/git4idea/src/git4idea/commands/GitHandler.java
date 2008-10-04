/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.EventDispatcher;
import git4idea.GitSSHGUIHandler;
import git4idea.GitSSHService;
import git4idea.GitVcs;
import git4idea.GitVcsSettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A handler for git commands
 */
public abstract class GitHandler {
  /**
   * the logger
   */
  private static final Logger log = Logger.getInstance(GitHandler.class.getName());
  /**
   * a command line
   */
  private final GeneralCommandLine myCommandLine;
  /**
   * wrapped process handler
   */
  // note that access is safe beacause it accessed in unsychronized block only after process is started, and it does not change after that
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private OSProcessHandler myHandler;
  /**
   * process
   */
  private Process myProcess;
  /**
   * the contenxt project (might be a default project)
   */
  private final Project myProject;
  /**
   * the working directory
   */
  private final File myWorkingDirectory;
  /**
   * the flag indicating that environment has been cleaned up, by default is true because there is nothing to clean
   */
  private boolean myEnvironmentCleanedUp = true;
  /**
   * the handler number
   */
  private int myHandlerNo;
  /**
   * if true process might be cancelled
   */
  // note that access is safe beacause it accessed in unsychronized block only after process is started, and it does not change after that
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private boolean myIsCancellable = true;
  /**
   * exit code or null if exit code is not yet availale
   */
  private Integer myExitCode;
  /**
   * listeners
   */
  private final EventDispatcher<GitHandlerListener> myListeners = EventDispatcher.create(GitHandlerListener.class);

  /**
   * A constructor
   *
   * @param project   a project
   * @param directory a process directory
   * @param command   a command to execute
   */
  protected GitHandler(@NotNull Project project, @NotNull File directory, @NotNull String command) {
    myProject = project;
    GitVcsSettings settings = GitVcsSettings.getInstance(project);
    myWorkingDirectory = directory;
    myCommandLine = new GeneralCommandLine();
    myCommandLine.setExePath(settings.GIT_EXECUTABLE);
    myCommandLine.setWorkingDirectory(myWorkingDirectory);
    myCommandLine.addParameter(command);
  }

  /**
   * @return a context project
   */
  public Project project() {
    return myProject;
  }


  /**
   * Add listner to handler
   *
   * @param listener a listener
   */
  protected void addListener(GitHandlerListener listener) {
    myListeners.addListener(listener);
  }

  /**
   * End option parameters and start file paths. The method adds {@code "--"} parameter.
   */
  public void endOptions() {
    myCommandLine.addParameter("--");
  }

  /**
   * Add string parameters
   *
   * @param parameters a parameters to add
   */
  public void addParameters(@NonNls @NotNull String... parameters) {
    checkNotStarted();
    myCommandLine.addParameters(parameters);
  }

  /**
   * Add filepath parameters. The parameters are made relative to the working directory
   *
   * @param parameters a parameters to add
   * @throws IllegalArgumentException if some path is not under root.
   */
  public void addRelativePaths(@NotNull FilePath... parameters) {
    addRelativePaths(Arrays.asList(parameters));
  }

  /**
   * Add filepath parameters. The parameters are made relative to the working directory
   *
   * @param filePaths a parameters to add
   * @throws IllegalArgumentException if some path is not under root.
   */
  public void addRelativePaths(@NotNull final Collection<FilePath> filePaths) {
    checkNotStarted();
    for (FilePath path : filePaths) {
      myCommandLine.addParameter(relativePath(myWorkingDirectory, path));
    }
  }

  /**
   * Get relative path
   *
   * @param root a root path
   * @param path a path to file (possibly deleted file)
   * @return a relative path
   * @throws IllegalArgumentException if path is not under root.
   */
  private static String relativePath(final File root, FilePath path) {
    String rc = FileUtil.getRelativePath(root, path.getIOFile());
    if (rc == null) {
      throw new IllegalArgumentException("The file " + path.getIOFile() + " cannot be made relative to " + root);
    }
    return rc.replace(File.separatorChar, '/');
  }


  /**
   * check that process is not started yet
   *
   * @throws IllegalStateException if process has been already started
   */
  private void checkNotStarted() {
    if (isStarted()) {
      throw new IllegalStateException("The process has been already started");
    }
  }

  /**
   * check that process is started
   *
   * @throws IllegalStateException if process has not been started
   */
  protected final void checkStarted() {
    if (!isStarted()) {
      throw new IllegalStateException("The process is not started yet");
    }
  }

  /**
   * @return true if process is started
   */
  public final synchronized boolean isStarted() {
    return myProcess != null;
  }


  /**
   * Set new value of cacellable flag (by default true)
   *
   * @param value a new value of the flag
   */
  public void setCancellable(boolean value) {
    checkNotStarted();
    myIsCancellable = value;
  }

  /**
   * @return cancellable state
   */
  public boolean isCancellable() {
    return myIsCancellable;
  }

  /**
   * Start process
   */
  public synchronized void start() {
    checkNotStarted();
    try {
      // setup environment
      if (!myProject.isDefault()) {
        GitVcs.getInstance(myProject).showMessages(printCommandLine());
      }
      if (log.isDebugEnabled()) {
        log.debug("running git: " + myCommandLine.getCommandLineString() + " in " + myWorkingDirectory);
      }
      GitSSHService ssh = GitSSHService.getInstance();
      final Map<String, String> env = new HashMap<String, String>(System.getenv());
      env.put(GitSSHService.GIT_SSH_ENV, ssh.getScriptPath().getPath());
      myHandlerNo = ssh.registerHandler(new GitSSHGUIHandler(myProject));
      myEnvironmentCleanedUp = false;
      env.put(GitSSHService.SSH_HANDLER_ENV, Integer.toString(myHandlerNo));
      myCommandLine.setEnvParams(env);
      // start process
      myProcess = myCommandLine.createProcess();
      myHandler = new OSProcessHandler(myProcess, myCommandLine.getCommandLineString());
      myHandler.addProcessListener(new ProcessListener() {
        public void startNotified(final ProcessEvent event) {
          // do nothing
        }

        public void processTerminated(final ProcessEvent event) {
          final int exitCode = event.getExitCode();
          setExitCode(exitCode);
          cleanupEnv();
          myListeners.getMulticaster().processTerminted(exitCode);
        }

        public void processWillTerminate(final ProcessEvent event, final boolean willBeDestroyed) {
          // do nothing
        }

        public void onTextAvailable(final ProcessEvent event, final Key outputType) {
          GitHandler.this.onTextAvailable(event.getText(), outputType);
        }
      });
      myHandler.startNotify();
    }
    catch (Throwable t) {
      log.error("Creation of git process failed", t);
      cleanupEnv();
      myListeners.getMulticaster().startFailed(t);
    }
  }

  /**
   * @return a command line with full path to executable replace to "git"
   */
  public String printCommandLine() {
    final GeneralCommandLine line = myCommandLine.clone();
    line.setExePath("git");
    return line.getCommandLineString();
  }

  /**
   * This method is invoked when some text is available
   *
   * @param text       an available text
   * @param outputType output type
   */
  protected abstract void onTextAvailable(final String text, final Key outputType);

  /**
   * Cancel activity
   */
  public synchronized void cancel() {
    checkStarted();
    if (!myIsCancellable) {
      throw new IllegalStateException("The process is not cancellable.");
    }
    try {
      myHandler.destroyProcess();
    }
    catch (Exception e) {
      log.warn("Exception during cancel", e);
    }
  }

  /**
   * @return exit code for process if it is available
   */
  public synchronized int getExitCode() {
    if (myExitCode == null) {
      throw new IllegalStateException("Exit code is not yet available");
    }
    return myExitCode.intValue();
  }

  /**
   * @param exitCode a exit code for process
   */
  private synchronized void setExitCode(int exitCode) {
    myExitCode = exitCode;
  }

  /**
   * Cleanup environment
   */
  private synchronized void cleanupEnv() {
    if (!myEnvironmentCleanedUp) {
      GitSSHService ssh = GitSSHService.getInstance();
      myEnvironmentCleanedUp = true;
      ssh.unregisterHander(myHandlerNo);
    }
  }

  /**
   * Wait for process termination
   */
  public void waitFor() {
    checkStarted();
    myHandler.waitFor();
  }

}
