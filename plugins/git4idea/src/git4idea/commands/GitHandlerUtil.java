/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.commands;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;

/**
 * Handler utilities that allow running handlers with progress indicators
 */
public class GitHandlerUtil {
  /**
   * a private constructor for utility class
   */
  private GitHandlerUtil() {
  }

  /**
   * Execute simple process synchronously with progress
   *
   * @param handler        a handler
   * @param operationTitle an operation title shown in progress dialog
   * @param operationName  an operation name shown in failure dialog
   * @return A stdout content or null if there was error (exit code != 0 or exception during start).
   */
  @Nullable
  public static String doSynchronously(final GitSimpleHandler handler, String operationTitle, @NonNls final String operationName) {
    handler.addListener(new GitHandlerListenerBase(handler, operationName) {
      protected String getErrorText() {
        String text = handler.getStderr();
        if (text.length() == 0) {
          text = handler.getStdout();
        }
        return text;
      }
    });
    runHandlerSynchronously(handler, operationTitle, ProgressManager.getInstance(), true);
    if (!handler.isStarted() || handler.getExitCode() != 0) {
      return null;
    }
    return handler.getStdout();
  }

  /**
   * Execute simple process synchronously with progress
   *
   * @param handler        a handler
   * @param operationTitle an operation title shown in progress dialog
   * @param operationName  an operation name shown in failure dialog
   * @return A exit code
   */
  public static int doSynchronously(final GitLineHandler handler, String operationTitle, @NonNls final String operationName) {
    return doSynchronously(handler, operationTitle, operationName, true);
  }


