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

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.ui.tabs.JBTabs;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Roman Chernyatchik
 */
public class MockTestResultsViewer implements TestResultsViewer {
  private final TestConsoleProperties myProperties;
  private final SMTestProxy myRootSuite;

  public MockTestResultsViewer(final TestConsoleProperties properties,
                               final SMTestProxy suite) {
    myProperties = properties;
    myRootSuite = suite;
  }

  public void addTab(final String name, @Nullable final String tooltip, final Icon icon, final JComponent contentPane) {}

  @Nullable
  public JComponent getContentPane() {
    return null;
  }

  @Override
  public SMTestProxy getTestsRootNode() {
    return myRootSuite;
  }

  @Override
  public void selectAndNotify(@Nullable final AbstractTestProxy proxy) {}

  @Override
  public void addEventsListener(final EventsListener listener) {}

  public JBTabs getTabs() { return null; }


  @Override
  public void dispose() {
    myProperties.dispose();
  }

  @Override
  public void setShowStatisticForProxyHandler(final PropagateSelectionHandler handler) {}

  @Override
  public void showStatisticsForSelectedProxy() {}
}
