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
import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SystemNotifications;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class TestsUIUtil {
  @NonNls private static final String ICONS_ROOT = "/runConfigurations/";

  public static final Color PASSED_COLOR = new Color(0, 128, 0);
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.testframework.TestsUIUtil");

  private TestsUIUtil() {
  }

  @Nullable
  public static Object getData(final AbstractTestProxy testProxy, final String dataId, final TestFrameworkRunningModel model) {
    final Project project = model.getProperties().getProject();
    if (testProxy == null) return null;
    if (AbstractTestProxy.DATA_KEY.is(dataId)) return testProxy;
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) return getOpenFileDescriptor(testProxy, model);
    if (PlatformDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      final Navigatable openFileDescriptor = getOpenFileDescriptor(testProxy, model);
      return openFileDescriptor != null ? new Navigatable[]{openFileDescriptor} : null;
    }
    if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
      final Location location = testProxy.getLocation(project);
      if (location != null) {
        final PsiElement element = location.getPsiElement();
        return element.isValid() ? element : null;
      }
      else {
        return null;
      }
    }
    if (Location.DATA_KEY.is(dataId)) return testProxy.getLocation(project);
    if (RuntimeConfiguration.DATA_KEY.is(dataId)) return model.getProperties().getConfiguration();
    return null;
  }

  public static Navigatable getOpenFileDescriptor(final AbstractTestProxy testProxy, final TestFrameworkRunningModel model) {
    return getOpenFileDescriptor(testProxy, model.getProperties().getProject(),
                                 TestConsoleProperties.OPEN_FAILURE_LINE.value(model.getProperties()));
  }

  private static Navigatable getOpenFileDescriptor(final AbstractTestProxy proxy, final Project project, final boolean openFailureLine) {
    if (proxy != null) {
      final Location location = proxy.getLocation(project);
      if (openFailureLine) {
        return proxy.getDescriptor(location);
      }
      final OpenFileDescriptor openFileDescriptor = location == null ? null : location.getOpenFileDescriptor();
      if (openFileDescriptor != null && openFileDescriptor.getFile().isValid()) {
        return openFileDescriptor;
      }
    }
    return null;
  }

  public static Icon loadIcon(@NonNls final String iconName) {
    final Application application = ApplicationManager.getApplication();
    if (application == null || application.isUnitTestMode()) return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR));
    @NonNls final String fullIconName = ICONS_ROOT + iconName + ".png";

    return IconLoader.getIcon(fullIconName);
  }

  public static void notifyByBalloon(@NotNull final Project project, final AbstractTestProxy root, final TestConsoleProperties properties) {
    if (project.isDisposed()) return;
    if (properties == null) return;

    final String testRunDebugId = properties.isDebug() ? ToolWindowId.DEBUG : ToolWindowId.RUN;
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);

    String title;
    String text;
    String balloonText;
    MessageType type;
    TestResultPresentation testResultPresentation = new TestResultPresentation(root).getPresentation();
    type = testResultPresentation.getType();
    balloonText = testResultPresentation.getBalloonText();
    title = testResultPresentation.getTitle();
    text = testResultPresentation.getText();

    if (!Comparing.strEqual(toolWindowManager.getActiveToolWindowId(), testRunDebugId)) {
      toolWindowManager.notifyByBalloon(testRunDebugId, type, balloonText, null, null);
    }
    SystemNotifications.getInstance().notify("TestRunner", title, text);
  }

  public static String getTestSummary(AbstractTestProxy proxy) {
    return new TestResultPresentation(proxy).getPresentation().getBalloonText();
  }

  private static class TestResultPresentation {
    private AbstractTestProxy myRoot;
    private String myTitle;
    private String myText;
    private String myBalloonText;
    private MessageType myType;

    public TestResultPresentation(AbstractTestProxy root) {
      myRoot = root;
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
      if (myRoot == null) {
        myBalloonText = myTitle = ExecutionBundle.message("test.not.started.progress.text");
        myText = "";
        myType = MessageType.WARNING;
      } else{
        List allTests = Filter.LEAF.select(myRoot.getAllTests());
        final List<AbstractTestProxy> failed = Filter.DEFECTIVE_LEAF.select(allTests);
        int failedCount = failed.size();
        final List<AbstractTestProxy> notStarted = Filter.NOT_PASSED.select(allTests);
        notStarted.removeAll(failed);
        int notStartedCount = notStarted.size();
        int passedCount = allTests.size() - failedCount - notStartedCount;
        if (failedCount > 0) {
          myTitle = ExecutionBundle.message("junit.runing.info.tests.failed.label");
          myText = passedCount + " passed, " + failedCount + " failed" + (notStartedCount > 0 ? ", " + notStartedCount + " not started" : "");
          myType = MessageType.ERROR;
        }
        else if (notStartedCount > 0) {
          myTitle = ExecutionBundle.message("junit.runing.info.failed.to.start.error.message");
          myText = passedCount + " passed, " + notStartedCount + " not started" ;
          myType = MessageType.ERROR;
        }
        else {
          myTitle = ExecutionBundle.message("junit.runing.info.tests.passed.label");
          myText = passedCount + " passed";
          myType = MessageType.INFO;
        }
        myBalloonText = myTitle + ": " + myText;
      }
      return this;
    }

  }
}
