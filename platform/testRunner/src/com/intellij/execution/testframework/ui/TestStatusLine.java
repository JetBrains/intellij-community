// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;


public class TestStatusLine extends NonOpaquePanel {
  private static final SimpleTextAttributes IGNORE_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ColorProgressBar.YELLOW);
  private static final SimpleTextAttributes ERROR_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ColorProgressBar.RED_TEXT);

  protected final JProgressBar myProgressBar = new JProgressBar();
  protected final SimpleColoredComponent myState = new SimpleColoredComponent();
  private final JPanel myProgressPanel;
  private final JLabel myWarning = new JLabel();

  public TestStatusLine() {
    super(new BorderLayout());
    myProgressPanel = new NonOpaquePanel(new BorderLayout());
    add(myProgressPanel, BorderLayout.SOUTH);
    myProgressBar.setMaximum(100);
    myProgressBar.putClientProperty("ProgressBar.stripeWidth", 3);
    myProgressBar.putClientProperty(JBUI.CurrentTheme.ProgressBar.statusKey(), JBUI.CurrentTheme.ProgressBar.passedStatusValue());

    JPanel stateWrapper = new NonOpaquePanel(new GridBagLayout());
    stateWrapper.setBorder(JBUI.Borders.emptyLeft(2));
    final var constraint = new GridBag();
    myState.setOpaque(false);
    stateWrapper.add(myState, constraint.next());

    myWarning.setOpaque(false);
    myWarning.setVisible(false);
    myWarning.setIcon(AllIcons.General.Warning);
    myWarning.setBorder(JBUI.Borders.emptyLeft(12));
    stateWrapper.add(myWarning, constraint.next());

    add(stateWrapper, BorderLayout.WEST);
    myState.append(ExecutionBundle.message("junit.runing.info.starting.label"));
  }

  public void formatTestMessage(final int testsTotal,
                                final int finishedTestsCount,
                                final int failuresCount,
                                final int ignoredTestsCount,
                                final Long duration,
                                final long endTime) {
    UIUtil.invokeLaterIfNeeded(() -> {
      doFormatTestMessage(testsTotal, finishedTestsCount, failuresCount, ignoredTestsCount, duration, endTime);
      updateWarningVisibility();
    });
  }

  private void updateWarningVisibility() {
    myWarning.setVisible(!myState.getCharSequence(false).isEmpty() && StringUtil.isNotEmpty(myWarning.getText()));
  }

  private void doFormatTestMessage(int testsTotal,
                                   int finishedTestsCount,
                                   int failuresCount,
                                   int ignoredTestsCount,
                                   Long duration,
                                   long endTime) {
    myState.clear();
    if (testsTotal == 0) {
      testsTotal = finishedTestsCount;
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
      myState.append(TestRunnerBundle.message("test.stopped") + " ");
    }

    formatCounts(failuresCount, ignoredTestsCount, passedCount, testsTotal);

    @NlsSafe String fragment = " – " + NlsMessages.formatDurationApproximateNarrow(duration);
    myState.append(fragment, SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  private void formatCounts(int failuresCount, int ignoredTestsCount, int passedCount, int testsTotal) {
    boolean something = false;
    if (failuresCount > 0) {
      myState.append(TestRunnerBundle.message("tests.result.prefix") + " ", ERROR_ATTRIBUTES);
      myState.append(TestRunnerBundle.message("tests.result.failed.count", failuresCount), ERROR_ATTRIBUTES);
      something = true;
    }
    else {
      myState.append(TestRunnerBundle.message("tests.result.prefix")+" ");
    }

    if (passedCount > 0 || ignoredTestsCount + failuresCount == 0) {
      if (something) {
        myState.append(", ");
      }
      something = true;
      myState.append(TestRunnerBundle.message("tests.result.passed.count", passedCount));
    }

    if (ignoredTestsCount > 0) {
      if (something) {
        myState.append(", ");
      }
      myState.append(TestRunnerBundle.message("tests.result.ignored.count", ignoredTestsCount), IGNORE_ATTRIBUTES);
    }

    if (testsTotal > 0) {
      myState.append(TestRunnerBundle.message("tests.result.total.count", testsTotal), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  public void setIndeterminate(boolean flag) {
    myProgressPanel.add(myProgressBar, BorderLayout.NORTH);
    myProgressBar.setIndeterminate(flag);
  }

  public void onTestsDone(@Nullable Supplier<? extends Icon> toolbarIconSupplier) {
    EdtInvocationManager.getInstance().invokeLater(() -> {
      if (toolbarIconSupplier != null) {
        myState.setIcon(toolbarIconSupplier.get());
      }
    });
  }

  @ApiStatus.Internal
  public String getStatus() {
    final var property = myProgressBar.getClientProperty(JBUI.CurrentTheme.ProgressBar.statusKey());
    if (property == null) return null;
    return (String)property;
  }

  public void setStatus(@NotNull String status) {
    myProgressBar.putClientProperty(JBUI.CurrentTheme.ProgressBar.statusKey(), status);
  }

  /**
   * @deprecated Use {@link #setStatus(String)} with values from {@link JBUI.CurrentTheme.ProgressBar}.
   */
  @Deprecated
  public void setStatusColor(Color color) {
    myProgressBar.setForeground(color);
  }

  /**
   * @deprecated Use {@link #getStatus()} instead.
   */
  @Deprecated
  public Color getStatusColor() {
    return myProgressBar.getForeground();
  }

  public void setFraction(double v) {
    int fraction = (int)(v * 100);
    myProgressBar.setValue(fraction);
  }

  public void setText(@Nls String progressStatus_text) {
    UIUtil.invokeLaterIfNeeded(() -> {
      myState.clear();
      myState.append(progressStatus_text);
      myWarning.setVisible(!progressStatus_text.isEmpty());
    });
  }

  @NlsSafe
  @NotNull
  public String getStateText() {
    return myState.toString();
  }

  @ApiStatus.Internal
  public void setWarning(@Nls @NotNull String suffix) {
    myWarning.setText(suffix);
    updateWarningVisibility();
  }
}
