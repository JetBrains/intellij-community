// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.testframework;

import com.intellij.execution.ExecutionBundle;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;

import java.awt.*;

public final class LvcsHelper {
  private static final Color RED = new JBColor(new Color(250, 220, 220), new Color(104, 67, 67));
  private static final Color GREEN = new JBColor(new Color(220, 250, 220), new Color(44, 66, 60));

  public static void addLabel(final TestFrameworkRunningModel model) {
    String name;
    int color;

    AbstractTestProxy root = model.getRoot();

    if (root.isInterrupted()) return;

    TestConsoleProperties consoleProperties = model.getProperties();
    String configName = consoleProperties.getConfiguration().getName();

    if (root.isPassed() || root.isIgnored()) {
      color = GREEN.getRGB();
      name = ExecutionBundle.message("junit.running.info.tests.passed.with.test.name.label", configName);
    }
    else {
      color = RED.getRGB();
      name = ExecutionBundle.message("junit.running.info.tests.failed.with.test.name.label", configName);
    }

    Project project = consoleProperties.getProject();
    if (project.isDisposed()) return;

    LocalHistory.getInstance().putSystemLabel(project, name, color);
  }
}