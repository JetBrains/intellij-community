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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Handler utilities that allow running handlers with progress indicators
 */
public class GitHandlerUtil {
  /**
   * The logger instance
   */
  private static final Logger LOG = Logger.getInstance(GitHandlerUtil.class.getName());

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
   * @return An exit code
   */
  public static int doSynchronously(final GitLineHandler handler, String operationTitle, @NonNls final String operationName) {
    return doSynchronously(handler, operationTitle, operationName, true);
  }

  /**
   * Execute simple process synchronously with progress
   *
   * @param handler        a handler
   * @param operationTitle an operation title shown in progress dialog
   * @param operationName  an operation name shown in failure dialog
   * @param showErrors     if true, the errors are shown when process is terminated
   * @return An exit code
   */
  public static int doSynchronously(final GitLineHandler handler,
                                    String operationTitle,
                                    @NonNls final String operationName,
                                    boolean showErrors) {
    return doSynchronously(handler, operationTitle, operationName, showErrors, true);
  }


  /**
   * Execute simple process synchronously with progress
   *
   * @param handler              a handler
   * @param operationTitle       an operation title shown in progress dialog
   * @param operationName        an operation name shown in failure dialog
   * @param showErrors           if true, the errors are shown when process is terminated
   * @param setIndeterminateFlag a flag indicating that progress should be configured as indeterminate
   * @return An exit code
   */
  public static int doSynchronously(final GitLineHandler handler,
                                    final String operationTitle,
                                    @NonNls final String operationName,
                                    final boolean showErrors,
                                    final boolean setIndeterminateFlag) {
    final ProgressManager manager = ProgressManager.getInstance();
    manager.run(new Task.Modal(handler.project(), operationTitle, false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        handler.addLineListener(new GitLineHandlerListenerProgress(indicator, handler, operationName, showErrors));
        runInCurrentThread(handler, indicator, setIndeterminateFlag, operationTitle);
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
        runInCurrentThread(handler, manager.getProgressIndicator(), setIndeterminateFlag,
                           operationTitle);
      }
    }, operationTitle, false, handler.project());
  }

  /**
   * Run handler in the current thread
   *
   * @param handler              a handler to run
   * @param indicator            a progress manager
   * @param setIndeterminateFlag if true handler is configured as indeterminate
   * @param operationName
   */
  private static void runInCurrentThread(final GitHandler handler,
                                         final ProgressIndicator indicator,
                                         final boolean setIndeterminateFlag,
                                         @Nullable final String operationName) {
    runInCurrentThread(handler, new Runnable() {
      public void run() {
        if (indicator != null) {
          indicator.setText(operationName == null ? GitBundle.message("git.running", handler.printableCommandLine()) : operationName);
          indicator.setText2("");
          if (setIndeterminateFlag) {
            indicator.setIndeterminate(true);
          }
        }
      }
    });
  }

  /**
   * Run handler in the current thread
   *
   * @param handler         a handler to run
   * @param postStartAction an action that is executed
   */
  static void runInCurrentThread(final GitHandler handler, @Nullable final Runnable postStartAction) {
    boolean suspendable = false;
    switch (handler.myCommand.lockingPolicy()) {
      case META:
        // do nothing no locks are taken for metadata
        break;
      case READ:
        handler.myVcs.getCommandLock().readLock().lock();
        break;
      case WRITE_SUSPENDABLE:
        suspendable = true;
        //noinspection fallthrough
      case WRITE:
        handler.myVcs.getCommandLock().writeLock().lock();
        break;
    }
    try {
      if (suspendable) {
        final Object EXIT = new Object();
        final Object SUSPEND = new Object();
        final Object RESUME = new Object();
        final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
        Runnable suspend = new Runnable() {
          public void run() {
            queue.add(SUSPEND);
          }
        };
        Runnable resume = new Runnable() {
          public void run() {
            queue.add(RESUME);
          }
        };
        handler.setSuspendResume(suspend, resume);
        handler.start();
        if (handler.isStarted()) {
          if (postStartAction != null) {
            postStartAction.run();
          }
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
              handler.waitFor();
              queue.add(EXIT);
            }
          });
          boolean suspended = false;
          while (true) {
            Object action;
            while (true) {
              try {
                action = queue.take();
                break;
              }
              catch (InterruptedException e) {
                if (LOG.isDebugEnabled()) {
                  LOG.debug("queue.take() is interrupted", e);
                }
              }
            }
            if (action == EXIT) {
              if (suspended) {
                LOG.error("Exiting while RW lock is suspended (reacquiring W-lock command)");
                handler.myVcs.getCommandLock().writeLock().lock();
              }
              break;
            }
            else if (action == SUSPEND) {
              if (suspended) {
                LOG.error("Suspending suspended W-lock (ignoring command)");
              }
              else {
                handler.myVcs.getCommandLock().writeLock().unlock();
                suspended = true;
              }
            }
            else if (action == RESUME) {
              if (!suspended) {
                LOG.error("Resuming not suspended W-lock (ignoring command)");
              }
              else {
                handler.myVcs.getCommandLock().writeLock().lock();
                suspended = false;
              }
            }
          }
        }
      }
      else {
        handler.start();
        if (handler.isStarted()) {
          if (postStartAction != null) {
            postStartAction.run();
          }
          handler.waitFor();
        }
      }
    }
    finally {
      switch (handler.myCommand.lockingPolicy()) {
        case META:
          // do nothing no locks are taken for metadata
          break;
        case READ:
          handler.myVcs.getCommandLock().readLock().unlock();
          break;
        case WRITE_SUSPENDABLE:
        case WRITE:
          handler.myVcs.getCommandLock().writeLock().unlock();
          break;
      }
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
    return doSynchronouslyWithExceptions(handler, progressIndicator, null);
  }

  /**
   * Run synchronously using progress indicator, but collect exception instead of showing error dialog
   *
   * @param handler           a handler to use
   * @param progressIndicator a progress indicator
   * @param operationName
   * @return the collection of exception collected during operation
   */
  public static Collection<VcsException> doSynchronouslyWithExceptions(final GitLineHandler handler,
                                                                       final ProgressIndicator progressIndicator,
                                                                       @Nullable String operationName) {
    handler.addLineListener(new GitLineHandlerListenerProgress(progressIndicator, handler, operationName, false));
    runInCurrentThread(handler, progressIndicator, false, operationName);
    return handler.errors();
  }

  public static String formatOperationName(String operation, @NotNull VirtualFile root) {
    return operation + " '" + root.getName() + "'...";
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
    public GitHandlerListenerBase(final GitHandler handler, final String operationName) {
      this(handler, operationName, true);
    }

    /**
     * A constructor
     *
     * @param handler       a handler instance
     * @param operationName an operation name
     * @param showErrors    if true, the errors are shown when process is terminated
     */
    public GitHandlerListenerBase(final GitHandler handler, final String operationName, boolean showErrors) {
      myHandler = handler;
      myOperationName = operationName;
      myShowErrors = showErrors;
    }

    /**
     * {@inheritDoc}
     */
    public void processTerminated(final int exitCode) {
      if (exitCode != 0 && !myHandler.isIgnoredErrorCode(exitCode)) {
        ensureError(exitCode);
        if (myShowErrors) {
          EventQueue.invokeLater(new Runnable() {
            public void run() {
              GitUIUtil.showOperationErrors(myHandler.project(), myHandler.errors(), myOperationName);
            }
          });
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
     * @return error text for the handler, if null or empty string a default message is used.
     */
    protected abstract String getErrorText();

    /**
     * {@inheritDoc}
     */
    public void startFailed(final Throwable exception) {
      //noinspection ThrowableInstanceNeverThrown
      myHandler.addError(new VcsException("Git start failed: " + exception.getMessage(), exception));
      if (myShowErrors) {
        EventQueue.invokeLater(new Runnable() {
          public void run() {
            GitUIUtil.showOperationError(myHandler.project(), myOperationName, exception.getMessage());
          }
        });
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
    public GitLineHandlerListenerBase(GitHandler handler, String operationName, boolean showErrors) {
      super(handler, operationName, showErrors);
    }

    /**
     * Error indicators for the line
     */
    @NonNls private static final String[] ERROR_INDICATORS =
      {"ERROR:", "error", "FATAL:", "fatal", "Cannot apply", "Could not", "Interactive rebase already started", "refusing to pull",
        "cannot rebase:"};

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
     * @param showErrors    if true, the errors are shown when process is terminated
     */
    public GitLineHandlerListenerProgress(final ProgressIndicator manager, GitHandler handler, String operationName, boolean showErrors) {
      super(handler, operationName, showErrors);    //To change body of overridden methods use File | Settings | File Templates.
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
