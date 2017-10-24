/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProcessEventListener;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ThrowableConsumer;
import com.intellij.vcs.VcsLocaleHelper;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitVcs;
import git4idea.config.GitExecutableManager;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVersionSpecialty;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.*;

import static java.util.Collections.singletonList;

/**
 * A handler for git commands
 */
public abstract class GitHandler {

  protected static final Logger LOG = Logger.getInstance(GitHandler.class);
  protected static final Logger OUTPUT_LOG = Logger.getInstance("#output." + GitHandler.class.getName());
  private static final Logger TIME_LOG = Logger.getInstance("#time." + GitHandler.class.getName());

  protected final Project myProject;
  protected final GitCommand myCommand;

  private final HashSet<Integer> myIgnoredErrorCodes = new HashSet<>(); // Error codes that are ignored for the handler
  private final List<VcsException> myErrors = Collections.synchronizedList(new ArrayList<VcsException>());
  private final List<String> myLastOutput = Collections.synchronizedList(new ArrayList<String>());
  private final int LAST_OUTPUT_SIZE = 5;
  protected final GeneralCommandLine myCommandLine;
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  protected Process myProcess;

  private boolean myStdoutSuppressed; // If true, the standard output is not copied to version control console
  private boolean myStderrSuppressed; // If true, the standard error is not copied to version control console

  @Nullable private ThrowableConsumer<OutputStream, IOException> myInputProcessor; // The processor for stdin

  private Integer myExitCode; // exit code or null if exit code is not yet available

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  @NonNls
  @NotNull
  private Charset myCharset = CharsetToolkit.UTF8_CHARSET; // Character set to use for IO

  private final EventDispatcher<ProcessEventListener> myListeners = EventDispatcher.create(ProcessEventListener.class);
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  protected boolean mySilent; // if true, the command execution is not logged in version control view

  protected final GitVcs myVcs;
  private final Map<String, String> myEnv;
  private GitVcsApplicationSettings myAppSettings;

  private long myStartTime; // git execution start timestamp
  private static final long LONG_TIME = 10 * 1000;
  @Nullable private Collection<String> myUrls;

  /**
   * A constructor
   *
   * @param project   a project
   * @param directory a process directory
   * @param command   a command to execute (if empty string, the parameter is ignored)
   */
  protected GitHandler(@NotNull Project project,
                       @NotNull File directory,
                       @NotNull GitCommand command,
                       @NotNull List<String> configParameters) {
    myProject = project;
    myCommand = command;
    myAppSettings = GitVcsApplicationSettings.getInstance();
    myEnv = new HashMap<>(EnvironmentUtil.getEnvironmentMap());
    myVcs = GitVcs.getInstance(project);
    myCommandLine = new GeneralCommandLine().withExePath(GitExecutableManager.getInstance().getPathToGit(project));
    myCommandLine.setWorkDirectory(directory);
    if (GitVersionSpecialty.CAN_OVERRIDE_GIT_CONFIG_FOR_COMMAND.existsIn(myVcs.getVersion())) {
      myCommandLine.addParameters("-c", "core.quotepath=false");
      myCommandLine.addParameters("-c", "log.showSignature=false");
      for (String configParameter : configParameters) {
        myCommandLine.addParameters("-c", configParameter);
      }
    }
    myCommandLine.addParameter(command.name());
    myStdoutSuppressed = true;
    mySilent = myCommand.lockingPolicy() == GitCommand.LockingPolicy.READ;
  }

  /**
   * A constructor
   *
   * @param project a project
   * @param vcsRoot a process directory
   * @param command a command to execute
   */
  protected GitHandler(@NotNull Project project,
                       @NotNull VirtualFile vcsRoot,
                       @NotNull GitCommand command,
                       @NotNull List<String> configParameters) {
    this(project, VfsUtil.virtualToIoFile(vcsRoot), command, configParameters);
  }

  /**
   * @return multicaster for listeners
   */
  protected ProcessEventListener listeners() {
    return myListeners.getMulticaster();
  }

