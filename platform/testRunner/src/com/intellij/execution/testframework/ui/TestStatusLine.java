/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.testframework.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.testframework.TestIconMapper;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class TestStatusLine extends NonOpaquePanel {
  private static final SimpleTextAttributes IGNORE_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ColorProgressBar.YELLOW);
  private static final SimpleTextAttributes ERROR_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ColorProgressBar.RED_TEXT);

  protected final JProgressBar myProgressBar = new JProgressBar();
  protected final SimpleColoredComponent myState = new SimpleColoredComponent();
  private final JPanel myProgressPanel;

  public TestStatusLine() {
    super(new BorderLayout());
    myProgressPanel = new NonOpaquePanel(new BorderLayout());
    add(myProgressPanel, BorderLayout.SOUTH);
    myProgressBar.setMaximum(100);
    myProgressBar.putClientProperty("ProgressBar.stripeWidth", 3);
    myProgressBar.putClientProperty("ProgressBar.flatEnds", Boolean.TRUE);
    setStatusColor(ColorProgressBar.GREEN);
    JPanel stateWrapper = new NonOpaquePanel(new BorderLayout());
    myState.setOpaque(false);
    stateWrapper.add(myState, BorderLayout.NORTH);
    add(stateWrapper, BorderLayout.CENTER);
    myState.append(ExecutionBundle.message("junit.runing.info.starting.label"));
  }

  public void formatTestMessage(int testsTotal,
                                final int finishedTestsCount,
                                final int failuresCount,
                                final int ignoredTestsCount,
                                final Long duration,
                                final long endTime) {
    myState.clear();
    if (testsTotal == 0) {
      testsTotal = finishedTestsCount + failuresCount + ignoredTestsCount;
      if (testsTotal == 0) return;
    }
    int passedCount = finishedTestsCount - failuresCount - ignoredTestsCount;
    if (duration == null || endTime == 0) {
      //running tests
      formatCounts(failuresCount, ignoredTestsCount, passedCount, testsTotal);
      return;
    }

    //finished tests
    boolean stopped = finishedTestsCount != testsTotal;
    if (stopped) {
      myState.append("Stopped. ");
    }

    formatCounts(failuresCount, ignoredTestsCount, passedCount, testsTotal);

    myState.append(" – " + StringUtil.formatDuration(duration, "\u2009"), SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  private void formatCounts(int failuresCount, int ignoredTestsCount, int passedCount, int testsTotal) {
    boolean something = false;
    if (failuresCount > 0) {
      myState.append("Tests failed: " + failuresCount, ERROR_ATTRIBUTES);
      something = true;
    }
    else {
      myState.append("Tests ");
    }

    if (passedCount > 0 || ignoredTestsCount + failuresCount == 0) {
      if (something) {
        myState.append(", ");
      }
      something = true;
      myState.append("passed: " + passedCount);
    }

    if (ignoredTestsCount > 0) {
      if (something) {
        myState.append(", ");
      }
      myState.append("ignored: " + ignoredTestsCount, IGNORE_ATTRIBUTES);
    }

    if (testsTotal > 0) {
      myState.append(" of " + getTestsTotalMessage(testsTotal), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  public void setIndeterminate(boolean flag) {
    myProgressPanel.add(myProgressBar, BorderLayout.NORTH);
    myProgressBar.setIndeterminate(flag);
  }

  public void onTestsDone(@Nullable TestStateInfo.Magnitude info) {
    myProgressPanel.remove(myProgressBar);
    if (info != null) {
      myState.setIcon(TestIconMapper.getIcon(info));
    }
  }

  private static String getTestsTotalMessage(int testsTotal) {
    return testsTotal + " test" + (testsTotal > 1 ? "s" : "");
  }

  public void setStatusColor(Color color) {
    myProgressBar.setForeground(color);
  }

  public Color getStatusColor() {
    return myProgressBar.getForeground();
  }

  public void setFraction(double v) {
    int fraction = (int)(v * 100);
    myProgressBar.setValue(fraction);
  }

  /**
   * Usages should be deleted as progress is now incorporated into console
   */
  @Deprecated
  public void setPreferredSize(boolean orientation) {
    final Dimension size = new JBDimension(orientation ? 150 : 450 , -1);
    myProgressPanel.setMaximumSize(size);
    myProgressPanel.setMinimumSize(size);
    myProgressPanel.setPreferredSize(size);
  }
  
  public void setText(String progressStatus_text) {
    myState.clear();
    myState.append(progressStatus_text);
  }
}
