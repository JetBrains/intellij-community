// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitVcs;
import git4idea.config.GitExecutableManager;
import git4idea.config.GitExecutableProblemsNotifier;
import git4idea.config.GitVersion;
import git4idea.config.GitVersionSpecialty;
import git4idea.i18n.GitBundle;
import git4idea.util.GitVcsConsoleWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import static git4idea.commands.GitCommand.LockingPolicy.WRITE;

/**
 * Basic functionality for git handler execution.
 */
abstract class GitImplBase implements Git {
  @NotNull
  @Override
  public GitCommandResult runCommand(@NotNull GitLineHandler handler) {
    return run(handler, getCollectingCollector());
  }

  @Override
  @NotNull
  public GitCommandResult runCommand(@NotNull Computable<GitLineHandler> handlerConstructor) {
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
        if (!looksLikeError(line)) {
          addOutputLine(line);
        }
        else {
          addErrorLine(line);
        }
      }
    };
  }

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
  private GitCommandResult run(@NotNull Computable<GitLineHandler> handlerConstructor,
                               @NotNull Computable<OutputCollector> outputCollectorConstructor) {
    @NotNull GitCommandResult result;

    int authAttempt = 0;
    do {
      GitLineHandler handler = handlerConstructor.compute();
      OutputCollector outputCollector = outputCollectorConstructor.compute();

      result = run(handler, outputCollector);
    }
    while (result.isAuthenticationFailed() && authAttempt++ < 2);
    return result;
  }

  /**
   * Run handler with per-project locking, logging and authentication
   */
  @NotNull
  private GitCommandResult run(@NotNull GitLineHandler handler, @NotNull OutputCollector outputCollector) {
    Project project = handler.project();
    if (project != null && handler.isRemote()) {
      try (GitHandlerAuthenticationManager authenticationManager = prepareAuthentication(project, handler)) {
        return GitCommandResult.withAuthentication(doRun(handler, outputCollector), authenticationManager.isHttpAuthFailed());
      }
      catch (IOException e) {
        return GitCommandResult.startError("Failed to start Git process " + e.getLocalizedMessage());
      }
    }

    return doRun(handler, outputCollector);
  }

  @NotNull
  protected GitHandlerAuthenticationManager prepareAuthentication(@NotNull Project project, @NotNull GitLineHandler handler)
    throws IOException {
    return GitHandlerAuthenticationManager.prepare(project, handler);
  }

  /**
   * Run handler with per-project locking, logging
   */
  @NotNull
  private static GitCommandResult doRun(@NotNull GitLineHandler handler, @NotNull OutputCollector outputCollector) {
    GitVersion version = GitVersion.NULL;
    if (handler.isPreValidateExecutable()) {
      String executablePath = handler.getExecutablePath();
      try {
        version = GitExecutableManager.getInstance().identifyVersion(executablePath);
      }
      catch (Exception e) {
        return handlePreValidationException(handler.project(), e);
      }
    }

    getGitTraceEnvironmentVariables(version).forEach(handler::addCustomEnvironmentVariable);

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
   * Only public because of {@link git4idea.config.GitExecutableValidator#isExecutableValid()}
   */
  @NotNull
  public static Map<String, String> getGitTraceEnvironmentVariables(@NotNull GitVersion version) {
    Map<String, String> environment = new HashMap<>(5);
    environment.put("GIT_TRACE", "0");
    if (GitVersionSpecialty.ENV_GIT_TRACE_PACK_ACCESS_ALLOWED.existsIn(version)) environment.put("GIT_TRACE_PACK_ACCESS", "");
    environment.put("GIT_TRACE_PACKET", "");
    environment.put("GIT_TRACE_PERFORMANCE", "0");
    environment.put("GIT_TRACE_SETUP", "0");
    return environment;
  }

  private static class GitCommandResultListener implements GitLineHandlerListener {
    private final OutputCollector myOutputCollector;

    private int myExitCode = 0;
    private boolean myStartFailed = false;

    public GitCommandResultListener(OutputCollector outputCollector) {
      myOutputCollector = outputCollector;
    }

    @Override
    public void onLineAvailable(String line, Key outputType) {
      if (outputType == ProcessOutputTypes.STDOUT) {
        myOutputCollector.outputLineReceived(line);
      }
      else if (outputType == ProcessOutputTypes.STDERR) {
        myOutputCollector.errorLineReceived(line);
      }
    }

    @Override
    public void processTerminated(int code) {
      myExitCode = code;
    }

    @Override
    public void startFailed(Throwable t) {
      myStartFailed = true;
      myOutputCollector.errorLineReceived("Failed to start Git process " + t.getLocalizedMessage());
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
    Project project = handler.project();
    if (project != null && !project.isDefault()) {
      GitVcsConsoleWriter vcsConsoleWriter = GitVcsConsoleWriter.getInstance(project);
      handler.addLineListener(new GitLineHandlerAdapter() {
        @Override
        public void onLineAvailable(String line, Key outputType) {
          if (!handler.isSilent() && !StringUtil.isEmptyOrSpaces(line)) {
            if (outputType == ProcessOutputTypes.STDOUT && !handler.isStdoutSuppressed()) {
              vcsConsoleWriter.showMessage(line);
            }
            else if (outputType == ProcessOutputTypes.STDERR && !handler.isStderrSuppressed()) {
              if (!looksLikeProgress(line)) vcsConsoleWriter.showErrorMessage(line);
            }
          }
        }
      });
      if (!handler.isSilent()) {
        vcsConsoleWriter.showCommandLine("[" + stringifyWorkingDir(project.getBasePath(), handler.getWorkingDirectory()) + "] "
                                         + handler.printableCommandLine());
      }
    }
  }

  @NotNull
  private static AccessToken lock(@NotNull GitLineHandler handler) {
    Project project = handler.project();
    if (project != null && !project.isDefault() && WRITE == handler.getCommand().lockingPolicy()) {
      ReadWriteLock executionLock = GitVcs.getInstance(project).getCommandLock();
      executionLock.writeLock().lock();
      return new AccessToken() {
        @Override
        public void finish() {
          executionLock.writeLock().unlock();
        }
      };
    }
    return AccessToken.EMPTY_ACCESS_TOKEN;
  }

  private static boolean looksLikeProgress(@NotNull String line) {
    String trimmed = StringUtil.trimStart(line, REMOTE_PROGRESS_PREFIX);
    return ContainerUtil.exists(PROGRESS_INDICATORS, indicator -> StringUtil.startsWith(trimmed, indicator));
  }

  public static final String REMOTE_PROGRESS_PREFIX = "remote: ";

  public static final String[] PROGRESS_INDICATORS = {
    "Counting objects:",
    "Compressing objects:",
    "Writing objects:",
    "Receiving objects:",
    "Resolving deltas:"
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
