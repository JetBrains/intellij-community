/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.fixtures;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.StripeButton;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.ui.content.Content;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Timeout;
import org.fest.swing.util.TextMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.timing.Pause.pause;

public abstract class ToolWindowFixture {
  @NotNull protected final String myToolWindowId;
  @NotNull protected final Project myProject;
  @NotNull protected final Robot myRobot;
  @NotNull protected final ToolWindow myToolWindow;

  protected ToolWindowFixture(@NotNull final String toolWindowId, @NotNull final Project project, @NotNull Robot robot) {
    myToolWindowId = toolWindowId;
    myProject = project;
    final Ref<ToolWindow> toolWindowRef = new Ref<>();
    pause(new Condition("Find tool window with ID '" + toolWindowId + "'") {
      @Override
      public boolean test() {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
        toolWindowRef.set(toolWindow);
        return toolWindow != null;
      }
    }, GuiTestUtil.INSTANCE.getSHORT_TIMEOUT());
    myRobot = robot;
    myToolWindow = toolWindowRef.get();
  }

  @Nullable
  protected Content getContent(@NotNull final String displayName) {
    activateAndWaitUntilIsVisible();
    final Ref<Content> contentRef = new Ref<>();
    try {
      pause(new Condition("finding content '" + displayName + "'") {
        @Override
        public boolean test() {
          Content[] contents = getContents();
          for (Content content : contents) {
            if (displayName.equals(content.getDisplayName())) {
              contentRef.set(content);
              return true;
            }
          }
          return false;
        }
      }, GuiTestUtil.INSTANCE.getSHORT_TIMEOUT());
    } catch (WaitTimedOutError e) {
      throw new ComponentLookupException("Cannot find content with " + displayName);
    }
    return contentRef.get();
  }

  @Nullable
  protected Content getSelectedContent() {
    activateAndWaitUntilIsVisible();
    return myToolWindow.getContentManager().getSelectedContent();
  }

  @Nullable
  protected Content getContent(@NotNull final String displayName, @NotNull Timeout timeout) {
    long now = System.currentTimeMillis();
    long budget = timeout.duration();
    activateAndWaitUntilIsVisible(Timeout.timeout(budget));
    long revisedNow = System.currentTimeMillis();
    budget -= (revisedNow - now);
    final Ref<Content> contentRef = new Ref<>();
    pause(new Condition("finding content with display name " + displayName) {
      @Override
      public boolean test() {
        Content[] contents = getContents();
        for (Content content : contents) {
          if (displayName.equals(content.getDisplayName())) {
            contentRef.set(content);
            return true;
          }
        }
        return false;
      }
    }, Timeout.timeout(budget));
    return contentRef.get();
  }

  @Nullable
  protected Content getContent(@NotNull final TextMatcher displayNameMatcher) {
    return getContent(displayNameMatcher, GuiTestUtil.INSTANCE.getSHORT_TIMEOUT());
  }

  @Nullable
  protected Content getContent(@NotNull final TextMatcher displayNameMatcher, @NotNull Timeout timeout) {
    long now = System.currentTimeMillis();
    long budget = timeout.duration();
    activateAndWaitUntilIsVisible(Timeout.timeout(budget));
    long revisedNow = System.currentTimeMillis();
    budget -= (revisedNow - now);
    final Ref<Content> contentRef = new Ref<>();
    pause(new Condition("finding content matching " + displayNameMatcher.formattedValues()) {
      @Override
      public boolean test() {
        Content[] contents = getContents();
        for (Content content : contents) {
          String displayName = content.getDisplayName();
          if (displayNameMatcher.isMatching(displayName)) {
            contentRef.set(content);
            return true;
          }
        }
        return false;
      }
    }, Timeout.timeout(budget));
    return contentRef.get();
  }

  private void activateAndWaitUntilIsVisible() {
    activateAndWaitUntilIsVisible(GuiTestUtil.INSTANCE.getSHORT_TIMEOUT());
  }

  private void activateAndWaitUntilIsVisible(@NotNull Timeout timeout) {
    long now = System.currentTimeMillis();
    long budget = timeout.duration();
    activate();
    budget -= System.currentTimeMillis() - now;
    waitUntilIsVisible(Timeout.timeout(budget));
  }

  @NotNull
  public Content[] getContents() {
    return myToolWindow.getContentManager().getContents();
  }

  protected boolean isActive() {
    //noinspection ConstantConditions
    return execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        return myToolWindow.isActive();
      }
    });
  }

  public void activate() {
    if (isActive()) {
      return;
    }

    final Callback callback = new Callback();
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        myToolWindow.activate(callback);
      }
    });

    pause(new Condition("Wait for ToolWindow '" + myToolWindowId + "' to be activated") {
      @Override
      public boolean test() {
        return callback.finished;
      }
    }, GuiTestUtil.INSTANCE.getSHORT_TIMEOUT());
  }

  protected void waitUntilIsVisible() {
    waitUntilIsVisible(GuiTestUtil.INSTANCE.getTHIRTY_SEC_TIMEOUT());
  }

  protected void waitUntilIsVisible(@NotNull Timeout timeout) {
    pause(new Condition("Wait for ToolWindow '" + myToolWindowId + "' to be visible") {
      @Override
      public boolean test() {
        if (!isActive()) {
          activate();
        }
        return isVisible();
      }
    }, timeout);
  }

  public boolean isVisible() {
    //noinspection ConstantConditions
    return execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        if (!myToolWindow.isVisible()) {
          return false;
        }
        JComponent component = myToolWindow.getComponent();
        return component.isVisible() && component.isShowing();
      }
    });
  }

  public void selectContent(String tabName) {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        Stream<Content> contentStream = Arrays.stream(myToolWindow.getContentManager().getContents());
        Optional<Content> contentOptional = contentStream.filter(content -> content.getTabName().equals(tabName)).findAny();
        if (!contentOptional.isPresent()) throw new ComponentLookupException("Unable to find content with tab name: \"" + tabName +
                                                                             "\" for ToolWindow with id: \"" + myToolWindowId + "\"");
        Content content = contentOptional.get();
        if(Objects.equals(myToolWindow.getContentManager().getSelectedContent(), content)) return; // no need to select already selected content
        myToolWindow.getContentManager().setSelectedContent(content);
      }
    });
  }

  public static void clickToolwindowButton(String toolWindowStripeButtonName, Robot robot){
    final StripeButton stripeButton = robot.finder().find(new GenericTypeMatcher<StripeButton>(StripeButton.class) {
      @Override
      protected boolean isMatching(@NotNull StripeButton button) {
        return (button.getText().equals(toolWindowStripeButtonName));
      }
    });
    robot.click(stripeButton);
  }

  public static void showToolwindowStripes(Robot robot){
    if (UISettings.getInstance().getHideToolStripes()) {
      final JLabel toolwindowsWidget = robot.finder().find(new GenericTypeMatcher<JLabel>(JLabel.class) {
        @Override
        protected boolean isMatching(@NotNull JLabel label) {
          if (label instanceof StatusBarWidget) {
            StatusBarWidget statusBarWidget = (StatusBarWidget)label;
            if (statusBarWidget.ID().equals("ToolWindows Widget")) {
              return true;
            }
          }
          return false;
        }
      });
      if (toolwindowsWidget != null) {
        robot.click(toolwindowsWidget);
      }
    }
  }


  private static class Callback implements Runnable {
    volatile boolean finished;

    @Override
    public void run() {
      finished = true;
    }
  }
}
