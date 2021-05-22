// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.tabs.*;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseListener;
import java.util.concurrent.CopyOnWriteArraySet;

// used externally - cannot be final
public class JBTabsPaneImpl implements TabbedPane {
  private final JBEditorTabsBase myTabs;
  private final CopyOnWriteArraySet<ChangeListener> myListeners = new CopyOnWriteArraySet<>();

  public JBTabsPaneImpl(@Nullable Project project, int tabPlacement, @NotNull Disposable parent) {
    myTabs = JBTabsFactory.createEditorTabs(project, parent);
    myTabs.getPresentation()
      .setAlphabeticalMode(false)
      .setSupportsCompression(false)
      .setFirstTabOffset(10);
    myTabs.setEmptySpaceColorCallback(() -> UIUtil.getBgFillColor(myTabs.getComponent().getParent()));

    myTabs.addListener(new TabsListener() {
      @Override
      public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        fireChanged(new ChangeEvent(myTabs));
      }
    }).getPresentation()
      .setPaintBorder(1, 1, 1, 1)
      .setTabSidePaintBorder(2)
      .setPaintFocus(StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF());

    setTabPlacement(tabPlacement);
  }

  private void fireChanged(ChangeEvent event) {
    for (ChangeListener each : myListeners) {
      each.stateChanged(event);
    }
  }

  @Override
  public JComponent getComponent() {
    return myTabs.getComponent();
  }

  @Override
  public void putClientProperty(@NotNull Object key, Object value) {
    myTabs.getComponent().putClientProperty(key, value);
  }

  @Override
  public void setKeyboardNavigation(@NotNull PrevNextActionsDescriptor installKeyboardNavigation) {
    myTabs.setNavigationActionBinding(installKeyboardNavigation.getPrevActionId(), installKeyboardNavigation.getNextActionId());
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener listener) {
    myListeners.add(listener);
  }

  @Override
  public int getTabCount() {
    return myTabs.getTabCount();
  }

  @Override
  public void insertTab(@NotNull String title, Icon icon, @NotNull Component c, String tip, int index) {
    assert c instanceof JComponent;
    myTabs.addTab(new TabInfo((JComponent)c).setText(title).setTooltipText(tip).setIcon(icon), index);
  }

  @Override
  public void setTabPlacement(int tabPlacement) {
    final JBTabsPresentation presentation = myTabs.getPresentation();
    switch (tabPlacement) {
      case SwingConstants.TOP:
        presentation.setTabsPosition(JBTabsPosition.top);
        break;
      case SwingConstants.BOTTOM:
        presentation.setTabsPosition(JBTabsPosition.bottom);
        break;
      case SwingConstants.LEFT:
        presentation.setTabsPosition(JBTabsPosition.left);
        break;
      case SwingConstants.RIGHT:
        presentation.setTabsPosition(JBTabsPosition.right);
        break;
      default:
        throw new IllegalArgumentException("Invalid tab placement code=" + tabPlacement);
    }
  }

  @Override
  public void addMouseListener(@NotNull MouseListener listener) {
    myTabs.getComponent().addMouseListener(listener);
  }

  @Override
  public int getSelectedIndex() {
    return myTabs.getIndexOf(myTabs.getSelectedInfo());
  }

  @Override
  public Component getSelectedComponent() {
    final TabInfo selected = myTabs.getSelectedInfo();
    return selected != null ? selected.getComponent() : null;
  }

  @Override
  public void setSelectedIndex(int index) {
    myTabs.select(getTabAt(index), false);
  }

  @Override
  public Component getTabComponentAt(int index) {
    final TabInfo tabInfo = myTabs.getTabAt(index);
    return myTabs.getTabLabel(tabInfo);
  }

  @Override
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

  @Override
  public void revalidate() {
    myTabs.getComponent().revalidate();
  }

  @Override
  public Color getForegroundAt(int index) {
    return getTabAt(index).getDefaultForeground();
  }

  @Override
  public void setForegroundAt(int index, Color color) {
    getTabAt(index).setDefaultForeground(color);
  }

  @Override
  public Component getComponentAt(int i) {
    return getTabAt(i).getComponent();
  }

  @Override
  public void setTitleAt(int index, @NotNull String title) {
    getTabAt(index).setText(title);
  }

  @Override
  public void setToolTipTextAt(int index, String toolTipText) {
    getTabAt(index).setTooltipText(toolTipText);
  }

  @Override
  public void setComponentAt(int index, Component c) {
    getTabAt(index).setComponent(c);
  }

  @Override
  public void setIconAt(int index, Icon icon) {
    getTabAt(index).setIcon(icon);
  }

  @Override
  public void setEnabledAt(int index, boolean enabled) {
    getTabAt(index).setEnabled(enabled);
  }

  @Override
  public int getTabLayoutPolicy() {
    return myTabs.getPresentation().isSingleRow() ? JTabbedPane.SCROLL_TAB_LAYOUT : JTabbedPane.WRAP_TAB_LAYOUT;
  }

  @Override
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

  @Override
  public void scrollTabToVisible(int index) {
  }

  @Override
  public String getTitleAt(int i) {
    return getTabAt(i).getText();
  }

  @Override
  public void removeAll() {
    myTabs.removeAllTabs();
  }

  @Override
  public void updateUI() {
    myTabs.getComponent().updateUI();
  }

  @Override
  public void removeChangeListener(ChangeListener listener) {
    myListeners.remove(listener);
  }

  @NotNull
  public JBTabs getTabs() {
    return myTabs;
  }
}
