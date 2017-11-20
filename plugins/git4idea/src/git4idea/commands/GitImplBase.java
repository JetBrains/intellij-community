// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic functionality for git handler execution.
 */
abstract class GitImplBase implements Git {
  @NotNull
  @Override
  public GitCommandResult runCommand(@NotNull GitLineHandler handler) {
    return runCommand(() -> handler);
  }

  @Override
  @NotNull
  public GitCommandResult runCommand(@NotNull Computable<GitLineHandler> handlerConstructor) {
    return run(handlerConstructor, () -> new OutputCollector() {
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
    });
  }

  @NotNull
  public GitCommandResult runCommandWithoutCollectingOutput(@NotNull GitLineHandler handler) {
    return run(() -> handler, () -> new OutputCollector() {
      @Override
      protected void outputLineReceived(@NotNull String line) {}

      @Override
      protected void errorLineReceived(@NotNull String line) {
        addErrorLine(line);
      }
    });
  }

  @NotNull
  private static GitCommandResult run(@NotNull Computable<GitLineHandler> handlerConstructor,
                                      @NotNull Computable<OutputCollector> outputCollectorConstructor) {
    @NotNull GitLineHandler handler;
    @NotNull OutputCollector outputCollector;
    @NotNull GitCommandResultListener resultListener;

    boolean authFailed;
    int authAttempt = 0;
    do {
      handler = handlerConstructor.compute();

      outputCollector = outputCollectorConstructor.compute();
      resultListener = new GitCommandResultListener(outputCollector);

      handler.addLineListener(resultListener);

      handler.runInCurrentThread(null);
      authFailed = handler.hasHttpAuthFailed();
    }
    while (authFailed && authAttempt++ < 2);
    return new GitCommandResult(
      !resultListener.myStartFailed && (handler.isIgnoredErrorCode(resultListener.myExitCode) || resultListener.myExitCode == 0),
      resultListener.myExitCode,
      outputCollector.myErrorOutput,
      outputCollector.myOutput);
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
    "runnerw:"
  };
}