  /**
   * Execute simple process synchronously with progress
   *
   * @param handler              a handler
   * @param operationTitle       an operation title shown in progress dialog
   * @param operationName        an operation name shown in failure dialog
   * @param setIndeterminateFlag a flag indicating that progress should be configured as indeterminate
   * @return A exit code
   */
  public static int doSynchronously(final GitLineHandler handler,
                                    String operationTitle,
                                    @NonNls final String operationName,
                                    final boolean setIndeterminateFlag) {
    final ProgressManager manager = ProgressManager.getInstance();
    manager.run(new Task.Modal(handler.project(), operationTitle, false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        handler.addLineListener(new GitLineHandlerListenerProgress(indicator, handler, operationName));
        runInCurrentThread(handler, indicator, setIndeterminateFlag);
      }
    });
    if (!handler.isStarted()) {
      return -1;
    }
    return handler.getExitCode();
  }


  /**
   * Run handler synchronously. The method assumes that all listeners are set up.
   *
   * @param handler              a handler to run
   * @param operationTitle       operation title
   * @param manager              a progress manager
   * @param setIndeterminateFlag if true handler is configured as indeterminate
   */
  private static void runHandlerSynchronously(final GitHandler handler,
                                              final String operationTitle,
                                              final ProgressManager manager,
                                              final boolean setIndeterminateFlag) {
    manager.runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        runInCurrentThread(handler, manager.getProgressIndicator(), setIndeterminateFlag);
      }
    }, operationTitle, false, handler.project());
  }

  /**
   * Run handler in the current thread
   *
   * @param handler              a handler to run
   * @param indicator            a progress manager
   * @param setIndeterminateFlag if true handler is configured as indeterminate
   */
  private static void runInCurrentThread(final GitHandler handler, final ProgressIndicator indicator, final boolean setIndeterminateFlag) {
    handler.start();
    if (indicator != null) {
      indicator.setText(GitBundle.message("git.running", handler.printableCommandLine()));
      if (setIndeterminateFlag) {
        indicator.setIndeterminate(true);
      }
    }
    if (handler.isStarted()) {
      handler.waitFor();
    }
  }

  /**
   * Run synchronously using progress indicator, but collect exceptions instead of showing error dialog
   *
   * @param handler a handler to use
   * @return the collection of exception collected during operation
   */
  public static Collection<VcsException> doSynchronouslyWithExceptions(final GitLineHandler handler) {
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    return doSynchronouslyWithExceptions(handler, progressIndicator);
  }

  /**
   * Run synchronously using progress indicator, but collect exception instead of showing error dialog
   *
   * @param handler           a handler to use
   * @param progressIndicator a progress indicator
   * @return the collection of exception collected during operation
   */
  public static Collection<VcsException> doSynchronouslyWithExceptions(final GitLineHandler handler,
                                                                       final ProgressIndicator progressIndicator) {
    handler.addLineListener(new GitLineHandlerListenerProgress(progressIndicator, handler, "") {
      @Override
      public void processTerminated(final int exitCode) {
        if (exitCode != 0 && !handler.isIgnoredErrorCode(exitCode)) {
          ensureError(exitCode);
        }
      }

      @Override
      public void startFailed(final Throwable exception) {
        //noinspection ThrowableInstanceNeverThrown
        handler.addError(new VcsException("Git start failed: " + exception.toString(), exception));
      }
    });
    runInCurrentThread(handler, progressIndicator, false);
    return handler.errors();
  }

  /**
   * A base class for handler listener that implements error handling logic
   */
  private static abstract class GitHandlerListenerBase implements GitHandlerListener {
    /**
     * a handler
     */
    protected final GitHandler myHandler;
    /**
     * a operation name for the handler
     */
    protected final String myOperationName;

    /**
     * A constructor
     *
     * @param handler       a handler instance
     * @param operationName an operation name
     */
    public GitHandlerListenerBase(final GitHandler handler, final String operationName) {
      myHandler = handler;
      myOperationName = operationName;
    }

    /**
     * {@inheritDoc}
     */
    public void processTerminated(final int exitCode) {
      if (exitCode != 0 && !myHandler.isIgnoredErrorCode(exitCode)) {
        ensureError(exitCode);
        EventQueue.invokeLater(new Runnable() {
          public void run() {
            GitUIUtil.showOperationError(myHandler.project(), myOperationName, getAllErrors());
          }
        });
      }
    }

    /**
     * Ensure that at least one error is available in case if the process exited with non-zero exit code
     *
     * @param exitCode the exit code of the process
     */
    protected void ensureError(final int exitCode) {
      if (myHandler.errors().size() == 0) {
        String text = getErrorText();
        if ((text == null || text.length() == 0) && myHandler.errors().size() == 0) {
          //noinspection ThrowableInstanceNeverThrown
          myHandler.addError(new VcsException(GitBundle.message("git.error.exit", exitCode)));
        }
        else {
          //noinspection ThrowableInstanceNeverThrown
          myHandler.addError(new VcsException(text));
        }
      }
    }

    /**
     * @return a text for all errors in the handler
     */
    protected String getAllErrors() {
      StringBuilder text = new StringBuilder();
      for (VcsException e : myHandler.errors()) {
        if (text.length() > 0) {
          text.append('\n');
        }
        text.append(e.getMessage());
      }
      return text.toString();
    }

    /**
     * @return error text for the handler, if null or empty string a default message is used.
     */
    protected abstract String getErrorText();

    /**
     * {@inheritDoc}
     */
    public void startFailed(final Throwable exception) {
      EventQueue.invokeLater(new Runnable() {
        public void run() {
          GitUIUtil.showOperationError(myHandler.project(), myOperationName, exception.getMessage());
        }
      });
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
     */
    public GitLineHandlerListenerBase(final GitHandler handler, final String operationName) {
      super(handler, operationName);
    }

    /**
     * Error indicators for the line
     */
    @NonNls private static final String[] ERROR_INDICATORS =
      {"ERROR:", "error", "FATAL:", "fatal", "Cannot apply", "Could not", "Interactive rebase already started"};

    /**
     * Check if the line is an error line
     *
     * @param text a line to check
     * @return true if the error line
     */
    protected static boolean isErrorLine(String text) {
      for (String prefix : ERROR_INDICATORS) {
        if (text.startsWith(prefix)) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * A base class for line handler listeners
   */
  private static class GitLineHandlerListenerProgress extends GitLineHandlerListenerBase {
    /**
     * a progress manager to use
     */
    private final ProgressIndicator myProgressIndicator;

    /**
     * A constructor
     *
     * @param manager       the project manager
     * @param handler       a handler instance
     * @param operationName an operation name
     */
    public GitLineHandlerListenerProgress(final ProgressIndicator manager, final GitHandler handler, final String operationName) {
      super(handler, operationName);
      myProgressIndicator = manager;
    }

    /**
     * {@inheritDoc}
     */
    protected String getErrorText() {
      // all lines are already calculated as errors
      return "";
    }

    /**
     * {@inheritDoc}
     */
    public void onLineAvailable(final String line, final Key outputType) {
      if (isErrorLine(line.trim())) {
        //noinspection ThrowableInstanceNeverThrown
        myHandler.addError(new VcsException(line));
      }
      if (myProgressIndicator != null) {
        myProgressIndicator.setText2(line);
      }
    }
  }
}
