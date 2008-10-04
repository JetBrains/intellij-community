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
import git4idea.GitBundle;
import git4idea.GitUtil;
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
   * Execute simple process synchrnously
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
    runHandlerSynchronously(handler, operationTitle, ProgressManager.getInstance());
    if (!handler.isStarted() || handler.getExitCode() != 0) {
      return null;
    }
    return handler.getStdout();
  }


  /**
   * Execute simple process synchrnously
   *
   * @param handler        a handler
   * @param operationTitle an operation title shown in progress dialog
   * @param operationName  an operation name shown in failure dialog
   * @return A exit code
   */
  public static int doSynchronously(final GitLineHandler handler, String operationTitle, @NonNls final String operationName) {
    final ProgressManager manager = ProgressManager.getInstance();
    final ArrayList<String> errorLines = new ArrayList<String>();
    handler.addLineListener(new GitLineHandlerListenerBase(handler, operationName) {
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

      public void onLineAvaiable(final String line, final Key outputType) {
        if (isErrorLine(line.trim())) {
          errorLines.add(line);
        }
        final ProgressIndicator pi = manager.getProgressIndicator();
        if (pi != null) {
          pi.setText(line);
        }
      }
    });
    runHandlerSynchronously(handler, operationTitle, manager);
    if (!handler.isStarted()) {
      return -1;
    }
    return handler.getExitCode();
  }

  /**
   * Run handler synchronously. The method assumes that all listners are set up.
   *
   * @param handler        a handler to run
   * @param operationTitle operation title
   * @param manager        a progress manager
   */
  private static void runHandlerSynchronously(final GitHandler handler, final String operationTitle, final ProgressManager manager) {
    manager.runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        handler.start();
        ProgressIndicator indicator = manager.getProgressIndicator();
        indicator.setText(GitBundle.message("git.running", handler.printCommandLine()));
        indicator.setIndeterminate(true);
        if (handler.isStarted()) {
          handler.waitFor();
        }
      }
    }, operationTitle, false, handler.project());
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
  private static abstract class GitLineHandlerListenerBase extends GitHandlerListenerBase implements GitLineHandlerListener {
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
}
