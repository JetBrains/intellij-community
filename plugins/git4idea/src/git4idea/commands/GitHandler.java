// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProcessEventListener;
import com.intellij.openapi.vcs.RemoteFilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ThrowableConsumer;
import com.intellij.vcs.VcsLocaleHelper;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitVcs;
import git4idea.config.GitExecutableManager;
import git4idea.config.GitVersionSpecialty;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.*;

/**
 * A handler for git commands
 */
public abstract class GitHandler {

  protected static final Logger LOG = Logger.getInstance(GitHandler.class);
  protected static final Logger OUTPUT_LOG = Logger.getInstance("#output." + GitHandler.class.getName());
  private static final Logger TIME_LOG = Logger.getInstance("#time." + GitHandler.class.getName());

  private final Project myProject;
  @NotNull private final String myPathToExecutable;
  private final GitCommand myCommand;

  private boolean myPreValidateExecutable = true;

  protected final GeneralCommandLine myCommandLine;
  private final Map<String, String> myCustomEnv = new HashMap<>();
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  protected Process myProcess;

  private boolean myStdoutSuppressed; // If true, the standard output is not copied to version control console
  private boolean myStderrSuppressed; // If true, the standard error is not copied to version control console

  @Nullable private ThrowableConsumer<OutputStream, IOException> myInputProcessor; // The processor for stdin

  private final EventDispatcher<ProcessEventListener> myListeners = EventDispatcher.create(ProcessEventListener.class);
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  protected boolean mySilent; // if true, the command execution is not logged in version control view

  private long myStartTime; // git execution start timestamp
  private static final long LONG_TIME = 10 * 1000;

