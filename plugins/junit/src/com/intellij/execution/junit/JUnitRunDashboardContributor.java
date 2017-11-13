// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.dashboard.*;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.sm.runner.ui.TestTreeRenderer;
import com.intellij.execution.testframework.sm.runner.ui.TestsPresentationUtil;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PsiNavigateUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Aleev
 */
public class JUnitRunDashboardContributor extends RunDashboardContributor {
  private static final SimpleTextAttributes IGNORE_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ColorProgressBar.YELLOW);
  private static final SimpleTextAttributes ERROR_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ColorProgressBar.RED_TEXT);

  public JUnitRunDashboardContributor() {
    super(JUnitConfigurationType.getInstance());
  }

  @Override
  public void updatePresentation(@NotNull PresentationData presentation, @NotNull RunDashboardNode node) {
    if (!(node instanceof RunDashboardRunConfigurationNode)) return;

    boolean animated = false;
    try {
      RunContentDescriptor descriptor = node.getDescriptor();
      if (descriptor == null) return;

      ExecutionConsole executionConsole = descriptor.getExecutionConsole();
      if (!(executionConsole instanceof SMTRunnerConsoleView)) return;

      SMTestRunnerResultsForm resultsViewer = ((SMTRunnerConsoleView)executionConsole).getResultsViewer();
      animated = resultsViewer.isRunning();

      SMTestProxy.SMRootTestProxy rootNode = resultsViewer.getTestsRootNode();
      TestTreeRenderer renderer = new TestTreeRenderer(resultsViewer.getProperties());
      if (rootNode.isLeaf()) {
        TestsPresentationUtil.formatRootNodeWithoutChildren(rootNode, renderer);
      } else {
        TestsPresentationUtil.formatRootNodeWithChildren(rootNode, renderer);
      }
      if (renderer.getIcon() != null) {
        presentation.setIcon(renderer.getIcon());
      }

      addTestSummary(presentation, rootNode);
    }
    finally {
      RunDashboardAnimator animator = RunDashboardManager.getInstance(node.getProject()).getAnimator();
      if (animator != null) {
        if (animated) {
          animator.addNode(node);
        }
        else {
          animator.removeNode(node);
        }
      }
    }
  }

  private static void addTestSummary(@NotNull PresentationData presentation, @NotNull SMTestProxy.SMRootTestProxy rootNode) {
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
      presentation.addText("failed: " + failed, ERROR_ATTRIBUTES);
      addSeparator = true;
    }
    if (passed > 0 || ignored + failed == 0) {
      if (addSeparator) {
        presentation.addText(", ", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      presentation.addText("passed: " + passed, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addSeparator = true;
    }

    if (ignored > 0) {
      if (addSeparator) {
        presentation.addText(", ", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      presentation.addText("ignored: " + ignored, IGNORE_ATTRIBUTES);
    }

    presentation.addText(" of " + total +"]", SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  @Override
  public boolean handleDoubleClick(@NotNull RunConfiguration runConfiguration) {
    if (!(runConfiguration instanceof JUnitConfiguration)) return false;

    JUnitConfiguration jUnitConfiguration = (JUnitConfiguration)runConfiguration;

    String runClassName = jUnitConfiguration.getRunClass();
    if (runClassName == null) return false;

    PsiClass runClass = jUnitConfiguration.getConfigurationModule().findClass(runClassName);
    if (runClass == null) return false;

    PsiElement psiElement = runClass;
    String testMethod = jUnitConfiguration.getPersistentData().getMethodName();
    if (testMethod != null) {
      PsiMethod[] methods = runClass.findMethodsByName(testMethod, false);
      if (methods.length > 0) {
        psiElement = methods[0];
      }
    }

    PsiNavigateUtil.navigate(psiElement);
    return true;
  }
}
