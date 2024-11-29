// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import git4idea.DialogManager;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand.LockingPolicy;
import git4idea.config.*;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitHandlerRebaseEditorManager;
import git4idea.rebase.GitSimpleEditorHandler;
import git4idea.rebase.GitUnstructuredEditor;
import git4idea.util.GitVcsConsoleWriter;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.splitByLinesKeepSeparators;
import static com.intellij.openapi.util.text.StringUtil.trimLeading;
import static git4idea.commands.GitCommand.LockingPolicy.READ;
import static git4idea.commands.GitCommand.LockingPolicy.READ_OPTIONAL_LOCKING;

/**
 * Basic functionality for git handler execution.
 */
public abstract class GitImplBase implements Git {

  private static final Logger LOG = Logger.getInstance(GitImplBase.class);

  @Override
  public @NotNull GitCommandResult runCommand(@NotNull GitLineHandler handler) {
    return run(handler, getCollectingCollector());
  }

  @Override
  public @NotNull GitCommandResult runCommand(@NotNull Computable<? extends GitLineHandler> handlerConstructor) {
    return run(handlerConstructor, GitImplBase::getCollectingCollector);
  }

  private static @NotNull OutputCollector getCollectingCollector() {
    return new OutputCollector() {
      @Override
      public void outputLineReceived(@NotNull String line) {
        addOutputLine(line);
      }

      @Override
      public void errorLineReceived(@NotNull String line) {
        if (Registry.is("git.allow.stderr.to.stdout.mixing") && !looksLikeError(line)) {
          addOutputLine(line);
        }
        else {
          addErrorLine(line);
        }
      }
    };
  }

  @Override
  public @NotNull GitCommandResult runCommandWithoutCollectingOutput(@NotNull GitLineHandler handler) {
    return run(handler, new OutputCollector() {
      @Override
      protected void outputLineReceived(@NotNull String line) { }

      @Override
      protected void errorLineReceived(@NotNull String line) {
        addErrorLine(line);
      }
    });
  }

  /**
   * Run handler with retry on authentication failure
   */
  private static @NotNull GitCommandResult run(@NotNull Computable<? extends GitLineHandler> handlerConstructor,
                                               @NotNull Computable<? extends OutputCollector> outputCollectorConstructor) {
    @NotNull GitCommandResult result;

    int authAttempt = 0;
    do {
      GitLineHandler handler = handlerConstructor.compute();
      OutputCollector outputCollector = outputCollectorConstructor.compute();
      boolean isCredHelperUsed = GitVcsApplicationSettings.getInstance().isUseCredentialHelper();
      result = run(handler, outputCollector);
      if (isCredHelperUsed != GitVcsApplicationSettings.getInstance().isUseCredentialHelper()) {
        // do not spend attempt if the credential helper has been enabled
        continue;
      }
      authAttempt++;
    }
    while (result.isAuthenticationFailed() && authAttempt < 2);
    return result;
  }

