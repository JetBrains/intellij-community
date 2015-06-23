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
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.ToolbarPanel;

import javax.swing.*;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerToolbarPanel extends ToolbarPanel {
  public SMTRunnerToolbarPanel(final TestConsoleProperties properties,
                               final TestFrameworkRunningModel model, JComponent contentPane) {
    super(properties, contentPane);
    //TODO rerun failed test
    //TODO coverage
    setModel(model);
  }

  @Override
  public void setModel(final TestFrameworkRunningModel model) {
    //TODO: RunningTestTracker - for tracking current test
    super.setModel(model);
  }
}
