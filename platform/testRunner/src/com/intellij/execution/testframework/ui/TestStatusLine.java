// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HtmlToSimpleColoredComponentConverter;
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
import javax.swing.text.html.HTML;
import java.awt.*;
import java.util.function.Supplier;


public class TestStatusLine extends NonOpaquePanel {
  private static final SimpleTextAttributes IGNORED_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Label.warningForeground());
  private static final SimpleTextAttributes FAILED_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Label.errorForeground());
  private final HtmlToSimpleColoredComponentConverter myConverter;

  protected final JProgressBar myProgressBar = new JProgressBar();
  protected final SimpleColoredComponent myState = new SimpleColoredComponent();
  private final SimpleColoredComponent myStateDescription = new SimpleColoredComponent();
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
    myStateDescription.setOpaque(false);
    myStateDescription.setVisible(false);
    stateWrapper.add(myStateDescription, constraint.next().insetLeft(6));

    myWarning.setOpaque(false);
    myWarning.setVisible(false);
    myWarning.setIcon(AllIcons.General.Warning);
    stateWrapper.add(myWarning, constraint.next().insetLeft(12));

    add(stateWrapper, BorderLayout.WEST);
    myState.append(ExecutionBundle.message("junit.runing.info.starting.label"));

    myConverter = new HtmlToSimpleColoredComponentConverter((tag, attr) -> {
      final String className = (String) attr.getAttribute(HTML.Attribute.CLASS);
      if (className == null) return SimpleTextAttributes.REGULAR_ATTRIBUTES;
      return switch (className) {
        case "failed" -> FAILED_ATTRIBUTES;
        case "ignored" -> IGNORED_ATTRIBUTES;
        default -> SimpleTextAttributes.REGULAR_ATTRIBUTES;
      };
    });
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
    myStateDescription.clear();
    myStateDescription.setVisible(false);

    if (testsTotal == 0) {
      testsTotal = finishedTestsCount;
      if (testsTotal == 0) return;
    }

    int passedCount = finishedTestsCount - failuresCount - ignoredTestsCount;
    int failedCount = finishedTestsCount - passedCount - ignoredTestsCount;
    int ignoredCount = finishedTestsCount - failuresCount - passedCount;

    if (finishedTestsCount != testsTotal) {
      final var stopped = endTime != 0;
      if (stopped) {
        myState.append(TestRunnerBundle.message("test.result.stopped"));
        myState.append(" ");
      }

      if (finishedTestsCount == passedCount) myState.append(TestRunnerBundle.message("test.result.in.progress.all.passed", finishedTestsCount, testsTotal));
      else if (finishedTestsCount == failedCount) appendColored(TestRunnerBundle.message("test.result.in.progress.failed", finishedTestsCount, testsTotal, failedCount));
      else if (finishedTestsCount == ignoredCount) appendColored(TestRunnerBundle.message("test.result.in.progress.ignored", finishedTestsCount, testsTotal, ignoredCount));
      else if (ignoredCount == 0) appendColored(TestRunnerBundle.message("test.result.in.progress.failed.passed", finishedTestsCount, testsTotal, failedCount, passedCount));
      else if (passedCount == 0) appendColored(TestRunnerBundle.message("test.result.in.progress.failed.ignored", finishedTestsCount, testsTotal, failedCount, ignoredCount));
      else if (failedCount == 0) appendColored(TestRunnerBundle.message("test.result.in.progress.passed.ignored", finishedTestsCount, testsTotal, passedCount, ignoredCount));
      else appendColored(TestRunnerBundle.message("test.result.in.progress.failed.passed.ignored", finishedTestsCount, testsTotal, failedCount, passedCount, ignoredCount));

      if (stopped && duration != null) {
        myStateDescription.setVisible(true);
        myStateDescription.append(NlsMessages.formatDurationApproximateNarrow(duration), SimpleTextAttributes.GRAY_ATTRIBUTES);
      }

      return;
    }

    if (finishedTestsCount == passedCount) myState.append(TestRunnerBundle.message("test.result.finished.all.passed"));
    else if (finishedTestsCount == failedCount) myState.append(TestRunnerBundle.message("test.result.finished.all.failed"), FAILED_ATTRIBUTES);
    else if (finishedTestsCount == ignoredCount) myState.append(TestRunnerBundle.message("test.result.finished.all.ignored"), IGNORED_ATTRIBUTES);
    else if (ignoredCount == 0) appendColored(TestRunnerBundle.message("test.result.finished.failed.passed", failedCount, passedCount));
    else if (passedCount == 0) appendColored(TestRunnerBundle.message("test.result.finished.failed.ignored", failedCount, ignoredCount));
    else if (failedCount == 0) appendColored(TestRunnerBundle.message("test.result.finished.passed.ignored", passedCount, ignoredCount));
    else appendColored(TestRunnerBundle.message("test.result.finished.failed.passed.ignored", failedCount, passedCount, ignoredCount));

    if (duration == null) return;
    myStateDescription.setVisible(true);
    @NlsSafe String fragment = TestRunnerBundle.message("test.result.finished.description", testsTotal, NlsMessages.formatDurationApproximateNarrow(duration));
    myStateDescription.append(fragment, SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  private void appendColored(@Nls String text) {
    myConverter
      .convert(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      .forEach(frag -> myState.append(frag.getText(), frag.getAttributes()));
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