  /**
   * Add error code to ignored list
   *
   * @param code the code to ignore
   */
  public void ignoreErrorCode(int code) {
    myIgnoredErrorCodes.add(code);
  }

  /**
   * Check if error code should be ignored
   *
   * @param code a code to check
   * @return true if error code is ignorable
   */
  public boolean isIgnoredErrorCode(int code) {
    return myIgnoredErrorCodes.contains(code);
  }


  /**
   * add error to the error list
   *
   * @param ex an error to add to the list
   */
  public void addError(VcsException ex) {
    myErrors.add(ex);
  }

  /**
   * @return unmodifiable list of errors.
   */
  public List<VcsException> errors() {
    return Collections.unmodifiableList(myErrors);
  }

  /**
   * @return a context project
   */
  public Project project() {
    return myProject;
  }

  /**
   * @return the current working directory
   */
  public File getWorkingDirectory() {
    return myCommandLine.getWorkDirectory();
  }

  /**
   * @return the current working directory
   */
  public VirtualFile getWorkingDirectoryFile() {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(getWorkingDirectory());
    if (file == null) {
      throw new IllegalStateException("The working directly should be available: " + getWorkingDirectory());
    }
    return file;
  }

  @NotNull
  GitCommand getCommand() {
    return myCommand;
  }

  public void setUrl(@NotNull String url) {
    setUrls(singletonList(url));
  }

  public void setUrls(@NotNull Collection<String> urls) {
    myUrls = urls;
  }

  @Nullable
  public Collection<String> getUrls() {
    return myUrls;
  }

  protected boolean isRemote() {
    return myUrls != null;
  }