  /**
   * Run handler with per-project locking, logging and authentication
   */
  private static @NotNull GitCommandResult run(@NotNull GitLineHandler handler, @NotNull OutputCollector outputCollector) {
    GitVersion version = GitVersion.NULL;
    if (handler.isPreValidateExecutable()) {
      GitExecutable executable = handler.getExecutable();
      try {
        version = GitExecutableManager.getInstance().identifyVersion(executable);

        if (version.getType() == GitVersion.Type.WSL1 &&
            !Registry.is("git.allow.wsl1.executables")) {
          throw new GitNotInstalledException(GitBundle.message("executable.error.git.not.installed"), null);
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        Project project = handler.project();
        if (project != null) {
          GitExecutableProblemsNotifier.getInstance(project).notifyExecutionError(e);
        }
        return GitCommandResult.startError(GitBundle.message("git.executable.validation.error.start.title") + ": \n" +
                                           GitExecutableProblemsNotifier.getPrettyErrorMessage(e));
      }
    }

    Project project = handler.project();
    if (project != null && project.isDisposed()) {
      LOG.warn("Project has already been disposed");
      throw new ProcessCanceledException();
    }

    if (project != null && handler.isEnableInteractiveCallbacks()) {
      try (GitHandlerAuthenticationManager authenticationManager = GitHandlerAuthenticationManager.prepare(project, handler, version)) {
        try (GitHandlerRebaseEditorManager ignored = prepareGeneralPurposeEditor(project, handler)) {
          GitCommandResult result = doRun(handler, version, outputCollector);
          return GitCommandResult.withAuthentication(result, authenticationManager.isHttpAuthFailed());
        }
      }
      catch (IOException e) {
        return GitCommandResult.startError(GitBundle.message("git.executable.unknown.error.message", e.getLocalizedMessage()));
      }
    }
    else {
      return doRun(handler, version, outputCollector);
    }
  }

  private static @NotNull GitHandlerRebaseEditorManager prepareGeneralPurposeEditor(@NotNull Project project,
                                                                                    @NotNull GitLineHandler handler) {
    return GitHandlerRebaseEditorManager.prepareEditor(handler, new GitSimpleEditorHandler(project));
  }

  /**
   * Run handler with per-project locking, logging
   */
  private static @NotNull GitCommandResult doRun(@NotNull GitLineHandler handler,
                                                 @NotNull GitVersion version,
                                                 @NotNull OutputCollector outputCollector) {
    getGitTraceEnvironmentVariables(version).forEach(handler::addCustomEnvironmentVariable);

    boolean canSuppressOptionalLocks = Registry.is("git.use.no.optional.locks") &&
                                       GitVersionSpecialty.ENV_GIT_OPTIONAL_LOCKS_ALLOWED.existsIn(version);
    if (canSuppressOptionalLocks) {
      handler.addCustomEnvironmentVariable("GIT_OPTIONAL_LOCKS", "0");
    }

    GitCommandResultListener resultListener = new GitCommandResultListener(outputCollector);
    handler.addLineListener(resultListener);

    try (AccessToken ignored = lock(handler, canSuppressOptionalLocks)) {
      writeOutputToConsole(handler);
      handler.runInCurrentThread();
    }
    catch (IOException e) {
      return GitCommandResult.error(GitBundle.message("git.error.cant.process.output", e.getLocalizedMessage()));
    }

    String rootName = getPresentableRootName(handler);
    return new GitCommandResult(resultListener.myStartFailed,
                                resultListener.myExitCode,
                                outputCollector.myErrorOutput,
                                outputCollector.myOutput,
                                rootName);
  }

  private static @Nullable @Nls String getPresentableRootName(@NotNull GitLineHandler handler) {
    if (GitHandler.shouldSuppressReadLocks()) return null;
    if (handler.getCommand().equals(GitCommand.VERSION)) return null;

    VirtualFile root = handler.getExecutableContext().getRoot();
    if (root == null) return null;

    Project project = handler.project();
    if (project == null) return root.getName();

    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    VirtualFile vcsRoot = vcsManager.getVcsRootFor(root);
    if (root.equals(vcsRoot)) {
      if (vcsManager.getRootsUnderVcs(GitVcs.getInstance(project)).length == 1) {
        return null;
      }
      return ProjectLevelVcsManager.getInstance(project).getShortNameForVcsRoot(root);
    }

    return root.getName();
  }

  /**
   * Only public because of {@link GitExecutableValidator#isExecutableValid()}
   */
  public static @NotNull Map<String, String> getGitTraceEnvironmentVariables(@NotNull GitVersion version) {
    Map<@NonNls String, @NonNls String> environment = new HashMap<>(5);
    int logLevel = Registry.intValue("git.execution.trace");
    if (logLevel == 0) {
      environment.put("GIT_TRACE", "0");
      if (GitVersionSpecialty.ENV_GIT_TRACE_PACK_ACCESS_ALLOWED.existsIn(version)) environment.put("GIT_TRACE_PACK_ACCESS", "");
      environment.put("GIT_TRACE_PACKET", "");
      environment.put("GIT_TRACE_PERFORMANCE", "0");
      environment.put("GIT_TRACE_SETUP", "0");
    }
    else {
      String logFile = PathManager.getLogPath() + "/gittrace.log";
      if ((logLevel & 1) == 1) environment.put("GIT_TRACE", logFile);
      if ((logLevel & 2) == 2) environment.put("GIT_TRACE_PACK_ACCESS", logFile);
      if ((logLevel & 4) == 4) environment.put("GIT_TRACE_PACKET", logFile);
      if ((logLevel & 8) == 8) environment.put("GIT_TRACE_PERFORMANCE", logFile);
      if ((logLevel & 16) == 16) environment.put("GIT_TRACE_SETUP", logFile);
    }
    return environment;
  }

  @RequiresBackgroundThread
  public static boolean loadFileAndShowInSimpleEditor(@NotNull Project project,
                                                      @Nullable VirtualFile root,
                                                      @NotNull File file,
                                                      @NotNull @NlsContexts.DialogTitle String dialogTitle,
                                                      @NotNull @NlsContexts.Button String okButtonText) throws IOException {
    Charset encoding = root == null ? StandardCharsets.UTF_8 : GitConfigUtil.getCommitEncodingCharset(project, root);
    String initialText = trimLeading(ignoreComments(FileUtil.loadFile(file, encoding)));

    String newText = showUnstructuredEditorAndWait(project, root, initialText, dialogTitle, okButtonText);
    if (newText == null) {
      return false;
    }
    else {
      FileUtil.writeToFile(file, newText.getBytes(encoding));
      return true;
    }
  }

  private static @Nullable String showUnstructuredEditorAndWait(@NotNull Project project,
                                                                @Nullable VirtualFile root,
                                                                @NotNull @NlsSafe String initialText,
                                                                @NotNull @NlsContexts.DialogTitle String dialogTitle,
                                                                @NotNull @NlsContexts.Button String okButtonText) {
    Ref<String> newText = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      GitUnstructuredEditor editor = new GitUnstructuredEditor(project, root, initialText, dialogTitle, okButtonText);
      DialogManager.show(editor);
      if (editor.isOK()) {
        newText.set(editor.getText());
      }
    });
    return newText.get();
  }

  private static @NotNull String ignoreComments(@NotNull String text) {
    String[] lines = splitByLinesKeepSeparators(text);
    return StreamEx.of(lines)
      .filter(line -> !line.startsWith(GitUtil.COMMENT_CHAR))
      .joining();
  }

  private static class GitCommandResultListener implements GitLineHandlerListener {
    private final OutputCollector myOutputCollector;

    private int myExitCode = 0;
    private boolean myStartFailed = false;

    GitCommandResultListener(OutputCollector outputCollector) {
      myOutputCollector = outputCollector;
    }

    @Override
    public void onLineAvailable(String line, Key outputType) {
      if (outputType == ProcessOutputTypes.STDOUT) {
        myOutputCollector.outputLineReceived(line);
      }
      else if (outputType == ProcessOutputTypes.STDERR &&
               !looksLikeProgress(line)) {
        myOutputCollector.errorLineReceived(line);
      }
    }

    @Override
    public void processTerminated(int code) {
      myExitCode = code;
    }

    @Override
    public void startFailed(@NotNull Throwable t) {
      myStartFailed = true;
      myOutputCollector.errorLineReceived(GitBundle.message("git.executable.unknown.error.message", t.getLocalizedMessage()));
    }
  }

  private abstract static class OutputCollector {
    final List<String> myOutput = new ArrayList<>();
    final List<String> myErrorOutput = new ArrayList<>();

    final void addOutputLine(@NotNull String line) {
      synchronized (myOutput) {
        myOutput.add(line);
      }
    }

    final void addErrorLine(@NotNull String line) {
      synchronized (myErrorOutput) {
        myErrorOutput.add(line);
      }
    }

    abstract void outputLineReceived(@NotNull String line);

    abstract void errorLineReceived(@NotNull String line);
  }

  private static void writeOutputToConsole(@NotNull GitLineHandler handler) {
    if (handler.isSilent()) return;

    Project project = handler.project();
    if (project != null && !project.isDefault()) {
      String workingDir = stringifyWorkingDir(project.getBasePath(), handler.getWorkingDirectory());
      GitVcsConsoleWriter.getInstance(project).showCommandLine(String.format("[%s] %s", workingDir, handler.printableCommandLine()));

      handler.addLineListener(new GitCommandOutputLogger(project, handler));
    }
  }

  private static class GitCommandOutputLogger implements GitLineHandlerListener {
    private final @NotNull GitLineHandler myHandler;

    private final GitVcsConsoleWriter myVcsConsoleWriter;
    private final AnsiEscapeDecoder myAnsiEscapeDecoder;

    GitCommandOutputLogger(@NotNull Project project, @NotNull GitLineHandler handler) {
      myHandler = handler;
      myVcsConsoleWriter = GitVcsConsoleWriter.getInstance(project);
      myAnsiEscapeDecoder = new AnsiEscapeDecoder();
    }

    @Override
    public void onLineAvailable(String line, Key outputType) {
      try {
        if (StringUtil.isEmptyOrSpaces(line)) return;
        if (outputType == ProcessOutputTypes.SYSTEM) return;
        if (outputType == ProcessOutputTypes.STDOUT && myHandler.isStdoutSuppressed()) return;
        if (outputType == ProcessOutputTypes.STDERR && myHandler.isStderrSuppressed()) return;

        List<Pair<String, Key>> lineChunks = new ArrayList<>();
        myAnsiEscapeDecoder.escapeText(line, outputType, (text, key) -> lineChunks.add(Pair.create(text, key)));
        myVcsConsoleWriter.showMessage(lineChunks);
      }
      catch (ProcessCanceledException ignore) {
      }
      catch (Exception e) {
        throw new RuntimeException("Logging error for " + myHandler, e);
      }
    }
  }

  private static @NotNull AccessToken lock(@NotNull GitLineHandler handler, boolean canSuppressOptionalLocks) {
    Project project = handler.project();
    LockingPolicy lockingPolicy = handler.getCommand().lockingPolicy();

    if (project == null || project.isDefault() || lockingPolicy == READ) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    ReadWriteLock executionLock = GitVcs.getInstance(project).getCommandLock();
    Lock lock = lockingPolicy == READ_OPTIONAL_LOCKING && canSuppressOptionalLocks
                ? executionLock.readLock()
                : executionLock.writeLock();

    ProgressIndicatorUtils.awaitWithCheckCanceled(lock);
    return new AccessToken() {
      @Override
      public void finish() {
        lock.unlock();
      }
    };
  }

  public static boolean looksLikeProgress(@NotNull String line) {
    if (PROGRESS_PATTERN.matcher(line).matches()) return true;
    return ContainerUtil.exists(SUPPRESSED_PROGRESS_INDICATORS, prefix -> {
      if (StringUtil.startsWith(line, prefix)) return true;
      if (StringUtil.startsWith(line, REMOTE_PROGRESS_PREFIX)) {
        return StringUtil.startsWith(line, REMOTE_PROGRESS_PREFIX.length(), prefix);
      }
      return false;
    });
  }

  /**
   * Pattern that matches most git progress messages.
   * <p>
   * 'remote: Finding sources:   1% (575/57489)   '
   * 'Receiving objects: 100% (57489/57489), 50.03 MiB | 2.83 MiB/s, done.'
   */
  private static final Pattern PROGRESS_PATTERN = Pattern.compile(".*:\\s*\\d{1,3}% \\(\\d+/\\d+\\).*");

  private static final @NonNls String REMOTE_PROGRESS_PREFIX = "remote: ";

  /**
   * 'remote: Counting objects: 198285, done'
   * 'Expanding reachable commits in commit graph: 95907'
   */
  private static final @NonNls String[] SUPPRESSED_PROGRESS_INDICATORS = {
    "Counting objects: ",
    "Enumerating objects: ",
    "Compressing objects: ",
    "Writing objects: ",
    "Receiving objects: ",
    "Resolving deltas: ",
    "Finding sources: ",
    "Updating files: ",
    "Checking out files: ",
    "Expanding reachable commits in commit graph: ",
    "Delta compression using up to "
  };

  private static boolean looksLikeError(@NotNull @NonNls String text) {
    return ContainerUtil.exists(ERROR_INDICATORS, indicator -> StringUtil.startsWithIgnoreCase(text.trim(), indicator));
  }

  // could be upper-cased, so should check case-insensitively
  public static final @NonNls String[] ERROR_INDICATORS = {
    "warning:",
    "error:",
    "fatal:",
    "remote: error",
    "Cannot",
    "Could not",
    "Interactive rebase already started",
    "refusing to pull",
    "cannot rebase:",
    "conflict",
    "unable",
    "The file will have its original",
    "runnerw:"
  };

  static @NotNull String stringifyWorkingDir(@Nullable String basePath, @NotNull File workingDir) {
    if (basePath != null) {
      String relPath = FileUtil.getRelativePath(basePath, FileUtil.toSystemIndependentName(workingDir.getPath()), '/');
      if (".".equals(relPath)) {
        return workingDir.getName();
      }
      else if (relPath != null) {
        return FileUtil.toSystemDependentName(relPath);
      }
    }
    return workingDir.getPath();
  }
}
