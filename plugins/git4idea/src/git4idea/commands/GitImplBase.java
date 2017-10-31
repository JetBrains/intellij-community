// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;

/**
 * Basic functionality for git handler execution.
 */
abstract class GitImplBase implements Git {
  protected static final Logger LOG = Logger.getInstance(Git.class);

  @Override
  @NotNull
  public GitCommandResult runCommand(@NotNull Computable<GitLineHandler> handlerConstructor) {
    return run(handlerConstructor);
  }

  @NotNull
  @Override
  public GitCommandResult runCommand(@NotNull GitLineHandler handler) {
    return runCommand(() -> handler);
  }

  @NotNull
  private static GitCommandResult run(@NotNull Computable<GitLineHandler> handlerConstructor) {
    final List<String> errorOutput = new ArrayList<>();
    final List<String> output = new ArrayList<>();
    final AtomicInteger exitCode = new AtomicInteger();
    final AtomicBoolean startFailed = new AtomicBoolean();

    int authAttempt = 0;
    boolean authFailed;
    boolean success;
    do {
      errorOutput.clear();
      output.clear();
      exitCode.set(0);
      startFailed.set(false);

      GitLineHandler handler = handlerConstructor.compute();
      handler.addLineListener(new GitLineHandlerListener() {
        @Override public void onLineAvailable(String line, Key outputType) {
          if (looksLikeError(line)) {
            synchronized (errorOutput) {
              errorOutput.add(line);
            }
          } else {
            synchronized (output) {
              output.add(line);
            }
          }
        }

        @Override public void processTerminated(int code) {
          exitCode.set(code);
        }

        @Override public void startFailed(Throwable t) {
          startFailed.set(true);
          errorOutput.add("Failed to start Git process");
        }
      });

      handler.runInCurrentThread(null);
      authFailed = handler.hasHttpAuthFailed();
      success = !startFailed.get() && (handler.isIgnoredErrorCode(exitCode.get()) || exitCode.get() == 0);
    }
    while (authFailed && authAttempt++ < 2);
    return new GitCommandResult(success, exitCode.get(), errorOutput, output);
  }

  private static boolean looksLikeError(@NotNull final String text) {
    return ContainerUtil.exists(ERROR_INDICATORS, indicator -> StringUtil.startsWithIgnoreCase(text.trim(), indicator));
  }

  // could be upper-cased, so should check case-insensitively
  public static final String[] ERROR_INDICATORS = {
    "error:", "remote: error", "fatal:",
    "Cannot", "Could not", "Interactive rebase already started", "refusing to pull", "cannot rebase:", "conflict",
    "unable"
  };
}
