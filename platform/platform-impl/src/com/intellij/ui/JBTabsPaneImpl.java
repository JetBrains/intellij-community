// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseListener;
import java.util.concurrent.CopyOnWriteArraySet;

// used externally - cannot be final
public class JBTabsPaneImpl implements TabbedPane {
  private final JBEditorTabs tabs;
  private final CopyOnWriteArraySet<ChangeListener> listeners = new CopyOnWriteArraySet<>();

  public JBTabsPaneImpl(@Nullable Project project, int tabPlacement, @NotNull Disposable parent) {
    tabs = new JBEditorTabs(project, parent);
    tabs.getPresentation()
      .setAlphabeticalMode(false)
      .setPaintFocus(true)
      .setFirstTabOffset(10);

    tabs.addListener(new TabsListener() {
      @Override
      public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        fireChanged(new ChangeEvent(tabs));
      }
    });

    setTabPlacement(tabPlacement);
  }

  private void fireChanged(ChangeEvent event) {
    for (ChangeListener each : listeners) {
      each.stateChanged(event);
    }
  }

  @Override
  public JComponent getComponent() {
    return tabs.getComponent();
  }

  @Override
  public void putClientProperty(@NotNull Object key, Object value) {
    tabs.getComponent().putClientProperty(key, value);
  }

  @Override
  public void setKeyboardNavigation(@NotNull PrevNextActionsDescriptor installKeyboardNavigation) {
    tabs.setNavigationActionBinding(installKeyboardNavigation.getPrevActionId(), installKeyboardNavigation.getNextActionId());
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener listener) {
    listeners.add(listener);
  }

  @Override
  public int getTabCount() {
    return tabs.getTabCount();
  }

  @Override
  public void insertTab(@NotNull String title, Icon icon, @NotNull Component c, String tip, int index) {
    assert c instanceof JComponent;
    tabs.addTab(new TabInfo((JComponent)c).setText(title).setTooltipText(tip).setIcon(icon), index);
  }

  @Override
  public void setTabPlacement(int tabPlacement) {
    JBTabsPosition position = swingConstantToEnum(tabPlacement);
    tabs.setTabsPosition(position);
  }

  @ApiStatus.Internal
  public static @NotNull JBTabsPosition swingConstantToEnum(int tabPlacement) {
    return switch (tabPlacement) {
      case SwingConstants.TOP -> JBTabsPosition.top;
      case SwingConstants.BOTTOM -> JBTabsPosition.bottom;
      case SwingConstants.LEFT -> JBTabsPosition.left;
      case SwingConstants.RIGHT -> JBTabsPosition.right;
      default -> throw new IllegalArgumentException("Invalid tab placement code=" + tabPlacement);
    };
  }

  @Override
  public void addMouseListener(@NotNull MouseListener listener) {
    tabs.getComponent().addMouseListener(listener);
  }

  @Override
  public int getSelectedIndex() {
    TabInfo tab = tabs.getSelectedInfo();
    return tab == null ? -1 : tabs.getIndexOf(tab);
  }

  @Override
  public Component getSelectedComponent() {
    TabInfo selected = tabs.getSelectedInfo();
    return selected == null ? null : selected.getComponent();
  }

  @Override
  public void setSelectedIndex(int index) {
    tabs.select(getTabAt(index), false);
  }

  @Override
  public Component getTabComponentAt(int index) {
    final TabInfo tabInfo = tabs.getTabAt(index);
    return tabs.getTabLabel(tabInfo);
  }

  @Override
  public void removeTabAt(int index) {
    tabs.removeTab(getTabAt(index));
  }

  private TabInfo getTabAt(int index) {
    checkIndex(index);
    return tabs.getTabAt(index);
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= getTabCount()) {
      throw new ArrayIndexOutOfBoundsException("tabCount=" + getTabCount() + " index=" + index);
    }
  }

  @Override
  public void revalidate() {
    tabs.getComponent().revalidate();
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
    return tabs.isSingleRow() ? JTabbedPane.SCROLL_TAB_LAYOUT : JTabbedPane.WRAP_TAB_LAYOUT;
  }

  @Override
  public void setTabLayoutPolicy(int policy) {
    boolean singleRow = switch (policy) {
      case JTabbedPane.SCROLL_TAB_LAYOUT -> true;
      case JTabbedPane.WRAP_TAB_LAYOUT -> false;
      default -> throw new IllegalArgumentException("Unsupported tab layout policy: " + policy);
    };
    tabs.getPresentation().setSingleRow(singleRow);
  }

  @Override
  public void scrollTabToVisible(int index) {
  }

  @Override
  public @Nls String getTitleAt(int i) {
    return getTabAt(i).getText();
  }

  @Override
  public void removeAll() {
    tabs.removeAllTabs();
  }

  @Override
  public void updateUI() {
    tabs.getComponent().updateUI();
  }

  @Override
  public void removeChangeListener(ChangeListener listener) {
    listeners.remove(listener);
  }

  public @NotNull JBTabs getTabs() {
    return tabs;
  }
}
