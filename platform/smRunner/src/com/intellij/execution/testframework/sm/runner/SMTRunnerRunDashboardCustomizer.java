// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.dashboard.RunDashboardCustomizationBuilder;
import com.intellij.execution.dashboard.RunDashboardCustomizer;
import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.sm.runner.ui.TestTreeRenderer;
import com.intellij.execution.testframework.sm.runner.ui.TestsPresentationUtil;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class SMTRunnerRunDashboardCustomizer extends RunDashboardCustomizer {
  private static final SimpleTextAttributes IGNORE_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ColorProgressBar.YELLOW);
  private static final SimpleTextAttributes ERROR_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ColorProgressBar.RED_TEXT);

  @Override
  public boolean isApplicable(@NotNull RunnerAndConfigurationSettings settings, @Nullable RunContentDescriptor descriptor) {
    return descriptor != null && descriptor.getExecutionConsole() instanceof SMTRunnerConsoleView;
  }

  @Override
  public boolean updatePresentation(@NotNull RunDashboardCustomizationBuilder customizationBuilder,
                                    @NotNull RunnerAndConfigurationSettings settings,
                                    @Nullable RunContentDescriptor descriptor) {
    if (descriptor == null) return false;

    ExecutionConsole executionConsole = descriptor.getExecutionConsole();
    if (!(executionConsole instanceof SMTRunnerConsoleView)) return false;

    SMTestRunnerResultsForm resultsViewer = ((SMTRunnerConsoleView)executionConsole).getResultsViewer();

    // todo: consider reimplementing manual update logic for test runner, the old one did not work and prevented from further refactorings
    SMTestProxy.SMRootTestProxy rootNode = resultsViewer.getTestsRootNode();
    TestTreeRenderer renderer = new TestTreeRenderer(resultsViewer.getProperties());
    if (rootNode.isLeaf()) {
      TestsPresentationUtil.formatRootNodeWithoutChildren(rootNode, renderer);
    }
    else {
      TestsPresentationUtil.formatRootNodeWithChildren(rootNode, renderer);
    }
    if (renderer.getIcon() != null) {
      customizationBuilder.setIcon(renderer.getIcon());
    }

    addTestSummary(customizationBuilder, rootNode);
    return true;
  }

  private static void addTestSummary(@NotNull RunDashboardCustomizationBuilder presentation, @NotNull SMTestProxy.SMRootTestProxy rootNode) {
    // Do not add any summary if test tree is not constructed.
    if (rootNode.isLeaf()) return;

    TestsUIUtil.TestResultPresentation testResultPresentation = new TestsUIUtil.TestResultPresentation(rootNode).getPresentation();

    int failed = testResultPresentation.getFailedCount();
    int ignored = testResultPresentation.getIgnoredCount();
    int passed = testResultPresentation.getPassedCount();
    int total = passed + failed + testResultPresentation.getNotStartedCount();

    if (total == 0) return;

    presentation.addText(" [", SimpleTextAttributes.GRAY_ATTRIBUTES);
    boolean addSeparator = false;
    if (failed > 0) {
      presentation.addText(TestRunnerBundle.message("tests.result.failed.count", failed), ERROR_ATTRIBUTES);
      addSeparator = true;
    }
    if (passed > 0 || ignored + failed == 0) {
      if (addSeparator) {
        presentation.addText(", ", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      presentation.addText(TestRunnerBundle.message("tests.result.passed.count", passed), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addSeparator = true;
    }

    if (ignored > 0) {
      if (addSeparator) {
        presentation.addText(", ", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      presentation.addText(TestRunnerBundle.message("tests.result.ignored.count", ignored), IGNORE_ATTRIBUTES);
    }

    presentation.addText(TestRunnerBundle.message("tests.result.total.count", total) + "]", SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }
}
