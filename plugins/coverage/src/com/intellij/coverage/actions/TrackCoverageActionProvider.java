/*
 * User: anna
 * Date: 28-Aug-2009
 */
package com.intellij.coverage.actions;

import com.intellij.execution.testframework.ToggleModelActionProvider;
import com.intellij.execution.testframework.ToggleModelAction;
import com.intellij.execution.testframework.TestConsoleProperties;

public class TrackCoverageActionProvider implements ToggleModelActionProvider{
  public ToggleModelAction createToggleModelAction(TestConsoleProperties properties) {
    return new TrackCoverageAction(properties);
  }
}