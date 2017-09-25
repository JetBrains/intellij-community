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
package com.intellij.execution.testframework;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.AppIcon;
import com.intellij.ui.SystemNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;

public class TestsUIUtil {
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("Test Runner");

  public static final Color PASSED_COLOR = new Color(0, 128, 0);
  private static final String TESTS = "tests";

  private TestsUIUtil() {
  }

  @Nullable
  public static Object getData(final AbstractTestProxy testProxy, final String dataId, final TestFrameworkRunningModel model) {
    final TestConsoleProperties properties = model.getProperties();
    final Project project = properties.getProject();
    if (testProxy == null) return null;
    if (AbstractTestProxy.DATA_KEY.is(dataId)) return testProxy;
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) return getOpenFileDescriptor(testProxy, model);
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      final Location location = testProxy.getLocation(project, properties.getScope());
      if (location != null) {
        final PsiElement element = location.getPsiElement();
        return element.isValid() ? element : null;
      }
      else {
        return null;
      }
    }
    if (Location.DATA_KEY.is(dataId)) return testProxy.getLocation(project, properties.getScope());
    if (RunConfiguration.DATA_KEY.is(dataId)) {
      final RunProfile configuration = properties.getConfiguration();
      if (configuration instanceof RunConfiguration) {
        return configuration;
      }
    }
    return null;
  }

  public static boolean isMultipleSelectionImpossible(DataContext dataContext) {
    final Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    if (component instanceof JTree) {
      final TreePath[] selectionPaths = ((JTree)component).getSelectionPaths();
      if (selectionPaths == null || selectionPaths.length <= 1) {
        return true;
      }
    }
    return false;
  }

  public static Navigatable getOpenFileDescriptor(final AbstractTestProxy testProxy, final TestFrameworkRunningModel model) {
    final TestConsoleProperties testConsoleProperties = model.getProperties();
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
      final OpenFileDescriptor openFileDescriptor = location == null ? null : location.getOpenFileDescriptor();
      if (openFileDescriptor != null && openFileDescriptor.getFile().isValid()) {
        return openFileDescriptor;
      }
    }
    return null;
  }

  public static void notifyByBalloon(@NotNull final Project project,
                                     boolean started,
                                     final AbstractTestProxy root,
                                     final TestConsoleProperties properties,
                                     @Nullable final String comment) {
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

    if (!Comparing.strEqual(toolWindowManager.getActiveToolWindowId(), windowId)) {
      toolWindowManager.notifyByBalloon(windowId, type, balloonText, null, null);
    }

    NOTIFICATION_GROUP.createNotification(balloonText, type).notify(project);
    SystemNotifications.getInstance().notify("TestRunner", title, text);
  }

  public static String getTestSummary(AbstractTestProxy proxy) {
    return new TestResultPresentation(proxy).getPresentation().getBalloonText();
  }

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
          icon.requestAttention(project, false);
        } else {
          icon.setOkBadge(project, true);
          icon.requestAttention(project, false);
        }
      }
    }
  }

  public static void clearIconProgress(Project project) {
    AppIcon.getInstance().hideProgress(project, TESTS);
    AppIcon.getInstance().setErrorBadge(project, null);
  }

  public static class TestResultPresentation {
    private AbstractTestProxy myRoot;
    private boolean myStarted;
    private String myComment;
    private String myTitle;
    private String myText;
    private String myBalloonText;
    private MessageType myType;

    public TestResultPresentation(AbstractTestProxy root, boolean started, String comment) {
      myRoot = root;
      myStarted = started;
      myComment = comment;
    }

    public TestResultPresentation(AbstractTestProxy root) {
      this(root, true, null);
    }

    public String getTitle() {
      return myTitle;
    }

    public String getText() {
      return myText;
    }

    public String getBalloonText() {
      return myBalloonText;
    }

    public MessageType getType() {
      return myType;
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
        myBalloonText = myTitle = myStarted ? "Tests were interrupted" : ExecutionBundle.message("test.not.started.progress.text");
        myText = "";
        myType = MessageType.WARNING;
      } else{
        if (failedCount > 0) {
          myTitle = ExecutionBundle.message("junit.runing.info.tests.failed.label");
          myText = passedCount + " passed, " + failedCount + " failed" + (notStartedCount > 0 ? ", " + notStartedCount + " not started" : "");
          myType = MessageType.ERROR;
        }
        else if (notStartedCount > 0) {
          myTitle = ignoredCount > 0 ? "Tests Ignored" : ExecutionBundle.message("junit.running.info.failed.to.start.error.message");
          myText = passedCount + " passed, " + notStartedCount + (ignoredCount > 0 ? " ignored" : " not started");
          myType = ignoredCount > 0 ? MessageType.WARNING : MessageType.ERROR;
        }
        else {
          myTitle = ExecutionBundle.message("junit.runing.info.tests.passed.label");
          myText = passedCount + " passed";
          myType = MessageType.INFO;
        }
        if (myComment != null) {
          myText += " " + myComment;
        }
        myBalloonText = myTitle + ": " + myText;
      }
      return this;
    }

  }
}
