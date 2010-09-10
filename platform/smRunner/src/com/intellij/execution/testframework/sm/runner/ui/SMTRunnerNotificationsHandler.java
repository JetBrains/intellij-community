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
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerNotificationsHandler extends SMTRunnerEventsAdapter {
  private final TestConsoleProperties myConsoleProperties;
  //private boolean myFirstDefectWasFound;

  public SMTRunnerNotificationsHandler(@NotNull final TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  public void onTestingStarted(@NotNull SMTestProxy testsRoot) {
    //myFirstDefectWasFound = false;
  }

  public void onTestingFinished(@NotNull SMTestProxy testsRoot) {
    final String msg;
    final MessageType type;

    final TestStateInfo.Magnitude magnitude = testsRoot.getMagnitudeInfo();
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (magnitude) {
      case SKIPPED_INDEX:
      case IGNORED_INDEX:
        msg = testsRoot.hasErrors() ? SMTestsRunnerBundle.message("sm.test.runner.notifications.tests.skipped.with.errors")
                                    : SMTestsRunnerBundle.message("sm.test.runner.notifications.tests.skipped");

        type = MessageType.WARNING;
        break;

      case NOT_RUN_INDEX:
        msg = testsRoot.hasErrors() ? SMTestsRunnerBundle.message("sm.test.runner.notifications.tests.not.run.with.errors")
                                    : SMTestsRunnerBundle.message("sm.test.runner.notifications.tests.not.run");
        type = MessageType.WARNING;
        break;

      case FAILED_INDEX:
      case ERROR_INDEX:
        msg = testsRoot.hasErrors() ? SMTestsRunnerBundle.message("sm.test.runner.notifications.tests.failed.with.errors")
                                    : SMTestsRunnerBundle.message("sm.test.runner.notifications.tests.failed");
        type = MessageType.ERROR;
        break;
      case COMPLETE_INDEX:
        if (testsRoot.getChildren().size() == 0) {
          msg = testsRoot.hasErrors() ? SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.no.tests.were.found.with.errors")
                                      : SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.no.tests.were.found");
          type = MessageType.ERROR;
          break;
        } else if (testsRoot.isEmptySuite()) {
          msg = SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.empty.test.suite");
          type = MessageType.WARNING;
          break;
        }
        // else same as: PASSED_INDEX 
      case PASSED_INDEX:
        msg = testsRoot.hasErrors() ? SMTestsRunnerBundle.message("sm.test.runner.notifications.tests.passed.with.errors")
                                    : SMTestsRunnerBundle.message("sm.test.runner.notifications.tests.passed");
        type = MessageType.INFO;
        break;

      default:
        msg = null;
        type = null;
    }

    if (msg != null) {
      notify(msg, type);
    }
  }

  public void onTestFailed(@NotNull SMTestProxy test) {
    // TODO : if user doesn't close this balloon then user will not see 'tests failed' balloon
    //if (!myFirstDefectWasFound) {
    //  // notify about defect on the fly
    //  if (test.isDefect()) {
    //    final TestStateInfo.Magnitude magnitude = test.getMagnitudeInfo();
    //    //noinspection EnumSwitchStatementWhichMissesCases
    //    switch (magnitude) {
    //      case FAILED_INDEX:
    //      case ERROR_INDEX:
    //        myFirstDefectWasFound = true;
    //        notify("Tests will fail", MessageType.WARNING);
    //        break;
    //      default:
    //        // Do nothing
    //    }
    //  }
    //}
  }

  private void notify(final String msg, final MessageType type) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        final Project project = myConsoleProperties.getProject();
        if ( project.isDisposed()) {
          return;
        }

        if (myConsoleProperties == null) {
          return;
        }
        final String testRunDebugId = myConsoleProperties.isDebug() ? ToolWindowId.DEBUG : ToolWindowId.RUN;
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        if (!Comparing.strEqual(toolWindowManager.getActiveToolWindowId(), testRunDebugId)) {
          toolWindowManager.notifyByBalloon(testRunDebugId, type, msg, null, null);
        }
      }
    });
  }
}