/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffToolbar;
import com.intellij.openapi.diff.ex.DiffStatusBar;
import com.intellij.openapi.diff.impl.DiffToolbarComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class DiffPanelOuterComponent extends JPanel implements DataProvider {
  private final DiffStatusBar myStatusBar;
  private final DiffToolbarComponent myToolbar;
  @Nullable private DiffRequest.ToolbarAddons myDefaultActions;
  private DataProvider myDataProvider = null;
  private DeferScrollToFirstDiff myScrollState = NO_SCROLL_NEEDED;
  private ScrollingPanel myScrollingPanel = null;
  private final JPanel myBottomContainer;
  private JComponent myBottomComponent;
  private JPanel myWrapper;
  private Getter<Integer> myPreferredHeightGetter;
  private int myPrefferedWidth;
  private Getter<Integer> myDefaultHeight;

  public DiffPanelOuterComponent(List<TextDiffType> diffTypes, @Nullable DiffRequest.ToolbarAddons toolbarAddons) {
    super(new BorderLayout());
    myStatusBar = new DiffStatusBar(diffTypes);
    myBottomContainer = new JPanel(new BorderLayout());
    myBottomContainer.add(myStatusBar, BorderLayout.SOUTH);
    add(myBottomContainer, BorderLayout.SOUTH);
    myDefaultActions = toolbarAddons;
    myToolbar = new DiffToolbarComponent(this);
    disableToolbar(false);
    myWrapper = new JPanel(new BorderLayout());
    add(myWrapper, BorderLayout.CENTER);
    myDefaultHeight = new Getter<Integer>() {
      @Override
      public Integer get() {
        return 400;
      }
    };
    myPreferredHeightGetter = myDefaultHeight;
    myPrefferedWidth = 600;
  }

  public void setToolbarActions(@NotNull DiffRequest.ToolbarAddons toolbarAddons) {
    myDefaultActions = toolbarAddons;
  }

  public DiffToolbar resetToolbar() {
    if (myDefaultActions != null) {
      myToolbar.resetToolbar(myDefaultActions);
    }
    return myToolbar.getToolbar();
  }

  public void insertDiffComponent(JComponent component, ScrollingPanel scrollingPanel) {
    myWrapper.add(component, BorderLayout.CENTER);
    setScrollingPanel(scrollingPanel);
  }

  public void insertTopComponent(JComponent component) {
    myWrapper.add(component, BorderLayout.NORTH);
  }

  public JComponent getBottomComponent() {
    return myBottomComponent;
  }

  public void setBottomComponent(JComponent component) {
    if (myBottomComponent != null) {
      myBottomContainer.remove(myBottomComponent);
    }
    myBottomComponent = component;
    if (myBottomComponent != null) {
      myBottomContainer.add(BorderLayout.CENTER, component);
    }
  }

  public void setDataProvider(DataProvider dataProvider) {
    myDataProvider = dataProvider;
  }

  public void setStatusBarText(String text) {
    myStatusBar.setText(text);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(myPrefferedWidth, myPreferredHeightGetter.get());
  }

  public void setPrefferedWidth(int prefferedWidth) {
    myPrefferedWidth = prefferedWidth;
  }

  public void setPreferredHeightGetter(final Getter<Integer> getter) {
    if (getter == null) {
      myPreferredHeightGetter = myDefaultHeight;
    } else {
      myPreferredHeightGetter = getter;
    }
  }

  public void removeStatusBar() {
    if (myStatusBar != null) {
      myStatusBar.setVisible(false);
    }
  }

  public Object getData(String dataId) {
    if (PlatformDataKeys.SOURCE_NAVIGATION_LOCKED.is(dataId)) {
      return Boolean.TRUE;
    }
    if (myDataProvider == null) {
      return null;
    }
    if (CommonDataKeys.EDITOR.is(dataId)) {
      if (myBottomComponent != null) {
        // we don't want editor actions to be executed when the bottom component has focus
        final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (myBottomComponent.isAncestorOf(focusOwner)) {
          return null;
        }
      }
      final FocusDiffSide side = (FocusDiffSide)myDataProvider.getData(FocusDiffSide.DATA_KEY.getName());
      if (side != null) {
        final Editor editor = side.getEditor();
        return editor != null && editor.getComponent().hasFocus() ? editor : null;
      }
    }
    return myDataProvider.getData(dataId);
  }

  public void setScrollingPanel(ScrollingPanel scrollingPanel) {
    myScrollingPanel = scrollingPanel;
  }

  public void requestScrollEditors() {
    myScrollState = SCROLL_WHEN_POSSIBLE;
    tryScrollNow();
  }

  private void tryScrollNow() {
    if (myScrollingPanel == null) return;
    myScrollState.deferScroll(this);
  }

  private void performScroll() {
    DeferScrollToFirstDiff newState = myScrollState.scrollNow(myScrollingPanel, this);
    if (newState != null) myScrollState = newState;
  }

  public void addNotify() {
    super.addNotify();
    tryScrollNow();
  }

  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, y, width, height);
    tryScrollNow();
  }

  protected void validateTree() {
    super.validateTree();
    tryScrollNow();
  }

  public void cancelScrollEditors() {
    myScrollState = NO_SCROLL_NEEDED;
  }

  public void removeTopComponent(final JComponent jComponent) {
    myWrapper.remove(jComponent);
  }

  private interface DeferScrollToFirstDiff {
    DeferScrollToFirstDiff scrollNow(ScrollingPanel panel, JComponent component);

    void deferScroll(DiffPanelOuterComponent outer);
  }

  public interface ScrollingPanel {
    void scrollEditors();
  }

  private static final DeferScrollToFirstDiff NO_SCROLL_NEEDED = new DeferScrollToFirstDiff() {
    public DeferScrollToFirstDiff scrollNow(ScrollingPanel panel, JComponent component) {
      return NO_SCROLL_NEEDED;
    }

    public void deferScroll(DiffPanelOuterComponent outer) {
    }
  };

  private static final DeferScrollToFirstDiff SCROLL_WHEN_POSSIBLE = new DeferScrollToFirstDiff() {
    public DeferScrollToFirstDiff scrollNow(ScrollingPanel panel, JComponent component) {
      if (!component.isDisplayable()) return null;
      panel.scrollEditors();
      return NO_SCROLL_NEEDED;
    }

    public void deferScroll(final DiffPanelOuterComponent outer) {
      if (!outer.isDisplayable()) return;
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          outer.performScroll();
        }
      });
    }
  };

  public void disableToolbar(boolean disable) {
    if (disable && isToolbarEnabled()) remove(myToolbar);
    else if (myToolbar.getParent() == null) add(myToolbar, BorderLayout.NORTH);
  }

  public boolean isToolbarEnabled() {
    return myToolbar.getParent() != null;
  }

  public void registerToolbarActions() {
    myToolbar.getToolbar().registerKeyboardActions(this);
  }

  public void setColorScheme(EditorColorsScheme scheme) {
    myStatusBar.setColorScheme(scheme);
  }
}
