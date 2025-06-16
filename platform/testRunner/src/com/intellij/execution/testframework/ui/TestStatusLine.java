// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.progress.util.ProgressBarUtil;
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
import org.jetbrains.annotations.*;

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

  @TestOnly
  protected SimpleColoredComponent getStateDescription() {
    return myStateDescription;
  }

  private final JLabel myWarning = new JLabel();

  public TestStatusLine() {
    super(new BorderLayout());
    myProgressPanel = new NonOpaquePanel(new BorderLayout());
    add(myProgressPanel, BorderLayout.SOUTH);
    myProgressBar.setMaximum(100);
    myProgressBar.putClientProperty("ProgressBar.stripeWidth", 3);
    myProgressBar.putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.PASSED_VALUE);

    JPanel stateWrapper = new NonOpaquePanel(new GridBagLayout());
    stateWrapper.setBorder(JBUI.Borders.emptyLeft(2));
    final var constraint = new GridBag();
    myState.setOpaque(false);
    stateWrapper.add(myState, constraint.next());
    myStateDescription.setOpaque(false);
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

  /// Updates the test status count.
  /// The number of passed tests is inferred from `finishedTestsCount`, `failuresCount` and `ignoredTestsCount`.
  /// @param testsTotal total amount of tests. -1 if the amount of tests is unknown.
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

    if (testsTotal == 0) {
      testsTotal = finishedTestsCount;
      if (testsTotal == 0) return;
    }

    int passedCount = Math.max(finishedTestsCount - failuresCount - ignoredTestsCount, 0);

    final boolean ongoing = finishedTestsCount != testsTotal;
    final boolean finished = endTime != 0;
    final boolean indefinite = testsTotal < 0;

    if (ongoing && finished) {
      myState.append(TestRunnerBundle.message("test.result.stopped"));
      myState.append(" ");
    }

    if (failuresCount == 0 && ignoredTestsCount == 0) myState.append(TestRunnerBundle.message("test.result.all.passed", finishedTestsCount));
    else if (passedCount == 0 && ignoredTestsCount == 0) appendColored(TestRunnerBundle.message("test.result.all.failed", failuresCount));
    else if (failuresCount == 0 && passedCount == 0) appendColored(TestRunnerBundle.message("test.result.all.ignored", ignoredTestsCount));
    else if (ignoredTestsCount == 0) appendColored(TestRunnerBundle.message("test.result.failed.passed", failuresCount, passedCount));
    else if (passedCount == 0) appendColored(TestRunnerBundle.message("test.result.failed.ignored", failuresCount, ignoredTestsCount));
    else if (failuresCount == 0) appendColored(TestRunnerBundle.message("test.result.passed.ignored", passedCount, ignoredTestsCount));
    else appendColored(TestRunnerBundle.message("test.result.failed.passed.ignored", failuresCount, passedCount, ignoredTestsCount));

    final int count = failuresCount + passedCount + ignoredTestsCount;

    if (ongoing && finished && !indefinite && duration != null) {
      myStateDescription.append(TestRunnerBundle.message("test.result.in.progress.description.and.duration", count, testsTotal, NlsMessages.formatDurationApproximateNarrow(duration)), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    } else if (finished && indefinite && duration != null) {
      myStateDescription.append(TestRunnerBundle.message("test.result.finished.description", count, NlsMessages.formatDurationApproximateNarrow(duration)), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    } else if (ongoing && !indefinite) {
      myStateDescription.append(TestRunnerBundle.message("test.result.in.progress.description", count, testsTotal), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    } else if (!indefinite && duration != null) {
      myStateDescription.append(TestRunnerBundle.message("test.result.finished.description", testsTotal, NlsMessages.formatDurationApproximateNarrow(duration)), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
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
    final var property = myProgressBar.getClientProperty(ProgressBarUtil.STATUS_KEY);
    if (property == null) return null;
    return (String)property;
  }

  public void setStatus(@NotNull String status) {
    myProgressBar.putClientProperty(ProgressBarUtil.STATUS_KEY, status);
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

  @ApiStatus.Internal
  public void showProgressBar() {
    myProgressBar.setVisible(true);
  }

  @ApiStatus.Internal
  public void hideProgressBar() {
    myProgressBar.setVisible(false);
  }

  public void setText(@Nls String progressStatus_text) {
    UIUtil.invokeLaterIfNeeded(() -> {
      myState.clear();
      myState.append(progressStatus_text);
    });
  }

  public @NlsSafe @NotNull String getStateText() {
    return myState.toString();
  }

  @ApiStatus.Internal
  public void setWarning(@Nls @NotNull String suffix) {
    myWarning.setText(suffix);
    updateWarningVisibility();
  }
}
