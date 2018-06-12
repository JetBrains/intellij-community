// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public class MockTestResultsViewer implements TestResultsViewer {
  private final TestConsoleProperties myProperties;
  private final SMTestProxy myRootSuite;

  public MockTestResultsViewer(TestConsoleProperties properties, SMTestProxy suite) {
    myProperties = properties;
    myRootSuite = suite;
  }

  @Override
  public SMTestProxy getTestsRootNode() {
    return myRootSuite;
  }

  @Override
  public void selectAndNotify(@Nullable final AbstractTestProxy proxy) {}

  @Override
  public void addEventsListener(final EventsListener listener) {}

  @Override
  public void dispose() {
    Disposer.dispose(myProperties);
  }

  @Override
  public void setShowStatisticForProxyHandler(final PropagateSelectionHandler handler) {}

  @Override
  public void showStatisticsForSelectedProxy() {}
}
