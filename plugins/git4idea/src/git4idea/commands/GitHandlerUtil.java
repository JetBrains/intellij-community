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

import com.intellij.openapi.progress.ProgressManager;
import git4idea.GitUtil;

import java.awt.*;

/**
 * Handler utilities
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
  public static String doSynchronously(final GitSimpleHandler handler, String operationTitle, final String operationName) {
    handler.addListener(new GitHandlerListener() {
      public void processTerminted(final int exitCode) {
        if (exitCode != 0) {
          EventQueue.invokeLater(new Runnable() {
            public void run() {
              String text = handler.getStderr();
              if (text.length() == 0) {
                text = handler.getStdout();
              }
              if (text.length() == 0) {
                text = "The git process exited with the code " + exitCode;
              }
              GitUtil.showOperationError(handler.project(), operationName, text);
            }
          });
        }
      }

      public void startFailed(final Throwable exception) {
        EventQueue.invokeLater(new Runnable() {
          public void run() {
            GitUtil.showOperationError(handler.project(), exception.getMessage(), operationName);
          }
        });
      }
    });
    ProgressManager manager = ProgressManager.getInstance();
    manager.runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        handler.start();
        if (handler.isStarted()) {
          handler.waitFor();
        }
      }
    }, operationTitle, false, handler.project());
    if (!handler.isStarted() || handler.getExitCode() != 0) {
      return null;
    }
    return handler.getStdout();
  }
}
