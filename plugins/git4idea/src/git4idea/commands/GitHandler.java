// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PotemkinProgress;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProcessEventListener;
import com.intellij.openapi.vcs.VcsEnvCustomizer;
import com.intellij.openapi.vcs.VcsEnvCustomizer.VcsExecutableContext;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ThrowableConsumer;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitVcs;
import git4idea.config.GitExecutable;
import git4idea.config.GitExecutableContext;
import git4idea.config.GitExecutableManager;
import git4idea.config.GitVersionSpecialty;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A handler for git commands
 */
public abstract class GitHandler {

  protected static final Logger LOG = Logger.getInstance(GitHandler.class);
  protected static final Logger OUTPUT_LOG = Logger.getInstance("#output." + GitHandler.class.getName());
  protected static final Logger TIME_LOG = Logger.getInstance("#time." + GitHandler.class.getName());
  protected static final Logger CALL_TRACE_LOG = Logger.getInstance("#call_trace." + GitHandler.class.getName());

  private final Project myProject;
  protected final @NotNull GitExecutable myExecutable;
  private final GitCommand myCommand;

  private boolean myPreValidateExecutable = true;
  private boolean myEnableInteractiveCallbacks = true;

  protected final GeneralCommandLine myCommandLine;
  private final Map<String, String> myCustomEnv = new HashMap<>();
  protected Process myProcess;

  private boolean myStdoutSuppressed; // If true, the standard output is not copied to version control console
  private boolean myStderrSuppressed; // If true, the standard error is not copied to version control console

  private @Nullable ThrowableConsumer<? super OutputStream, IOException> myInputProcessor; // The processor for stdin

  private final EventDispatcher<ProcessEventListener> myListeners = EventDispatcher.create(ProcessEventListener.class);
  protected boolean mySilent; // if true, the command execution is not logged in version control view

  private final GitExecutableContext myExecutableContext;

  private long myStartTime; // git execution start timestamp
  private static final long LONG_TIME = 10 * 1000;

  /**
   * A constructor
   *
   * @param project   a project
   * @param directory a process directory
   * @param command   a command to execute
   */
  protected GitHandler(@Nullable Project project,
                       @NotNull File directory,
                       @NotNull GitCommand command,
                       @NotNull List<String> configParameters) {
    this(project,
         directory,
         GitExecutableManager.getInstance().getExecutable(project),
         command,
         configParameters);
  }

  /**
   * A constructor
   *
   * @param project a project
   * @param vcsRoot a process directory
   * @param command a command to execute
   */
  protected GitHandler(@Nullable Project project,
                       @NotNull VirtualFile vcsRoot,
                       @NotNull GitCommand command,
                       @NotNull List<String> configParameters) {
    this(project, VfsUtilCore.virtualToIoFile(vcsRoot), command, configParameters);
  }

  /**
   * A constructor for handler that can be run without project association.
   *
   * @param project          optional project
   * @param directory        working directory
   * @param executable       git executable
   * @param command          git command to execute
   * @param configParameters list of config parameters to use for this git execution
   */
  protected GitHandler(@Nullable Project project,
                       @NotNull File directory,
                       @NotNull GitExecutable executable,
                       @NotNull GitCommand command,
                       @NotNull List<String> configParameters) {
    myProject = project;
    myExecutable = executable;
    myCommand = command;

    myCommandLine = new GeneralCommandLine()
      .withWorkDirectory(directory)
      .withExePath(executable.getExePath())
      .withCharset(StandardCharsets.UTF_8);

    for (String parameter : getConfigParameters(project, configParameters)) {
      myCommandLine.addParameters("-c", parameter);
    }
    myCommandLine.addParameter(command.name());

    myStdoutSuppressed = true;
    mySilent = myCommand.lockingPolicy() != GitCommand.LockingPolicy.WRITE;

    GitVcs gitVcs = myProject != null ? GitVcs.getInstance(myProject) : null;
    VirtualFile root = VfsUtil.findFileByIoFile(directory, true);
    VcsEnvCustomizer.ExecutableType executableType = myExecutable instanceof GitExecutable.Wsl
                                                     ? VcsEnvCustomizer.ExecutableType.WSL
                                                     : VcsEnvCustomizer.ExecutableType.LOCAL;
    myExecutableContext = new GitExecutableContext(gitVcs, root, executableType);
  }

