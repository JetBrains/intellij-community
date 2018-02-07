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
import git4idea.i18n.GitBundle;
import git4idea.util.GitVcsConsoleWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
  private static GitCommandResult run(@NotNull Computable<GitLineHandler> handlerConstructor,
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
  private static GitCommandResult run(@NotNull GitLineHandler handler, @NotNull OutputCollector outputCollector) {
    Project project = handler.project();
    if (project != null && handler.isRemote()) {
      try {
        GitHandlerAuthenticationManager authenticationManager = GitHandlerAuthenticationManager.prepare(project, handler);
        return GitCommandResult.withAuthentication(doRun(handler, outputCollector), authenticationManager.isHttpAuthFailed());
      }
      catch (IOException e) {
        return GitCommandResult.startError("Failed to start Git process " + e.getLocalizedMessage());
      }
    }

    return doRun(handler, outputCollector);
  }

  /**
   * Run handler with per-project locking, logging
   */
  @NotNull
  private static GitCommandResult doRun(@NotNull GitLineHandler handler, @NotNull OutputCollector outputCollector) {
    GitCommandResult preValidationResult = preValidateExecutable(handler);
    if (preValidationResult != null) return preValidationResult;

    try (AccessToken ignored = lock(handler)) {
      GitCommandResultListener resultListener = new GitCommandResultListener(outputCollector);
      handler.addLineListener(resultListener);

      writeOutputToConsole(handler);

      handler.runInCurrentThread();
      return new GitCommandResult(resultListener.myStartFailed,
                                  resultListener.myExitCode,
                                  outputCollector.myErrorOutput,
                                  outputCollector.myOutput);
    }
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

  @Nullable
  private static GitCommandResult preValidateExecutable(@NotNull GitLineHandler handler) {
    if (handler.isPreValidateExecutable()) {
      String executablePath = handler.getExecutablePath();
      try {
        GitExecutableManager.getInstance().identifyVersion(executablePath);
      }
      catch (Exception e) {
        // Show notification if it's a project non-modal task and cancel the task
        ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        Project project = handler.project();
        if (project != null
            && progressIndicator != null
            && !progressIndicator.getModalityState().dominates(ModalityState.NON_MODAL)) {
          GitExecutableProblemsNotifier.getInstance(project).notifyExecutionError(handler.getExecutablePath(), e);
          throw new ProcessCanceledException(e);
        }
        else {
          return GitCommandResult.error(
            GitBundle.getString("git.executable.validation.error.title") + " \n" +
            GitBundle.message("git.executable.validation.error.start.subtitle", executablePath) + ": " +
            GitExecutableProblemsNotifier.getPrettyErrorMessage(e)
          );
        }
      }
    }
    return null;
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
              vcsConsoleWriter.showErrorMessage(line);
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
