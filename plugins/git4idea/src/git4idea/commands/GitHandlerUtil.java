// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import git4idea.i18n.GitBundle;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @deprecated use {@link GitImpl}
 */
@Deprecated
public class GitHandlerUtil {

  private GitHandlerUtil() {
  }

  @Deprecated
  public static int doSynchronously(final GitLineHandler handler, final String operationTitle, @NonNls final String operationName) {
    final ProgressManager manager = ProgressManager.getInstance();
    manager.run(new Task.Modal(handler.project(), operationTitle, true) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        handler.addLineListener(new GitLineHandlerListenerProgress(indicator, handler, operationName, true));
        runInCurrentThread(handler, indicator, true, operationTitle);
      }
    });
    if (!handler.isStarted()) {
      return -1;
    }
    return handler.getExitCode();
  }

  @Deprecated
  public static void runInCurrentThread(final GitHandler handler,
                                        final ProgressIndicator indicator,
                                        final boolean setIndeterminateFlag,
                                        @Nullable final String operationName) {
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
  public static void runInCurrentThread(final GitHandler handler, @Nullable final Runnable postStartAction) {
    handler.runInCurrentThread(postStartAction);
  }

  /**
   * A base class for handler listener that implements error handling logic
   */
  private abstract static class GitHandlerListenerBase implements GitHandlerListener {
    /**
     * a handler
     */
    protected final GitHandler myHandler;
    /**
     * a operation name for the handler
     */
    protected final String myOperationName;
    /**
     * if true, the errors are shown when process is terminated
     */
    protected boolean myShowErrors;

    /**
     * A constructor
     *
     * @param handler       a handler instance
     * @param operationName an operation name
     */
    GitHandlerListenerBase(final GitHandler handler, final String operationName) {
      this(handler, operationName, true);
    }

    /**
     * A constructor
     *
     * @param handler       a handler instance
     * @param operationName an operation name
     * @param showErrors    if true, the errors are shown when process is terminated
     */
    GitHandlerListenerBase(final GitHandler handler, final String operationName, boolean showErrors) {
      myHandler = handler;
      myOperationName = operationName;
      myShowErrors = showErrors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processTerminated(final int exitCode) {
      if (exitCode != 0) {
        ensureError(exitCode);
        if (myShowErrors) {
          EventQueue.invokeLater(() -> GitUIUtil.showOperationErrors(myHandler.project(), myHandler.errors(), myOperationName));
        }
      }
    }

    /**
     * Ensure that at least one error is available in case if the process exited with non-zero exit code
     *
     * @param exitCode the exit code of the process
     */
    protected void ensureError(final int exitCode) {
      if (myHandler.errors().isEmpty()) {
        String text = getErrorText();
        if ((text == null || text.length() == 0) && myHandler.errors().isEmpty()) {
          myHandler.addError(new VcsException(GitBundle.message("git.error.exit", exitCode)));
        }
        else {
          myHandler.addError(new VcsException(text));
        }
      }
    }

    /**
     * @return error text for the handler, if null or empty string a default message is used.
     */
    protected abstract String getErrorText();

    /**
     * {@inheritDoc}
     */
    @Override
    public void startFailed(@NotNull final Throwable exception) {
      myHandler.addError(new VcsException("Git start failed: " + exception.getMessage(), exception));
      if (myShowErrors) {
        EventQueue.invokeLater(() -> GitUIUtil.showOperationError(myHandler.project(), myOperationName, exception.getMessage()));
      }
    }
  }

  /**
   * A base class for line handler listeners
   */
  private abstract static class GitLineHandlerListenerBase extends GitHandlerListenerBase implements GitLineHandlerListener {
    /**
     * A constructor
     *
     * @param handler       a handler instance
     * @param operationName an operation name
     * @param showErrors    if true, the errors are shown when process is terminated
     */
    GitLineHandlerListenerBase(GitHandler handler, String operationName, boolean showErrors) {
      super(handler, operationName, showErrors);
    }

  }

  /**
   * A base class for line handler listeners
   */
  public static class GitLineHandlerListenerProgress extends GitLineHandlerListenerBase {
    /**
     * a progress manager to use
     */
    @Nullable private final ProgressIndicator myProgressIndicator;

    /**
     * A constructor
     *
     * @param indicator       the project manager
     * @param handler       a handler instance
     * @param operationName an operation name
     * @param showErrors    if true, the errors are shown when process is terminated
     */
    public GitLineHandlerListenerProgress(@Nullable ProgressIndicator indicator, GitHandler handler, String operationName, boolean showErrors) {
      super(handler, operationName, showErrors);
      myProgressIndicator = indicator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getErrorText() {
      // all lines are already calculated as errors
      return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLineAvailable(final String line, final Key outputType) {
      if (isErrorLine(line.trim())) {
        myHandler.addError(new VcsException(line));
      }
      if (myProgressIndicator != null) {
        myProgressIndicator.setText2(line);
      }
    }
  }

  /**
   * Check if the line is an error line
   *
   * @param text a line to check
   * @return true if the error line
   */
  protected static boolean isErrorLine(String text) {
    for (String prefix : GitImpl.ERROR_INDICATORS) {
      if (text.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