  /**
   * A constructor
   *
   * @param project   a project
   * @param directory a process directory
   * @param command   a command to execute
   */
  protected GitHandler(@NotNull Project project,
                       @NotNull File directory,
                       @NotNull GitCommand command,
                       @NotNull List<String> configParameters) {
    this(project,
         directory,
         GitExecutableManager.getInstance().getPathToGit(project),
         command,
         configParameters);
    myProgressParameterAllowed =
      GitVersionSpecialty.ABLE_TO_USE_PROGRESS_IN_REMOTE_COMMANDS.existsIn(project);
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
   * A constructor for handler that can be run without project association
   *
   * @param project          optional project
   * @param directory        working directory
   * @param pathToExecutable path to git executable
   * @param command          git command to execute
   * @param configParameters list of config parameters to use for this git execution
   */
  protected GitHandler(@Nullable Project project,
                       @NotNull File directory,
                       @NotNull String pathToExecutable,
                       @NotNull GitCommand command,
                       @NotNull List<String> configParameters) {
    myProject = project;
    myVcs = project != null ? GitVcs.getInstance(project) : null;
    myPathToExecutable = pathToExecutable;
    myCommand = command;

    myCommandLine = new GeneralCommandLine()
      .withWorkDirectory(directory)
      .withExePath(myPathToExecutable)
      .withCharset(CharsetToolkit.UTF8_CHARSET);

    for (String parameter : getConfigParameters(project, configParameters)) {
      myCommandLine.addParameters("-c", parameter);
    }
    myCommandLine.addParameter(command.name());

    myStdoutSuppressed = true;
    mySilent = myCommand.lockingPolicy() == GitCommand.LockingPolicy.READ;
  }

  @NotNull
  static List<String> getConfigParameters(@Nullable Project project, @NotNull List<String> requestedConfigParameters) {
    if (project == null || !GitVersionSpecialty.CAN_OVERRIDE_GIT_CONFIG_FOR_COMMAND.existsIn(project)) {
      return Collections.emptyList();
    }

    List<String> toPass = new ArrayList<>();
    toPass.add("core.quotepath=false");
    toPass.add("log.showSignature=false");
    toPass.addAll(requestedConfigParameters);
    return toPass;
  }

  /**
   * @return multicaster for listeners
   */
  protected ProcessEventListener listeners() {
    return myListeners.getMulticaster();
  }

  /**
   * @return a context project
   */
  @Nullable
  public Project project() {
    return myProject;
  }

  /**
   * @return the current working directory
   */
  @NotNull
  File getWorkingDirectory() {
    return myCommandLine.getWorkDirectory();
  }

  @NotNull
  String getExecutablePath() {
    return myPathToExecutable;
  }

  @NotNull
  GitCommand getCommand() {
    return myCommand;
  }

  /**
   * Add listener to handler
   *
   * @param listener a listener
   */
  protected void addListener(ProcessEventListener listener) {
    myListeners.addListener(listener);
  }

  /***
   * Execute process with lower priority
   *
   * @param isLowPriority whether to use lower or normal priority
   */
  public void setWithLowPriority(boolean isLowPriority) {
    if (isLowPriority) {
      ExecUtil.setupLowPriorityExecution(myCommandLine);
    }
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
      if (path instanceof RemoteFilePath) {
        myCommandLine.addParameter(path.getPath());
      }
      else {
        myCommandLine.addParameter(VcsFileUtil.relativePath(getWorkingDirectory(), path));
      }
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
   * End option parameters and start file paths. The method adds {@code "--"} parameter.
   */
  public void endOptions() {
    myCommandLine.addParameter("--");
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
   * @return true if process is started
   */
  final synchronized boolean isStarted() {
    return myProcess != null;
  }

  /**
   * @return true if the command line is too big
   */
  public boolean isLargeCommandLine() {
    return myCommandLine.getCommandLineString().length() > VcsFileUtil.FILE_PATH_LIMIT;
  }

  /**
   * @return a command line with full path to executable replace to "git"
   */
  public String printableCommandLine() {
    return unescapeCommandLine(myCommandLine.getCommandLineString("git"));
  }

  @NotNull
  private String unescapeCommandLine(@NotNull String commandLine) {
    if (escapeNeeded(commandLine)) {
      return commandLine.replaceAll("\\^\\^\\^\\^", "^");
    }
    return commandLine;
  }

  /**
   * @return a character set to use for IO
   */
  @NotNull
  public Charset getCharset() {
    return myCommandLine.getCharset();
  }

  /**
   * Set character set for IO
   *
   * @param charset a character set
   */
  @SuppressWarnings({"SameParameterValue"})
  public void setCharset(@NotNull Charset charset) {
    myCommandLine.setCharset(charset);
  }

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
   * Set processor for standard input. This is a place where input to the git application could be generated.
   *
   * @param inputProcessor the processor
   */
  public void setInputProcessor(@Nullable ThrowableConsumer<OutputStream, IOException> inputProcessor) {
    myInputProcessor = inputProcessor;
  }

  /**
   * Add environment variable to this handler
   *
   * @param name  the variable name
   * @param value the variable value
   */
  public void addCustomEnvironmentVariable(String name, String value) {
    myCustomEnv.put(name, value);
  }

  /**
   * See {@link GitImplBase#run(Computable, Computable)}
   */
  public void setPreValidateExecutable(boolean preValidateExecutable) {
    myPreValidateExecutable = preValidateExecutable;
  }

  /**
   * See {@link GitImplBase#run(Computable, Computable)}
   */
  boolean isPreValidateExecutable() {
    return myPreValidateExecutable;
  }

  void runInCurrentThread() throws IOException {
    try {
      start();
      if (isStarted()) {
        try {
          if (myInputProcessor != null) {
            myInputProcessor.consume(myProcess.getOutputStream());
          }
        }
        finally {
          waitForProcess();
        }
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

  /**
   * Start process
   */
  synchronized void start() {
    checkNotStarted();

    try {
      myStartTime = System.currentTimeMillis();
      String logDirectoryPath = myProject != null
                                ? GitImplBase.stringifyWorkingDir(myProject.getBasePath(), myCommandLine.getWorkDirectory())
                                : myCommandLine.getWorkDirectory().getPath();
      if (!mySilent) {
        LOG.info("[" + logDirectoryPath + "] " + printableCommandLine());
      }
      else {
        LOG.debug("[" + logDirectoryPath + "] " + printableCommandLine());
      }

      prepareEnvironment();
      // start process
      myProcess = startProcess();
      startHandlingStreams();
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Throwable t) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error(t); // will surely happen if called during unit test disposal, because the working dir is simply removed then
      }
      myListeners.getMulticaster().startFailed(t);
    }
  }

  private void prepareEnvironment() {
    Map<String, String> executionEnvironment = myCommandLine.getEnvironment();
    executionEnvironment.clear();
    executionEnvironment.putAll(EnvironmentUtil.getEnvironmentMap());
    executionEnvironment.putAll(VcsLocaleHelper.getDefaultLocaleEnvironmentVars("git"));
    executionEnvironment.putAll(myCustomEnv);
  }

  protected abstract Process startProcess() throws ExecutionException;

  /**
   * Start handling process output streams for the handler.
   */
  protected abstract void startHandlingStreams();

  /**
   * Wait for process
   */
  protected abstract void waitForProcess();

  @Override
  public String toString() {
    return myCommandLine.toString();
  }

  //region deprecated stuff
  //Used by Gitflow in GitInitLineHandler.onTextAvailable
  @Deprecated
  protected final GitVcs myVcs;
  @Deprecated
  private Integer myExitCode; // exit code or null if exit code is not yet available
  @Deprecated
  private final List<String> myLastOutput = Collections.synchronizedList(new ArrayList<String>());
  @Deprecated
  private final int LAST_OUTPUT_SIZE = 5;
  @Deprecated
  private boolean myProgressParameterAllowed = false;
  @Deprecated
  private final List<VcsException> myErrors = Collections.synchronizedList(new ArrayList<VcsException>());

  /**
   * Adds "--progress" parameter. Usable for long operations, such as clone or fetch.
   *
   * @return is "--progress" parameter supported by this version of Git.
   * @deprecated use {@link #addParameters}
   */
  @Deprecated
  public boolean addProgressParameter() {
    if (myProgressParameterAllowed) {
      addParameters("--progress");
      return true;
    }
    return false;
  }

  /**
   * @return exit code for process if it is available
   * @deprecated use {@link GitLineHandler}, {@link Git#runCommand(GitLineHandler)} and {@link GitCommandResult}
   */
  @Deprecated
  public synchronized int getExitCode() {
    if (myExitCode == null) {
      throw new IllegalStateException("Exit code is not yet available");
    }
    return myExitCode.intValue();
  }

  /**
   * @param exitCode a exit code for process
   * @deprecated use {@link GitLineHandler}, {@link Git#runCommand(GitLineHandler)} and {@link GitCommandResult}
   */
  @Deprecated
  protected synchronized void setExitCode(int exitCode) {
    if (myExitCode == null) {
      myExitCode = exitCode;
    }
    else {
      LOG.info("Not setting exit code " + exitCode + ", because it was already set to " + myExitCode);
    }
  }

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
   * @param postStartAction
   * @deprecated remove together with {@link GitHandlerUtil}
   */
  @Deprecated
  void runInCurrentThread(@Nullable Runnable postStartAction) {
    try {
      start();
      if (isStarted()) {
        if (postStartAction != null) {
          postStartAction.run();
        }
        waitForProcess();
      }
    }
    finally {
      logTime();
    }
  }

  /**
   * Destroy process
   *
   * @deprecated only used in {@link GitTask}
   */
  @Deprecated
  abstract void destroyProcess();

  /**
   * add error to the error list
   *
   * @param ex an error to add to the list
   * @deprecated remove together with {@link GitHandlerUtil} and {@link GitTask}
   */
  @Deprecated
  public void addError(VcsException ex) {
    myErrors.add(ex);
  }

  /**
   * @return unmodifiable list of errors.
   * @deprecated remove together with {@link GitHandlerUtil} and {@link GitTask}
   */
  @Deprecated
  public List<VcsException> errors() {
    return Collections.unmodifiableList(myErrors);
  }
  //endregion
}
