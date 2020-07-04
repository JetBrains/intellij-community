// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
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
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import static com.intellij.openapi.util.text.StringUtil.splitByLinesKeepSeparators;
import static com.intellij.openapi.util.text.StringUtil.trimLeading;
import static git4idea.commands.GitCommand.LockingPolicy.READ;

/**
 * Basic functionality for git handler execution.
 */
public abstract class GitImplBase implements Git {

  private static final Logger LOG = Logger.getInstance(GitImplBase.class);

  @NotNull
  @Override
  public GitCommandResult runCommand(@NotNull GitLineHandler handler) {
    return run(handler, getCollectingCollector());
  }

  @Override
  @NotNull
  public GitCommandResult runCommand(@NotNull Computable<? extends GitLineHandler> handlerConstructor) {
    return run(handlerConstructor, GitImplBase::getCollectingCollector);
  }

  @NotNull
  private static OutputCollector getCollectingCollector() {
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
  @NotNull
  public GitCommandResult runCommandWithoutCollectingOutput(@NotNull GitLineHandler handler) {
    return run(handler, new OutputCollector() {
      @Override
      protected void outputLineReceived(@NotNull String line) {}

      @Override
      protected void errorLineReceived(@NotNull String line) {
        addErrorLine(line);
      }
    });
  }

  /**
   * Run handler with retry on authentication failure
   */
  @NotNull
  private static GitCommandResult run(@NotNull Computable<? extends GitLineHandler> handlerConstructor,
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
  @NotNull
  private static GitCommandResult run(@NotNull GitLineHandler handler, @NotNull OutputCollector outputCollector) {
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
        return handlePreValidationException(handler.project(), e);
      }
    }

    Project project = handler.project();
    if (project != null && project.isDisposed()) {
      LOG.warn("Project has already been disposed");
      throw new ProcessCanceledException();
    }

    if (project != null) {
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

  @NotNull
  private static GitHandlerRebaseEditorManager prepareGeneralPurposeEditor(@NotNull Project project, @NotNull GitLineHandler handler) {
    return GitHandlerRebaseEditorManager.prepareEditor(handler, new GitSimpleEditorHandler(project));
  }

  /**
   * Run handler with per-project locking, logging
   */
  @NotNull
  private static GitCommandResult doRun(@NotNull GitLineHandler handler,
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

    try (AccessToken ignored = lock(handler)) {
      writeOutputToConsole(handler);
      handler.runInCurrentThread();
    }
    catch (IOException e) {
      return GitCommandResult.error("Error processing input stream: " + e.getLocalizedMessage());
    }
    return new GitCommandResult(resultListener.myStartFailed,
                                resultListener.myExitCode,
                                outputCollector.myErrorOutput,
                                outputCollector.myOutput);
  }

  /**
   * Only public because of {@link GitExecutableValidator#isExecutableValid()}
   */
  @NotNull
  public static Map<String, String> getGitTraceEnvironmentVariables(@NotNull GitVersion version) {
    Map<String, String> environment = new HashMap<>(5);
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

  @CalledInBackground
  public static boolean loadFileAndShowInSimpleEditor(@NotNull Project project,
                                                      @Nullable VirtualFile root,
                                                      @NotNull File file,
                                                      @NotNull String dialogTitle,
                                                      @NotNull String okButtonText) throws IOException {
    String encoding = root == null ? CharsetToolkit.UTF8 : GitConfigUtil.getCommitEncoding(project, root);
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

  @Nullable
  private static String showUnstructuredEditorAndWait(@NotNull Project project,
                                                      @Nullable VirtualFile root,
                                                      @NotNull String initialText,
                                                      @NotNull String dialogTitle,
                                                      @NotNull String okButtonText) {
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

  @NotNull
  private static String ignoreComments(@NotNull String text) {
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
               !suppressStderrLine(line)) {
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

  private static abstract class OutputCollector {
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

  @NotNull
  private static GitCommandResult handlePreValidationException(@Nullable Project project, @NotNull Exception e) {
    // Show notification if it's a project non-modal task and cancel the task
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (project != null
        && progressIndicator != null
        && !progressIndicator.getModalityState().dominates(ModalityState.NON_MODAL)) {
      GitExecutableProblemsNotifier.getInstance(project).notifyExecutionError(e);
      throw new ProcessCanceledException(e);
    }
    else {
      return GitCommandResult.startError(
        GitBundle.getString("git.executable.validation.error.start.title") + ": \n" +
        GitExecutableProblemsNotifier.getPrettyErrorMessage(e)
      );
    }
  }

  private static void writeOutputToConsole(@NotNull GitLineHandler handler) {
    if (handler.isSilent()) return;

    Project project = handler.project();
    if (project != null && !project.isDefault()) {
      GitVcsConsoleWriter vcsConsoleWriter = GitVcsConsoleWriter.getInstance(project);

      String workingDir = stringifyWorkingDir(project.getBasePath(), handler.getWorkingDirectory());
      vcsConsoleWriter.showCommandLine(String.format("[%s] %s", workingDir, handler.printableCommandLine()));

      handler.addLineListener(new GitLineHandlerListener() {
        private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

        @Override
        public void onLineAvailable(String line, Key outputType) {
          if (StringUtil.isEmptyOrSpaces(line)) return;
          if (outputType == ProcessOutputTypes.SYSTEM) return;
          if (outputType == ProcessOutputTypes.STDOUT && handler.isStdoutSuppressed()) return;
          if (outputType == ProcessOutputTypes.STDERR && handler.isStderrSuppressed()) return;

          List<Pair<String, Key>> lineChunks = new ArrayList<>();
          myAnsiEscapeDecoder.escapeText(line, outputType, (text, key) -> lineChunks.add(Pair.create(text, key)));
          vcsConsoleWriter.showMessage(lineChunks);
        }
      });
    }
  }

  @NotNull
  private static AccessToken lock(@NotNull GitLineHandler handler) {
    Project project = handler.project();
    LockingPolicy lockingPolicy = handler.getCommand().lockingPolicy();

    if (project == null || project.isDefault() || lockingPolicy == READ) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    ReadWriteLock executionLock = GitVcs.getInstance(project).getCommandLock();
    executionLock.writeLock().lock();
    return new AccessToken() {
      @Override
      public void finish() {
        executionLock.writeLock().unlock();
      }
    };
  }

  private static boolean suppressStderrLine(@NotNull String line) {
    return ContainerUtil.exists(SUPPRESSED_PROGRESS_INDICATORS, indicator -> StringUtil.startsWith(line, indicator));
  }

  public static boolean looksLikeProgress(@NotNull String line) {
    return ContainerUtil.exists(SUPPRESSED_PROGRESS_INDICATORS, indicator -> StringUtil.startsWith(line, indicator)) ||
           ContainerUtil.exists(PROGRESS_INDICATORS, indicator -> StringUtil.startsWith(line, indicator));
  }

  private static final String[] SUPPRESSED_PROGRESS_INDICATORS = {
    "remote: Counting objects: ",
    "remote: Enumerating objects: ",
    "remote: Compressing objects: ",
    "remote: Writing objects: ",
    "remote: Receiving objects: ",
    "remote: Resolving deltas: ",
    "remote: Finding sources: ",
    "Receiving objects: ",
    "Resolving deltas: ",
    "Updating files: ",
    "Checking out files: "
  };

  private static final String[] PROGRESS_INDICATORS = {
    "remote: Total "
  };

  private static boolean looksLikeError(@NotNull final String text) {
    return ContainerUtil.exists(ERROR_INDICATORS, indicator -> StringUtil.startsWithIgnoreCase(text.trim(), indicator));
  }

  // could be upper-cased, so should check case-insensitively
  public static final String[] ERROR_INDICATORS = {
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

  @NotNull
  static String stringifyWorkingDir(@Nullable String basePath, @NotNull File workingDir) {
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
