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
package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffToolbar;
import com.intellij.openapi.diff.ex.DiffStatusBar;
import com.intellij.openapi.diff.impl.DiffToolbarComponent;
import com.intellij.openapi.editor.colors.EditorColorsScheme;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class DiffPanelOutterComponent extends JPanel implements DataProvider {
  private final DiffStatusBar myStatusBar;
  private final DiffToolbarComponent myToolbar;
  private final DiffRequest.ToolbarAddons myDefaultActions;
  private DataProvider myDataProvider = null;
  private DeferScrollToFirstDiff myScrollState = NO_SCROLL_NEEDED;
  private ScrollingPanel myScrollingPanel = null;
  private final JPanel myBottomContainer;
  private JComponent myBottomComponent;

  public DiffPanelOutterComponent(List<TextDiffType> diffTypes, DiffRequest.ToolbarAddons defaultActions) {
    super(new BorderLayout());
    myStatusBar = new DiffStatusBar(diffTypes);
    myBottomContainer = new JPanel(new BorderLayout());
    myBottomContainer.add(myStatusBar, BorderLayout.SOUTH);
    add(myBottomContainer, BorderLayout.SOUTH);
    myDefaultActions = defaultActions;
    myToolbar = new DiffToolbarComponent(this);
    disableToolbar(false);
  }

  public DiffToolbar resetToolbar() {
    myToolbar.resetToolbar(myDefaultActions);
    return myToolbar.getToolbar();
  }

  public void insertDiffComponent(JComponent component, ScrollingPanel scrollingPanel) {
    add(component, BorderLayout.CENTER);
    setScrollingPanel(scrollingPanel);
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
    return new Dimension(600, 400);
  }

  public Object getData(String dataId) {
    if (PlatformDataKeys.SOURCE_NAVIGATION_LOCKED.is(dataId)) {
      return Boolean.TRUE;
    }
    if (myDataProvider == null) {
      return null;
    }
    if (PlatformDataKeys.EDITOR.is(dataId)) {
      if (myBottomComponent != null) {
        // we don't want editor actions to be executed when the bottom component has focus
        final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (myBottomComponent.isAncestorOf(focusOwner)) {
          return null;
        }
      }
      FocusDiffSide side = (FocusDiffSide)myDataProvider.getData(FocusDiffSide.DATA_KEY.getName());
      if (side != null) return side.getEditor();
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

  private interface DeferScrollToFirstDiff {
    DeferScrollToFirstDiff scrollNow(ScrollingPanel panel, JComponent component);

    void deferScroll(DiffPanelOutterComponent outter);
  }

  public interface ScrollingPanel {
    void scrollEditors();
  }

  private static final DeferScrollToFirstDiff NO_SCROLL_NEEDED = new DeferScrollToFirstDiff() {
    public DeferScrollToFirstDiff scrollNow(ScrollingPanel panel, JComponent component) {
      return NO_SCROLL_NEEDED;
    }

    public void deferScroll(DiffPanelOutterComponent outter) {
    }
  };

  private static final DeferScrollToFirstDiff SCROLL_WHEN_POSSIBLE = new DeferScrollToFirstDiff() {
    public DeferScrollToFirstDiff scrollNow(ScrollingPanel panel, JComponent component) {
      if (!component.isDisplayable()) return null;
      panel.scrollEditors();
      return NO_SCROLL_NEEDED;
    }

    public void deferScroll(final DiffPanelOutterComponent outter) {
      if (!outter.isDisplayable()) return;
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          outter.performScroll();
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
