// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.dashboard.RunDashboardCustomizer;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.execution.testframework.sm.runner.ui.*;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.reference.SoftReference;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

public class SMTRunnerRunDashboardCustomizer extends RunDashboardCustomizer {
  private static final SimpleTextAttributes IGNORE_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ColorProgressBar.YELLOW);
  private static final SimpleTextAttributes ERROR_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ColorProgressBar.RED_TEXT);
  private static final Key<NodeUpdaterEventsListener> EVENTS_LISTENER_KEY = Key.create("TestResultsEventsListener");

  @Override
  public boolean isApplicable(@NotNull RunnerAndConfigurationSettings settings, @Nullable RunContentDescriptor descriptor) {
    return descriptor != null && descriptor.getExecutionConsole() instanceof SMTRunnerConsoleView;
  }

  @Override
  public boolean updatePresentation(@NotNull PresentationData presentation, @NotNull RunDashboardRunConfigurationNode node) {
    RunContentDescriptor descriptor = node.getDescriptor();
    if (descriptor == null) return false;

    ExecutionConsole executionConsole = descriptor.getExecutionConsole();
    if (!(executionConsole instanceof SMTRunnerConsoleView)) return false;

    SMTestRunnerResultsForm resultsViewer = ((SMTRunnerConsoleView)executionConsole).getResultsViewer();
    Content content = node.getContent();
    if (content != null && node instanceof AbstractTreeNode<?>) {
      NodeUpdaterEventsListener eventsListener = content.getUserData(EVENTS_LISTENER_KEY);
      if (eventsListener == null) {
        eventsListener = new NodeUpdaterEventsListener();
        resultsViewer.addEventsListener(eventsListener);
        content.putUserData(EVENTS_LISTENER_KEY, eventsListener);
        NodeUpdaterEventsListener listener = eventsListener;
        Disposer.register(resultsViewer, () -> {
          if (content.getUserData(EVENTS_LISTENER_KEY) == listener) {
            content.putUserData(EVENTS_LISTENER_KEY, null);
          }
        });
      }
      eventsListener.setNode((AbstractTreeNode<?>)node);
    }

    SMTestProxy.SMRootTestProxy rootNode = resultsViewer.getTestsRootNode();
    TestTreeRenderer renderer = new TestTreeRenderer(resultsViewer.getProperties());
    if (rootNode.isLeaf()) {
      TestsPresentationUtil.formatRootNodeWithoutChildren(rootNode, renderer);
    }
    else {
      TestsPresentationUtil.formatRootNodeWithChildren(rootNode, renderer);
    }
    if (renderer.getIcon() != null) {
      presentation.setIcon(renderer.getIcon());
    }

    addTestSummary(presentation, rootNode);
    return true;
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

  private static class NodeUpdaterEventsListener implements TestResultsViewer.EventsListener {
    private WeakReference<AbstractTreeNode<?>> myNodeReference;

    @Override
    public void onTestingFinished(@NotNull TestResultsViewer sender) {
      update();
    }

    @Override
    public void onTestNodeAdded(@NotNull TestResultsViewer sender, @NotNull SMTestProxy test) {
      update();
    }

    void setNode(AbstractTreeNode<?> node) {
      myNodeReference = new WeakReference<>(node);
    }

    private void update() {
      AbstractTreeNode<?> node = SoftReference.dereference(myNodeReference);
      if (node != null) {
        //noinspection ConstantConditions
        AppUIExecutor.onUiThread().expireWith(node.getProject()).submit(node::update);
      }
    }
  }
}
