/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.*;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseListener;
import java.util.concurrent.CopyOnWriteArraySet;

public class JBTabsPaneImpl implements TabbedPane, SwingConstants {
  private final JBTabsImpl myTabs;
  private final CopyOnWriteArraySet<ChangeListener> myListeners = new CopyOnWriteArraySet<ChangeListener>();

  public JBTabsPaneImpl(@Nullable Project project, int tabPlacement, @NotNull Disposable parent) {
    myTabs = new JBEditorTabs(project, ActionManager.getInstance(), project == null ? null : IdeFocusManager.getInstance(project), parent) {
      @Override
      public boolean isAlphabeticalMode() {
        return false;
      }

      @Override
      protected void doPaintBackground(Graphics2D g2d, Rectangle clip) {
        super.doPaintBackground(g2d, clip);
        if (getTabsPosition() == JBTabsPosition.top && isSingleRow()) {
          int maxOffset = 0;
          int maxLength = 0;

          for (int i = getVisibleInfos().size() - 1; i >= 0; i--) {
            TabInfo visibleInfo = getVisibleInfos().get(i);
            TabLabel tabLabel = myInfo2Label.get(visibleInfo);
            Rectangle r = tabLabel.getBounds();
            if (r.width == 0 || r.height == 0) continue;
            maxOffset = r.x + r.width;
            maxLength = r.height;
            break;
          }

          maxOffset++;
          g2d.setPaint(UIUtil.getPanelBackground());
          if (getFirstTabOffset() > 0) {
            g2d.fillRect(clip.x, clip.y, clip.x + getFirstTabOffset() - 1, clip.y + maxLength - TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT);
          }
          g2d.fillRect(clip.x + maxOffset, clip.y, clip.width - maxOffset, clip.y + maxLength - TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT);
          g2d.setPaint(new JBColor(Gray._181, UIUtil.getPanelBackground()));
          g2d.drawLine(clip.x + maxOffset, clip.y + maxLength - TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT, clip.x + clip.width, clip.y + maxLength - TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT);
          g2d.setPaint(UIUtil.getPanelBackground());
          g2d.drawLine(clip.x, clip.y + maxLength, clip.width, clip.y + maxLength);
        }
      }

      @Override
      protected void paintSelectionAndBorder(Graphics2D g2d) {
        super.paintSelectionAndBorder(g2d);
      }
    };
    myTabs.setFirstTabOffset(10);

    myTabs.addListener(new TabsListener.Adapter() {
      @Override
      public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        fireChanged(new ChangeEvent(myTabs));
      }
    }).getPresentation()
      .setPaintBorder(1, 1, 1, 1)
      .setTabSidePaintBorder(2)
      .setPaintFocus(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())
      .setAlwaysPaintSelectedTab(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())
      .setGhostsAlwaysVisible(true);

    setTabPlacement(tabPlacement);
  }

  private void fireChanged(ChangeEvent event) {
    for (ChangeListener each : myListeners) {
      each.stateChanged(event);
    }
  }

  public JComponent getComponent() {
    return myTabs.getComponent();
  }

  public void putClientProperty(Object key, Object value) {
    myTabs.getComponent().putClientProperty(key, value);
  }

  public void setKeyboardNavigation(PrevNextActionsDescriptor installKeyboardNavigation) {
    myTabs.setNavigationActionBinding(installKeyboardNavigation.getPrevActionId(), installKeyboardNavigation.getNextActionId());
  }

  public void addChangeListener(ChangeListener listener) {
    myListeners.add(listener);
  }

  public int getTabCount() {
    return myTabs.getTabCount();
  }

  public void insertTab(String title, Icon icon, Component c, String tip, int index) {
    assert c instanceof JComponent;
    myTabs.addTab(new TabInfo((JComponent)c).setText(title).setTooltipText(tip).setIcon(icon), index);
  }

  public void setTabPlacement(int tabPlacement) {
    final JBTabsPresentation presentation = myTabs.getPresentation();
    switch (tabPlacement) {
      case TOP:
        presentation.setTabsPosition(JBTabsPosition.top);
        break;
      case BOTTOM:
        presentation.setTabsPosition(JBTabsPosition.bottom);
        break;
      case LEFT:
        presentation.setTabsPosition(JBTabsPosition.left);
        break;
      case RIGHT:
        presentation.setTabsPosition(JBTabsPosition.right);
        break;
      default:
        throw new IllegalArgumentException("Invalid tab placement code=" + tabPlacement);
    }
  }

  public void addMouseListener(MouseListener listener) {
    myTabs.getComponent().addMouseListener(listener);
  }

  public int getSelectedIndex() {
    return myTabs.getIndexOf(myTabs.getSelectedInfo());
  }

  public Component getSelectedComponent() {
    final TabInfo selected = myTabs.getSelectedInfo();
    return selected != null ? selected.getComponent() : null;
  }

  public void setSelectedIndex(int index) {
    myTabs.select(getTabAt(index), false);
  }

  public void removeTabAt(int index) {
    myTabs.removeTab(getTabAt(index));
  }

  private TabInfo getTabAt(int index) {
    checkIndex(index);
    return myTabs.getTabAt(index);
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= getTabCount()) {
      throw new ArrayIndexOutOfBoundsException("tabCount=" + getTabCount() + " index=" + index);
    }
  }

  public void revalidate() {
    myTabs.getComponent().revalidate();
  }

  public Color getForegroundAt(int index) {
    return getTabAt(index).getDefaultForeground();
  }

  public void setForegroundAt(int index, Color color) {
    getTabAt(index).setDefaultForeground(color);
  }

  public Component getComponentAt(int i) {
    return getTabAt(i).getComponent();
  }

  public void setTitleAt(int index, String title) {
    getTabAt(index).setText(title);
  }

  public void setToolTipTextAt(int index, String toolTipText) {
    getTabAt(index).setTooltipText(toolTipText);
  }

  public void setComponentAt(int index, Component c) {
    getTabAt(index).setComponent(c);
  }

  public void setIconAt(int index, Icon icon) {
    getTabAt(index).setIcon(icon);
  }

  public void setEnabledAt(int index, boolean enabled) {
    getTabAt(index).setEnabled(enabled);
  }

  public int getTabLayoutPolicy() {
    return myTabs.getPresentation().isSingleRow() ? JTabbedPane.SCROLL_TAB_LAYOUT : JTabbedPane.WRAP_TAB_LAYOUT;
  }

  public void setTabLayoutPolicy(int policy) {
    switch (policy) {
      case JTabbedPane.SCROLL_TAB_LAYOUT:
        myTabs.getPresentation().setSingleRow(true);
        break;
      case JTabbedPane.WRAP_TAB_LAYOUT:
        myTabs.getPresentation().setSingleRow(false);
        break;
      default:
        throw new IllegalArgumentException("Unsupported tab layout policy: " + policy);
    }
  }

  public void scrollTabToVisible(int index) {
  }

  public String getTitleAt(int i) {
    return getTabAt(i).getText();
  }

  public void removeAll() {
    myTabs.removeAllTabs();
  }

  public void updateUI() {
    myTabs.getComponent().updateUI();
  }

  public void removeChangeListener(ChangeListener listener) {
    myListeners.remove(listener);
  }

  public JBTabs getTabs() {
    return myTabs;
  }

  public boolean isDisposed() {
    return myTabs.isDisposed();
  }
}
