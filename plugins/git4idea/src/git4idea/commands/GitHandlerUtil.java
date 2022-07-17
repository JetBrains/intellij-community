// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsException;
import git4idea.i18n.GitBundle;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @deprecated use {@link GitImpl}
 */
@Deprecated
public final class GitHandlerUtil {

  private GitHandlerUtil() {
  }

  @Deprecated(forRemoval = true)
  public static int doSynchronously(@NotNull GitLineHandler handler,
                                    @NotNull @NlsContexts.ProgressTitle String operationTitle,
                                    @NotNull @Nls String operationName) {
    ProgressManager.getInstance().run(new Task.Modal(handler.project(), operationTitle, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        handler.addLineListener(new GitLineHandlerListenerProgress(indicator, handler, operationName, true));
        runInCurrentThread(handler, indicator, true, operationTitle);
      }
    });
    return handler.getExitCode();
  }

  @Deprecated(forRemoval = true)
  public static void runInCurrentThread(@NotNull GitHandler handler,
                                        @Nullable ProgressIndicator indicator,
                                        boolean setIndeterminateFlag,
                                        @Nullable @Nls String operationName) {
    runInCurrentThread(handler, () -> {
      if (indicator != null) {
        indicator.setText(operationName == null ? GitBundle.message("git.running", handler.printableCommandLine()) : operationName);
        indicator.setText2("");
        if (setIndeterminateFlag) {
          indicator.setIndeterminate(true);
        }
      }
    });
  }

  @Deprecated
  public static void runInCurrentThread(@NotNull GitHandler handler, @Nullable Runnable postStartAction) {
    handler.runInCurrentThread(postStartAction);
  }

  @Deprecated(forRemoval = true)
  public static class GitLineHandlerListenerProgress implements GitLineHandlerListener {
    @NotNull protected final GitHandler myHandler;
    @NotNull protected final @Nls String myOperationName;
    @Nullable private final ProgressIndicator myProgressIndicator;
    protected boolean myShowErrors;

    public GitLineHandlerListenerProgress(@Nullable ProgressIndicator indicator,
                                          @NotNull GitHandler handler,
                                          @NotNull @Nls String operationName,
                                          boolean showErrors) {
      myHandler = handler;
      myOperationName = operationName;
      myShowErrors = showErrors;
      myProgressIndicator = indicator;
    }

    @Override
    public void processTerminated(int exitCode) {
      if (exitCode != 0) {
        ensureError(exitCode);
        if (myShowErrors) {
          EventQueue.invokeLater(() -> GitUIUtil.showOperationErrors(myHandler.project(), myHandler.errors(), myOperationName));
        }
      }
    }

    private void ensureError(int exitCode) {
      if (myHandler.errors().isEmpty()) {
        myHandler.addError(new VcsException(GitBundle.message("git.error.exit", exitCode)));
      }
    }

    @Override
    public void startFailed(@NotNull Throwable exception) {
      myHandler.addError(new VcsException(GitBundle.message("git.executable.unknown.error.message", exception.getMessage()), exception));
      if (myShowErrors) {
        EventQueue.invokeLater(() -> GitUIUtil.showOperationError(myHandler.project(), myOperationName, exception.getMessage()));
      }
    }

    @Override
    public void onLineAvailable(@NotNull String line, @NotNull Key outputType) {
      if (isErrorLine(line.trim())) {
        myHandler.addError(new VcsException(line));
      }
      if (myProgressIndicator != null) {
        myProgressIndicator.setText2(line);
      }
    }
  }

  static boolean isErrorLine(@NotNull String text) {
    for (String prefix : GitImplBase.ERROR_INDICATORS) {
      if (text.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
