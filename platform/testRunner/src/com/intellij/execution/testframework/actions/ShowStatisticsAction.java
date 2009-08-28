package com.intellij.execution.testframework.actions;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.util.config.ToggleBooleanProperty;

/**
 * @author yole
 */
public class ShowStatisticsAction extends ToggleBooleanProperty {
  public ShowStatisticsAction(TestConsoleProperties properties) {
    super("Show Statistics", "Toggle the visibility of the test statistics panel",
          null, properties, TestConsoleProperties.SHOW_STATISTICS);
  }
}
