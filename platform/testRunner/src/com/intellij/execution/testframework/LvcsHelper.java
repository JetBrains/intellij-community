/*
 * User: anna
 * Date: 25-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.execution.ExecutionBundle;
import com.intellij.history.LocalHistory;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;

import java.awt.*;

public class LvcsHelper {
  private static final Color RED = new Color(250, 220, 220);
  private static final Color GREEN = new Color(220, 250, 220);

  public static void addLabel(final TestFrameworkRunningModel model) {
    String label;
    int color;

    if (model.getRoot().isDefect()) {
      color = RED.getRGB();
      label = ExecutionBundle.message("junit.runing.info.tests.failed.label");
    }
    else {
      color = GREEN.getRGB();

      if (model.getRoot().getMagnitude() != PoolOfTestStates.PASSED_INDEX) {
        label = ExecutionBundle.message("tests.passed.with.warnings.message");
      }
      else {
        label = ExecutionBundle.message("junit.runing.info.tests.passed.label");
      }
    }
    final TestConsoleProperties consoleProperties = model.getProperties();
    String name = label + " " + consoleProperties.getConfiguration().getName();
    LocalHistory.putSystemLabel(consoleProperties.getProject(), name, color);
  }
}