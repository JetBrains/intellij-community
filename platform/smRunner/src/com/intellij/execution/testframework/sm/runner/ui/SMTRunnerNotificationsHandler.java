package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
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
    switch (magnitude) {
      case SKIPPED_INDEX:
      case IGNORED_INDEX:
        msg = "Tests skipped";
        type = MessageType.WARNING;
        break;

      case NOT_RUN_INDEX:
        msg = "Tests were not started";
        type = MessageType.WARNING;
        break;

      case FAILED_INDEX:
      case ERROR_INDEX:
        msg = "Tests failed";
        type = MessageType.ERROR;
        break;
      case COMPLETE_INDEX:
      case PASSED_INDEX:
        msg = "Tests passed";
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