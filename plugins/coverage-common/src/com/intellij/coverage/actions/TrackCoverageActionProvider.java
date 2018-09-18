package com.intellij.coverage.actions;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.ToggleModelAction;
import com.intellij.execution.testframework.ToggleModelActionProvider;

public class TrackCoverageActionProvider implements ToggleModelActionProvider{
  @Override
  public ToggleModelAction createToggleModelAction(TestConsoleProperties properties) {
    return new TrackCoverageAction(properties);
  }
}