  private static @NotNull List<@NonNls String> getConfigParameters(@Nullable Project project,
                                                                   @NotNull List<@NonNls String> requestedConfigParameters) {
    if (project == null || !GitVersionSpecialty.CAN_OVERRIDE_GIT_CONFIG_FOR_COMMAND.existsIn(project)) {
      return Collections.emptyList();
    }

    List<@NonNls String> toPass = new ArrayList<>();
    toPass.add("core.quotepath=false");
    toPass.add("log.showSignature=false");
    toPass.addAll(requestedConfigParameters);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      toPass.add("protocol.file.allow=always");
    }

    return toPass;
  }

  protected @NotNull ProcessEventListener listeners() {
    return myListeners.getMulticaster();
  }

  public @Nullable Project project() {
    return myProject;
  }

  @NotNull
  File getWorkingDirectory() {
    return myCommandLine.getWorkDirectory();
  }

  public VcsExecutableContext getExecutableContext() {
    return myExecutableContext;
  }

  public @NotNull GitExecutable getExecutable() {
    return myExecutable;
  }

  @NotNull
  GitCommand getCommand() {
    return myCommand;
  }

  protected void addListener(@NotNull ProcessEventListener listener) {
    myListeners.addListener(listener);
  }

  /**
   * Execute process with lower priority
   */
  public void withLowPriority() {
    myExecutableContext.withLowPriority(true);
  }

  /**
   * Detach git process from IDE TTY session
   */
  public void withNoTty() {
    myExecutableContext.withNoTty(true);
  }

  public void addParameters(@NonNls String @NotNull ... parameters) {
    addParameters(Arrays.asList(parameters));
  }

  public void addParameters(@NotNull List<@NonNls String> parameters) {
    for (String parameter : parameters) {
      myCommandLine.addParameter(escapeParameterIfNeeded(parameter));
    }
  }

  private @NotNull String escapeParameterIfNeeded(@NotNull @NonNls String parameter) {
    if (escapeNeeded(parameter)) {
      return parameter.replaceAll("\\^", "^^^^");
    }
    return parameter;
  }

  private boolean escapeNeeded(@NotNull @NonNls String parameter) {
    return SystemInfo.isWindows && isCmd() && parameter.contains("^");
  }

  private boolean isCmd() {
    return StringUtil.toLowerCase(myCommandLine.getExePath()).endsWith("cmd"); //NON-NLS
  }

  public void addRelativePaths(FilePath @NotNull ... parameters) {
    addRelativePaths(Arrays.asList(parameters));
  }

  public void addRelativePaths(@NotNull Collection<? extends FilePath> filePaths) {
    for (FilePath path : filePaths) {
      myCommandLine.addParameter(VcsFileUtil.relativePath(getWorkingDirectory(), path));
    }
  }

  public void addRelativeFiles(final @NotNull Collection<? extends VirtualFile> files) {
    for (VirtualFile file : files) {
      myCommandLine.addParameter(VcsFileUtil.relativePath(getWorkingDirectory(), file));
    }
  }

  public void addAbsoluteFile(@NotNull File file) {
    myCommandLine.addParameter(myExecutable.convertFilePath(file));
  }

  /**
   * End option parameters and start file paths. The method adds {@code "--"} parameter.
   */
  public void endOptions() {
    myCommandLine.addParameter("--");
  }

  private boolean isStarted() {
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
  public @NlsSafe String printableCommandLine() {
    if (getExecutable().isLocal()) {
      return unescapeCommandLine(myCommandLine.getCommandLineString("git")); //NON-NLS
    }
    else {
      return unescapeCommandLine(myCommandLine.getCommandLineString(null));
    }
  }

  private @NotNull String unescapeCommandLine(@NotNull String commandLine) {
    if (escapeNeeded(commandLine)) {
      return commandLine.replaceAll("\\^\\^\\^\\^", "^");
    }
    return commandLine;
  }

  public @NotNull Charset getCharset() {
    return myCommandLine.getCharset();
  }

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
  public void setSilent(boolean silent) {
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
  public void setStderrSuppressed(boolean stderrSuppressed) {
    myStderrSuppressed = stderrSuppressed;
  }

  /**
   * Set processor for standard input. This is a place where input to the git application could be generated.
   *
   * @param inputProcessor the processor
   */
  public void setInputProcessor(@Nullable ThrowableConsumer<? super OutputStream, IOException> inputProcessor) {
    myInputProcessor = inputProcessor;
  }

  /**
   * Add environment variable to this handler
   *
   * @param name  the variable name
   * @param value the variable value
   */
  public void addCustomEnvironmentVariable(@NotNull @NonNls String name, @Nullable @NonNls String value) {
    myCustomEnv.put(name, value);
  }

  public boolean containsCustomEnvironmentVariable(@NotNull @NonNls String key) {
    return myCustomEnv.containsKey(key);
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

  /**
   * See {@link GitImplBase#run(Computable, Computable)}
   */
  public boolean isEnableInteractiveCallbacks() {
    return myEnableInteractiveCallbacks;
  }

  /**
   * See {@link GitImplBase#run(Computable, Computable)}
   */
  public void setEnableInteractiveCallbacks(boolean enableInteractiveCallbacks) {
    myEnableInteractiveCallbacks = enableInteractiveCallbacks;
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
        LOG.info(formatDurationMessage(time));
      }
      else {
        TIME_LOG.debug(formatDurationMessage(time));
      }
    }
    else {
      LOG.debug(String.format("git %s finished.", myCommand));
    }
  }

  private @NotNull String formatDurationMessage(long time) {
    return String.format("git %s took %s ms. Command parameters: %n%s", myCommand, time, myCommandLine.getCommandLineString());
  }

  private void start() {
    if (myProject != null && !myProject.isDefault() && !TrustedProjects.isTrusted(myProject)) {
      throw new IllegalStateException("Shouldn't be possible to run a Git command in the safe mode");
    }

    if (isStarted()) {
      throw new IllegalStateException("The process has been already started");
    }

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
      if (CALL_TRACE_LOG.isDebugEnabled()) {
        CALL_TRACE_LOG.debug(new Throwable("[" + logDirectoryPath + "] " + printableCommandLine()));
      }

      prepareEnvironment();
      myExecutable.patchCommandLine(this, myCommandLine, myExecutableContext);

      OUTPUT_LOG.debug(String.format("%s %% %s started: %s", getCommand(), this.hashCode(), myCommandLine));

      // start process
      myProcess = startProcess();
      startHandlingStreams();
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Throwable t) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.warn(t); // will surely happen if called during unit test disposal, because the working dir is simply removed then
      }
      myListeners.getMulticaster().startFailed(t);
    }
  }

  private void prepareEnvironment() {
    Map<String, String> executionEnvironment = myCommandLine.getEnvironment();
    executionEnvironment.clear();
    if (myExecutable.isLocal()) {
      executionEnvironment.putAll(EnvironmentUtil.getEnvironmentMap());
    }
    executionEnvironment.putAll(myExecutable.getLocaleEnv());
    executionEnvironment.putAll(myCustomEnv);
    executionEnvironment.put(GitCommand.IJ_HANDLER_MARKER_ENV, "true");

    if (!shouldSuppressReadLocks() && Registry.is("git.use.env.from.project.context")) {
      VcsEnvCustomizer.EP_NAME.forEachExtensionSafe(customizer -> {
        customizer.customizeCommandAndEnvironment(myProject, executionEnvironment, myExecutableContext);
      });

      executionEnvironment.remove("PS1"); // ensure we won't get detected as interactive shell because of faulty customizer
    }
  }

  /**
   * Tasks executed under {@link PotemkinProgress#runInBackground} cannot take read lock.
   */
  protected static boolean shouldSuppressReadLocks() {
    if (ProgressManager.getInstance().getProgressIndicator() instanceof PotemkinProgress) {
      return !ApplicationManager.getApplication().isDispatchThread();
    }
    return false;
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
  @Deprecated
  private Integer myExitCode; // exit code or null if exit code is not yet available
  @Deprecated
  private static final int LAST_OUTPUT_SIZE = 5;
  @Deprecated
  private final List<VcsException> myErrors = Collections.synchronizedList(new ArrayList<>());

  /**
   * @return exit code for process if it is available
   * @deprecated use {@link GitLineHandler}, {@link Git#runCommand(GitLineHandler)} and {@link GitCommandResult}
   */
  @Deprecated(forRemoval = true)
  public int getExitCode() {
    if (myExitCode == null) {
      return -1;
    }
    return myExitCode.intValue();
  }

  /**
   * @param exitCode a exit code for process
   * @deprecated use {@link GitLineHandler}, {@link Git#runCommand(GitLineHandler)} and {@link GitCommandResult}
   */
  @Deprecated
  protected void setExitCode(int exitCode) {
    if (myExitCode == null) {
      myExitCode = exitCode;
    }
    else {
      LOG.info("Not setting exit code " + exitCode + ", because it was already set to " + myExitCode);
    }
  }

  /**
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
  //endregion
}
