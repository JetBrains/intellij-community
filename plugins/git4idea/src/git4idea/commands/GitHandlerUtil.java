/*
 * Copyright 2000-2008 JetBrains s.r.o.
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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;

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
   * Execute simple process synchrnously with progress
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
   * Execute simple process synchrnously with progress
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
   * Execute simple process synchrnously with progress
   *
   * @param handler           a handler
   * @param operationTitle    an operation title shown in progress dialog
   * @param operationName     an operation name shown in failure dialog
   * @param configureProgress a flag indicating that progress should be configured as indeterminate
   * @return A exit code
   */
  public static int doSynchronously(final GitLineHandler handler,
                                    String operationTitle,
                                    @NonNls final String operationName,
                                    boolean configureProgress) {
    final ProgressManager manager = ProgressManager.getInstance();
    handler.addLineListener(new GitLineHandlerListenerProgress(manager, handler, operationName));
    runHandlerSynchronously(handler, operationTitle, manager, configureProgress);
    if (!handler.isStarted()) {
      return -1;
    }
    return handler.getExitCode();
  }


  /**
   * Run handler synchronously. The method assumes that all listners are set up.
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
        runInCurrentThread(handler, manager, setIndeterminateFlag);
      }
    }, operationTitle, false, handler.project());
  }

  /**
   * Run handler in the current thread
   *
   * @param handler              a handler to run
   * @param manager              a progress manager
   * @param setIndeterminateFlag if true handler is configured as indeterminate
   */
  private static void runInCurrentThread(final GitHandler handler, final ProgressManager manager, final boolean setIndeterminateFlag) {
    handler.start();
    ProgressIndicator indicator = manager.getProgressIndicator();
    indicator.setText(GitBundle.message("git.running", handler.printCommandLine()));
    if (setIndeterminateFlag) {
      indicator.setIndeterminate(true);
    }
    if (handler.isStarted()) {
      handler.waitFor();
    }
  }

  /**
   * Run synchrnously using progress indicator, but throw an exeption instead of showing error dialog
   *
   * @param handler        a handler to use
   * @param operationTitle a title of the operation
   * @throws VcsException if there is problem with running git operation
   */
  public static void doSynchronouslyWithException(final GitLineHandler handler, String operationTitle) throws VcsException {
    final ProgressManager manager = ProgressManager.getInstance();
    final VcsException[] ex = new VcsException[1];
    handler.addLineListener(new GitLineHandlerListenerProgress(manager, handler, "") {
      @Override
      public void processTerminted(final int exitCode) {
        if (exitCode != 0) {
          String text = getErrorText();
          if (text == null || text.length() == 0) {
            text = GitBundle.message("git.error.exit", exitCode);
          }
          ex[0] = new VcsException(text);
        }
      }

      @Override
      public void startFailed(final Throwable exception) {
        ex[0] = new VcsException("Git start failed: " + exception.toString(), exception);
      }
    });
    runInCurrentThread(handler, manager, false);
    if (ex[0] != null) {
      throw ex[0];
    }
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
    public void processTerminted(final int exitCode) {
      if (exitCode != 0) {
        EventQueue.invokeLater(new Runnable() {
          public void run() {
            String text = getErrorText();
            if (text == null || text.length() == 0) {
              text = GitBundle.message("git.error.exit", exitCode);
            }
            GitUtil.showOperationError(myHandler.project(), myOperationName, text);
          }
        });
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
      EventQueue.invokeLater(new Runnable() {
        public void run() {
          GitUtil.showOperationError(myHandler.project(), myOperationName, exception.getMessage());
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
    @NonNls private static final String[] ERROR_INIDCATORS = {"ERROR:", "error:", "FATAL:", "fatal:"};

    /**
     * Check if the line is an error line
     *
     * @param text a line to check
     * @return true if the error line
     */
    protected static boolean isErrorLine(String text) {
      for (String prefix : ERROR_INIDCATORS) {
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
     * error lines
     */
    final ArrayList<String> errorLines = new ArrayList<String>();
    /**
     * a progress manager to use
     */
    private final ProgressManager myProgressManager;

    /**
     * A constructor
     *
     * @param manager
     * @param handler       a handler instance
     * @param operationName an operation name
     */
    public GitLineHandlerListenerProgress(final ProgressManager manager, final GitHandler handler, final String operationName) {
      super(handler, operationName);
      myProgressManager = manager;
    }

    /**
     * {@inheritDoc}
     */
    protected String getErrorText() {
      StringBuilder builder = new StringBuilder();
      for (String l : errorLines) {
        if (builder.length() > 0) {
          builder.append('\n');
        }
        builder.append(l);
      }
      return builder.toString().trim();
    }

    /**
     * {@inheritDoc}
     */
    public void onLineAvaiable(final String line, final Key outputType) {
      if (isErrorLine(line.trim())) {
        errorLines.add(line);
      }
      final ProgressIndicator pi = myProgressManager.getProgressIndicator();
      if (pi != null) {
        pi.setText2(line);
      }
    }

  }

}