  /**
   * Add listener to handler
   *
   * @param listener a listener
   */
  protected void addListener(ProcessEventListener listener) {
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
  @SuppressWarnings({"WeakerAccess"})
  public void addParameters(@NonNls @NotNull String... parameters) {
    addParameters(Arrays.asList(parameters));
  }

  /**
   * Add parameters from the list
   *
   * @param parameters the parameters to add
   */
  public void addParameters(List<String> parameters) {
    checkNotStarted();
    for (String parameter : parameters) {
      myCommandLine.addParameter(escapeParameterIfNeeded(parameter));
    }
  }

  @NotNull
  private String escapeParameterIfNeeded(@NotNull String parameter) {
    if (escapeNeeded(parameter)) {
      return parameter.replaceAll("\\^", "^^^^");
    }
    return parameter;
  }

  private boolean escapeNeeded(@NotNull String parameter) {
    return SystemInfo.isWindows && isCmd() && parameter.contains("^");
  }

  private boolean isCmd() {
    return myCommandLine.getExePath().toLowerCase().endsWith("cmd");
  }

  @NotNull
  private String unescapeCommandLine(@NotNull String commandLine) {
    if (escapeNeeded(commandLine)) {
      return commandLine.replaceAll("\\^\\^\\^\\^", "^");
    }
    return commandLine;
  }

  /**
   * Add file path parameters. The parameters are made relative to the working directory
   *
   * @param parameters a parameters to add
   * @throws IllegalArgumentException if some path is not under root.
   */
  public void addRelativePaths(@NotNull FilePath... parameters) {
    addRelativePaths(Arrays.asList(parameters));
  }

  /**
   * Add file path parameters. The parameters are made relative to the working directory
   *
   * @param filePaths a parameters to add
   * @throws IllegalArgumentException if some path is not under root.
   */
  @SuppressWarnings({"WeakerAccess"})
  public void addRelativePaths(@NotNull final Collection<FilePath> filePaths) {
    checkNotStarted();
    for (FilePath path : filePaths) {
      myCommandLine.addParameter(VcsFileUtil.relativePath(getWorkingDirectory(), path));
    }
  }

  /**
   * Add virtual file parameters. The parameters are made relative to the working directory
   *
   * @param files a parameters to add
   * @throws IllegalArgumentException if some path is not under root.
   */
  @SuppressWarnings({"WeakerAccess"})
  public void addRelativeFiles(@NotNull final Collection<VirtualFile> files) {
    checkNotStarted();
    for (VirtualFile file : files) {
      myCommandLine.addParameter(VcsFileUtil.relativePath(getWorkingDirectory(), file));
    }
  }

  /**
   * Adds "--progress" parameter. Usable for long operations, such as clone or fetch.
   *
   * @return is "--progress" parameter supported by this version of Git.
   */
  public boolean addProgressParameter() {
    if (GitVersionSpecialty.ABLE_TO_USE_PROGRESS_IN_REMOTE_COMMANDS.existsIn(myVcs.getVersion())) {
      addParameters("--progress");
      return true;
    }
    return false;
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
  private void checkStarted() {
    if (!isStarted()) {
      throw new IllegalStateException("The process is not started yet");
    }
  }

  /**
   * @return true if process is started
   */
  final synchronized boolean isStarted() {
    return myProcess != null;
  }

  /**
   * Start process
   */
  synchronized void start() {
    checkNotStarted();

    try {
      myStartTime = System.currentTimeMillis();
      if (!mySilent) {
        LOG.info("[" + myCommandLine.getWorkDirectory().getPath() + "] " + printableCommandLine());
      }
      else {
        LOG.debug("[" + myCommandLine.getWorkDirectory().getPath() + "] " + printableCommandLine());
      }

      setUpLocale();
      unsetGitTrace();
      myCommandLine.getEnvironment().clear();
      myCommandLine.getEnvironment().putAll(myEnv);
      // start process
      myProcess = startProcess();
      startHandlingStreams();
    }
    catch (ProcessCanceledException ignored) {
    }
    catch (Throwable t) {
      if (!ApplicationManager.getApplication().isUnitTestMode() || !myProject.isDisposed()) {
        LOG.error(t); // will surely happen if called during unit test disposal, because the working dir is simply removed then
      }
      myListeners.getMulticaster().startFailed(t);
    }
  }

  private void setUpLocale() {
    myEnv.putAll(VcsLocaleHelper.getDefaultLocaleEnvironmentVars("git"));
  }

  private void unsetGitTrace() {
    myEnv.putAll(getCommonEnvironment());
  }

  protected abstract Process startProcess() throws ExecutionException;

  /**
   * Start handling process output streams for the handler.
   */
  protected abstract void startHandlingStreams();

  /**
   * @return a command line with full path to executable replace to "git"
   */
  public String printableCommandLine() {
    return unescapeCommandLine(myCommandLine.getCommandLineString("git"));
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
  protected synchronized void setExitCode(int exitCode) {
    if (myExitCode == null) {
      myExitCode = exitCode;
    }
    else {
      LOG.info("Not setting exit code " + exitCode + ", because it was already set to " + myExitCode);
    }
  }

  /**
   * Wait for process termination
   */
  void waitFor() {
    checkStarted();
    try {
      if (myInputProcessor != null && myProcess != null) {
        myInputProcessor.consume(myProcess.getOutputStream());
      }
    }
    catch (IOException e) {
      addError(new VcsException(e));
    }
    finally {
      waitForProcess();
    }
  }

  /**
   * Wait for process
   */
  protected abstract void waitForProcess();

  /**
   * Set silent mode. When handler is silent, it does not logs command in version control console.
   * Note that this option also suppresses stderr and stdout copying.
   *
   * @param silent a new value of the flag
   * @see #setStderrSuppressed(boolean)
   * @see #setStdoutSuppressed(boolean)
   */
  @SuppressWarnings({"SameParameterValue"})
  public void setSilent(final boolean silent) {
    checkNotStarted();
    mySilent = silent;
    if (silent) {
      setStderrSuppressed(true);
      setStdoutSuppressed(true);
    }
  }

  boolean isSilent() {
    return mySilent;
  }

  /**
   * @return a character set to use for IO
   */
  @NotNull
  public Charset getCharset() {
    return myCharset;
  }

  /**
   * Set character set for IO
   *
   * @param charset a character set
   */
  @SuppressWarnings({"SameParameterValue"})
  public void setCharset(@NotNull Charset charset) {
    myCharset = charset;
  }

  /**
   * @return true if standard output is not copied to the console
   */
  boolean isStdoutSuppressed() {
    return myStdoutSuppressed;
  }

  /**
   * Set flag specifying if stdout should be copied to the console
   *
   * @param stdoutSuppressed true if output is not copied to the console
   */
  public void setStdoutSuppressed(final boolean stdoutSuppressed) {
    checkNotStarted();
    myStdoutSuppressed = stdoutSuppressed;
  }

  /**
   * @return true if standard output is not copied to the console
   */
  boolean isStderrSuppressed() {
    return myStderrSuppressed;
  }

  /**
   * Set flag specifying if stderr should be copied to the console
   *
   * @param stderrSuppressed true if error output is not copied to the console
   */
  public void setStderrSuppressed(final boolean stderrSuppressed) {
    checkNotStarted();
    myStderrSuppressed = stderrSuppressed;
  }

  /**
   * Set environment variable
   *
   * @param name  the variable name
   * @param value the variable value
   */
  public void setEnvironment(String name, String value) {
    myEnv.put(name, value);
  }

  /**
   * @return true if the command line is too big
   */
  public boolean isLargeCommandLine() {
    return myCommandLine.getCommandLineString().length() > VcsFileUtil.FILE_PATH_LIMIT;
  }

  void runInCurrentThread(@Nullable Runnable postStartAction) {
    try {
      start();
      if (isStarted()) {
        if (postStartAction != null) {
          postStartAction.run();
        }
        waitFor();
      }
    }
    finally {
      logTime();
    }
  }

  private void logTime() {
    if (myStartTime > 0) {
      long time = System.currentTimeMillis() - myStartTime;
      if (!TIME_LOG.isDebugEnabled() && time > LONG_TIME) {
        LOG.info(String.format("git %s took %s ms. Command parameters: %n%s", myCommand, time, myCommandLine.getCommandLineString()));
      }
      else {
        TIME_LOG.debug(String.format("git %s took %s ms", myCommand, time));
      }
    }
    else {
      LOG.debug(String.format("git %s finished.", myCommand));
    }
  }

  @Override
  public String toString() {
    return myCommandLine.toString();
  }

  /**
   * Set processor for standard input. This is a place where input to the git application could be generated.
   *
   * @param inputProcessor the processor
   */
  public void setInputProcessor(@Nullable ThrowableConsumer<OutputStream, IOException> inputProcessor) {
    myInputProcessor = inputProcessor;
  }

  @NotNull
  public static Map<String, String> getCommonEnvironment() {
    Map<String, String> commonEnv = new HashMap<>();
    commonEnv.put("GIT_TRACE", "0");
    commonEnv.put("GIT_TRACE_PACK_ACCESS", "");
    commonEnv.put("GIT_TRACE_PACKET", "");
    commonEnv.put("GIT_TRACE_PERFORMANCE", "0");
    commonEnv.put("GIT_TRACE_SETUP", "0");
    return commonEnv;
  }
  //region deprecated stuff
  /**
   * @deprecated only used in {@link GitTask}
   */
  @Deprecated
  public void addLastOutput(String line) {
    if (myLastOutput.size() < LAST_OUTPUT_SIZE) {
      myLastOutput.add(line);
    }
    else {
      myLastOutput.add(0, line);
      Collections.rotate(myLastOutput, -1);
    }
  }

  /**
   * @deprecated only used in {@link GitTask}
   */
  @Deprecated
  public List<String> getLastOutput() {
    return myLastOutput;
  }

  /**
   * Destroy process
   *
   * @deprecated only used in {@link GitTask}
   */
  @Deprecated
  abstract void destroyProcess();
  //endregion
}
