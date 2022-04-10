// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Location;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AppIcon;
import com.intellij.ui.SystemNotifications;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;

public final class TestsUIUtil {
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Test Runner");

  public static final Color PASSED_COLOR = new Color(0, 128, 0);
  private static final @NonNls String TESTS = "tests";

  private TestsUIUtil() {
  }

  public static boolean isMultipleSelectionImpossible(DataContext dataContext) {
    final Component component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    if (component instanceof JTree) {
      final TreePath[] selectionPaths = ((JTree)component).getSelectionPaths();
      if (selectionPaths == null || selectionPaths.length == 0) {
        return true;
      }
      if (selectionPaths.length == 1) {
        Object lastPathComponent = selectionPaths[0].getLastPathComponent();
        if (lastPathComponent instanceof TreeNode && ((TreeNode)lastPathComponent).isLeaf()) {
          return true;
        }
      }
      return false;
    }
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor != null && !editor.getSelectionModel().hasSelection() && editor.getCaretModel().getCaretCount() < 2) {
      return true;
    }
    return false;
  }

  public static Navigatable getOpenFileDescriptor(final AbstractTestProxy testProxy,
                                                  final TestFrameworkRunningModel model) {
    return getOpenFileDescriptor(testProxy, model.getProperties());
  }

  public static Navigatable getOpenFileDescriptor(final AbstractTestProxy testProxy,
                                                  final TestConsoleProperties testConsoleProperties) {
    return getOpenFileDescriptor(testProxy, testConsoleProperties,
                                 TestConsoleProperties.OPEN_FAILURE_LINE.value(testConsoleProperties));
  }

  private static Navigatable getOpenFileDescriptor(final AbstractTestProxy proxy,
                                                   final TestConsoleProperties testConsoleProperties,
                                                   final boolean openFailureLine) {
    final Project project = testConsoleProperties.getProject();

    if (proxy != null) {
      final Location location = proxy.getLocation(project, testConsoleProperties.getScope());
      if (openFailureLine) {
        return proxy.getDescriptor(location, testConsoleProperties);
      }
      final Navigatable navigatable = location == null ? null : location.getNavigatable();
      if (navigatable instanceof OpenFileDescriptor && ((OpenFileDescriptor)navigatable).getFile().isValid()) {
        return navigatable;
      }
      return navigatable;
    }
    return null;
  }

  public static void notifyByBalloon(@NotNull final Project project,
                                     boolean started,
                                     final AbstractTestProxy root,
                                     final TestConsoleProperties properties,
                                     @Nullable final @NlsContexts.SystemNotificationText String comment) {
    notifyByBalloon(project, root, properties, new TestResultPresentation(root, started, comment).getPresentation());
  }

  public static void notifyByBalloon(@NotNull final Project project,
                                     final AbstractTestProxy root,
                                     final TestConsoleProperties properties,
                                     TestResultPresentation testResultPresentation) {
    if (project.isDisposed()) return;
    if (properties == null) return;

    TestStatusListener.notifySuiteFinished(root, properties.getProject());

    final String windowId = properties.getWindowId();
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);

    final String title = testResultPresentation.getTitle();
    final String text = testResultPresentation.getText();
    final String balloonText = testResultPresentation.getBalloonText();
    final MessageType type = testResultPresentation.getType();

    ToolWindow toolWindow = toolWindowManager.getToolWindow(windowId);
    if (toolWindow != null && !toolWindow.isVisible()) {
      String displayId = getTestResultsNotificationDisplayId(windowId);
      NotificationGroup group = NotificationGroup.findRegisteredGroup(displayId);
      if (group == null) {
        group = NotificationGroup.toolWindowGroup(displayId, windowId, false);
      }
      group.createNotification(balloonText, type).notify(project);
    }

    NOTIFICATION_GROUP.createNotification(balloonText, type).notify(project);
    SystemNotifications.getInstance().notify("TestRunner", title, text);
  }

  private static @NonNls String getTestResultsNotificationDisplayId(@NotNull String toolWindowId) {
    return "Test Results: " + toolWindowId;
  }

  @NlsContexts.NotificationContent
  public static String getTestSummary(AbstractTestProxy proxy) {
    return new TestResultPresentation(proxy).getPresentation().getBalloonText();
  }

  @NlsContexts.SystemNotificationText
  public static String getTestShortSummary(AbstractTestProxy proxy) {
    return new TestResultPresentation(proxy).getPresentation().getText();
  }

  public static void showIconProgress(Project project, int n, final int maximum, final int problemsCounter, boolean updateWithAttention) {
    AppIcon icon = AppIcon.getInstance();
    if (n < maximum || !updateWithAttention) {
      if (!updateWithAttention || icon.setProgress(project, TESTS, AppIconScheme.Progress.TESTS, (double)n / (double)maximum, problemsCounter == 0)) {
        if (problemsCounter > 0) {
          icon.setErrorBadge(project, String.valueOf(problemsCounter));
        }
      }
    } else {
      if (icon.hideProgress(project, TESTS)) {
        if (problemsCounter > 0) {
          icon.setErrorBadge(project, String.valueOf(problemsCounter));
        } else {
          icon.setOkBadge(project, true);
        }
        icon.requestAttention(project, false);
      }
    }
  }

  public static void clearIconProgress(Project project) {
    AppIcon.getInstance().hideProgress(project, TESTS);
    AppIcon.getInstance().setErrorBadge(project, null);
  }

  public static class TestResultPresentation {
    private final AbstractTestProxy myRoot;
    private final boolean myStarted;
    private final @NlsContexts.SystemNotificationText String myComment;

    private @NlsContexts.SystemNotificationTitle String myTitle;
    private @NlsContexts.SystemNotificationText String myText;
    private @NlsContexts.NotificationContent String myBalloonText;
    private MessageType myType;

    private int myFailedCount;
    private int myPassedCount;
    private int myNotStartedCount;
    private int myIgnoredCount;

    public TestResultPresentation(AbstractTestProxy root, boolean started, @NlsContexts.SystemNotificationText String comment) {
      myRoot = root;
      myStarted = started;
      myComment = comment;
    }

    public TestResultPresentation(AbstractTestProxy root) {
      this(root, true, null);
    }

    public @NlsContexts.SystemNotificationTitle String getTitle() {
      return myTitle;
    }

    public @NlsContexts.SystemNotificationText String getText() {
      return myText;
    }

    public @NlsContexts.NotificationContent String getBalloonText() {
      return myBalloonText;
    }

    public MessageType getType() {
      return myType;
    }

    /**
     * @deprecated Use {@link #getText()} to get short test result summary.
     */
    @Deprecated
    public int getFailedCount() {
      return myFailedCount;
    }

    /**
     * @deprecated Use {@link #getText()} to get short test result summary.
     */
    @Deprecated
    public int getPassedCount() {
      return myPassedCount;
    }

    /**
     * @deprecated Use {@link #getText()} to get short test result summary.
     */
    @Deprecated
    public int getNotStartedCount() {
      return myNotStartedCount;
    }

    /**
     * @deprecated Use {@link #getText()} to get short test result summary.
     */
    @Deprecated
    public int getIgnoredCount() {
      return myIgnoredCount;
    }

    public TestResultPresentation getPresentation() {
      List allTests = Filter.LEAF.select(myRoot.getAllTests());
      final List<AbstractTestProxy> failed = Filter.DEFECTIVE_LEAF.select(allTests);
      final List<AbstractTestProxy> notStarted = Filter.NOT_PASSED.select(allTests);
      notStarted.removeAll(failed);
      final List ignored = Filter.IGNORED.select(allTests);
      notStarted.removeAll(ignored);
      failed.removeAll(ignored);
      int failedCount = failed.size();
      int notStartedCount = notStarted.size() + ignored.size();
      int passedCount = allTests.size() - failedCount - notStartedCount;
      return getPresentation(failedCount, passedCount, notStartedCount, ignored.size());
    }

    public TestResultPresentation getPresentation(int failedCount, int passedCount, int notStartedCount, int ignoredCount) {
      if (myRoot == null) {
        myBalloonText = myTitle = myStarted ? TestRunnerBundle.message("test.interrupted.progress.text")
                                            : ExecutionBundle.message("test.not.started.progress.text");
        myText = "";
        myType = MessageType.WARNING;
      }
      else {
        myFailedCount = failedCount;
        myPassedCount = passedCount;
        myNotStartedCount = notStartedCount;
        myIgnoredCount = ignoredCount;

        if (failedCount > 0) {
          myTitle = ExecutionBundle.message("junit.running.info.tests.failed.label");
          myBalloonText = TestRunnerBundle.message("tests.failed.0.passed.1.ignored.2.not.started.3",
                                                   failedCount, passedCount, ignoredCount, ignoredCount > 0 ? 0 : notStartedCount);
          myText = myComment == null
                   ? TestRunnerBundle.message("0.failed.1.passed.2.ignored.3.not.started",
                                              failedCount, passedCount, ignoredCount, ignoredCount > 0 ? 0 : notStartedCount)
                   : TestRunnerBundle.message("0.failed.1.passed.2.ignored.3.not.started.with.comment",
                                              failedCount, passedCount, ignoredCount, ignoredCount > 0 ? 0 : notStartedCount, myComment);
          myType = MessageType.ERROR;
        }
        else if (ignoredCount > 0) {
          myTitle = TestRunnerBundle.message("tests.ignored.error.message");
          myBalloonText = TestRunnerBundle.message("tests.ignored.0.passed.1", ignoredCount, passedCount);
          myText = myComment == null
                   ? TestRunnerBundle.message("0.ignored.1.passed", ignoredCount, passedCount)
                   : TestRunnerBundle.message("0.ignored.1.passed.with.comment", ignoredCount, passedCount, myComment);
          myType = MessageType.WARNING;
        }
        else if (notStartedCount > 0) {
          myTitle = ExecutionBundle.message("junit.running.info.failed.to.start.error.message");
          myBalloonText = TestRunnerBundle.message("failed.to.start.0.passed.1", notStartedCount, passedCount);
          myText = myComment == null
                   ? TestRunnerBundle.message("0.not.started.1.passed", notStartedCount, passedCount)
                   : TestRunnerBundle.message("0.not.started.1.passed.with.comment", notStartedCount, passedCount, myComment);
          myType = MessageType.ERROR;
        }
        else {
          myTitle = ExecutionBundle.message("junit.running.info.tests.passed.label");
          myBalloonText = TestRunnerBundle.message("tests.passed.0", passedCount);
          myText = myComment == null
                   ? TestRunnerBundle.message("0.passed", passedCount)
                   : TestRunnerBundle.message("0.passed.with.comment", passedCount, myComment);
          myType = MessageType.INFO;
        }
      }
      return this;
    }
  }
}