// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseListener;

public interface TabbedPane {
  JComponent getComponent();

  void putClientProperty(@NotNull Object key, Object value);

  void setKeyboardNavigation(@NotNull PrevNextActionsDescriptor installKeyboardNavigation);

  void addChangeListener(@NotNull ChangeListener listener);

  int getTabCount();

  void insertTab(@NlsContexts.TabTitle @NotNull String title,
                 Icon icon,
                 @NotNull Component c,
                 @NlsContexts.Tooltip String tip,
                 int index);

  void setTabPlacement(int tabPlacement);

  void addMouseListener(@NotNull MouseListener listener);

  int getSelectedIndex();

  Component getSelectedComponent();

  void setSelectedIndex(int index);

  void removeTabAt(int index);

  void revalidate();

  Color getForegroundAt(int index);

  void setForegroundAt(int index, Color color);

  Component getComponentAt(int i);

  Component getTabComponentAt(int index);

  void setTitleAt(int index, @NlsContexts.TabTitle @NotNull String title);

  void setToolTipTextAt(int index, @NlsContexts.Tooltip String toolTipText);

  void setComponentAt(int index, Component c);

  void setIconAt(int index, Icon icon);

  void setEnabledAt(int index, boolean enabled);

  int getTabLayoutPolicy();

  void setTabLayoutPolicy(int policy);

  void scrollTabToVisible(int index);

  String getTitleAt(int i);

  void removeAll();

  void updateUI();

  void removeChangeListener(ChangeListener listener);
}