/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.ui.layout.impl.GridImpl;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.fest.swing.timing.Timeout;
import org.fest.swing.util.TextMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.List;

import static com.intellij.util.ui.UIUtil.findComponentOfType;
import static com.intellij.util.ui.UIUtil.findComponentsOfType;
import static junit.framework.Assert.assertNotNull;
import static org.fest.reflect.core.Reflection.method;
import static org.fest.swing.timing.Pause.pause;

public class ExecutionToolWindowFixture extends ToolWindowFixture {
  public static class ContentFixture {
    @NotNull private final ExecutionToolWindowFixture myParentToolWindow;
    @NotNull private final Robot myRobot;
    @NotNull private final Content myContent;

    private ContentFixture(@NotNull ExecutionToolWindowFixture parentToolWindow, @NotNull Robot robot, @NotNull Content content) {
      myParentToolWindow = parentToolWindow;
      myRobot = robot;
      myContent = content;
    }

    public void waitForOutput(@NotNull final TextMatcher matcher, @NotNull Timeout timeout) {
      pause(new Condition("LogCat tool window output check for package name.") {
        @Override
        public boolean test() {
          return outputMatches(matcher);
        }
      }, timeout);
    }

    /**
     * Waits until it grabs the console window and then returns true if its text matches that of {@code matcher}.
     * Note: The caller should wrap it in something like a org.fest.swing.timing.Pause.pause to make sure they don't hang forever if the
     * console view cannot be found for some reason.
     */
    public boolean outputMatches(@NotNull TextMatcher matcher) {
      ConsoleViewImpl consoleView;
      while ((consoleView = findConsoleView()) == null || consoleView.getEditor() == null) {
        // If our handle has been replaced, find it again.
        JComponent consoleComponent = getTabComponent("Console");
        myRobot.click(consoleComponent);
      }
      return matcher.isMatching(consoleView.getEditor().getDocument().getText());
    }

    // Returns the console or null if it is not found.
    @Nullable
    private ConsoleViewImpl findConsoleView() {
      try {
        return myRobot.finder().findByType(myParentToolWindow.myToolWindow.getComponent(), ConsoleViewImpl.class, false);
      } catch (ComponentLookupException e) {
        return null;
      }
    }

    @NotNull
    public JComponent getTabComponent(@NotNull final String tabName) {
      return getTabContent(myParentToolWindow.myToolWindow.getComponent(), JBRunnerTabs.class, GridImpl.class, tabName);
    }

    @NotNull
    public UnitTestTreeFixture getUnitTestTree() {
      return new UnitTestTreeFixture(this, myRobot.finder().findByType(myContent.getComponent(), TestTreeView.class));
    }

    // Returns the root of the debugger tree or null.
    @Nullable
    public XDebuggerTreeNode getDebuggerTreeRoot() {
      try {
        JComponent debuggerComponent = getTabComponent("Debugger");
        if (debuggerComponent != null) {
          myRobot.click(debuggerComponent);
        }
        ThreeComponentsSplitter threeComponentsSplitter =
          myRobot.finder().findByType(debuggerComponent, ThreeComponentsSplitter.class, false);
        JComponent innerComponent = threeComponentsSplitter.getInnerComponent();
        assertNotNull(innerComponent);
        return myRobot.finder().findByType(innerComponent, XDebuggerTree.class, false).getRoot();
      } catch (ComponentLookupException e) {
        return null;
      }
    }

    public void clickDebuggerTreeRoot() {
      try {
        JComponent debuggerComponent = getTabComponent("Debugger");
        myRobot.click(debuggerComponent);
      } catch (ComponentLookupException e) { }
    }

    @NotNull
    private JComponent getTabContent(@NotNull final JComponent root,
                                     final Class<? extends JBTabsImpl> parentComponentType,
                                     @NotNull final Class<? extends JComponent> tabContentType,
                                     @NotNull final String tabName) {
      myParentToolWindow.activate();
      myParentToolWindow.waitUntilIsVisible();

      TabLabel tabLabel;
      if (parentComponentType == null) {
        tabLabel = GuiTestUtil.INSTANCE.waitUntilFound(myRobot, new GenericTypeMatcher<TabLabel>(TabLabel.class) {
          @Override
          protected boolean isMatching(@NotNull TabLabel component) {
            return component.toString().equals(tabName);
          }
        });
      }
      else {
        final JComponent parent = myRobot.finder().findByType(root, parentComponentType, false);
        tabLabel = GuiTestUtil.INSTANCE.waitUntilFound(myRobot, parent, new GenericTypeMatcher<TabLabel>(TabLabel.class) {
          @Override
          protected boolean isMatching(@NotNull TabLabel component) {
            return component.getParent() == parent && component.toString().equals(tabName);
          }
        });
      }
      myRobot.click(tabLabel);
      return myRobot.finder().findByType(tabContentType);
    }

    public boolean isExecutionInProgress() {
      // Consider that execution is in progress if 'stop' toolbar button is enabled.
      for (ActionButton button : getToolbarButtons()) {
        if ("com.intellij.execution.actions.StopAction".equals(button.getAction().getClass().getCanonicalName())) {
          //noinspection ConstantConditions
          return method("isButtonEnabled").withReturnType(boolean.class).in(button).invoke();
        }
      }
      return true;
    }

    public void rerun() {
      for (ActionButton button : getToolbarButtons()) {
        if ("com.intellij.execution.runners.FakeRerunAction".equals(button.getAction().getClass().getCanonicalName())) {
          myRobot.click(button);
          return;
        }
      }

      throw new IllegalStateException("Could not find the Re-run button.");
    }

    public void rerunFailed() {
      for (ActionButton button : getToolbarButtons()) {
        if ("com.intellij.execution.junit2.ui.actions.RerunFailedTestsAction".equals(button.getAction().getClass().getCanonicalName())) {
          myRobot.click(button);
          return;
        }
      }

      throw new IllegalStateException("Could not find the Re-run failed tests button.");
    }

    public void waitForExecutionToFinish(@NotNull Timeout timeout) {
      Pause.pause(new Condition("Wait for execution to finish") {
        @Override
        public boolean test() {
          return !isExecutionInProgress();
        }
      }, timeout);
    }

    @TestOnly
    public boolean stop() {
      for (final ActionButton button : getToolbarButtons()) {
        final AnAction action = button.getAction();
        if (action != null && action.getClass().getName().equals("com.intellij.execution.actions.StopAction")) {
          //noinspection ConstantConditions
          boolean enabled = method("isButtonEnabled").withReturnType(boolean.class).in(button).invoke();
          if (enabled) {
            GuiActionRunner.execute(new GuiTask() {
              @Override
              protected void executeInEDT() throws Throwable {
                button.click();
              }
            });
            return true;
          }
          return false;
        }
      }
      return false;
    }

    @NotNull
    private List<ActionButton> getToolbarButtons() {
      ActionToolbarImpl toolbar = findComponentOfType(myContent.getComponent(), ActionToolbarImpl.class);
      assert toolbar != null;
      return findComponentsOfType(toolbar, ActionButton.class);
    }
  }  // End class ContentFixture

  protected ExecutionToolWindowFixture(@NotNull String toolWindowId, @NotNull IdeFrameFixture ideFrame) {
    super(toolWindowId, ideFrame.getProject(), ideFrame.robot());
  }

  @NotNull
  public ContentFixture findContent(@NotNull String tabName) {
    Content content = getContent(tabName);
    assertNotNull(content);
    return new ContentFixture(this, myRobot, content);
  }

  @NotNull
  public ContentFixture findContent(@NotNull TextMatcher tabNameMatcher) {
    Content content = getContent(tabNameMatcher);
    assertNotNull(content);
    return new ContentFixture(this, myRobot, content);
  }
}
