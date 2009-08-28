package com.intellij.ui;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.event.MouseListener;
import java.awt.*;

public interface TabbedPane {
  JComponent getComponent();

  void putClientProperty(Object key, Object value);

  void setKeyboardNavigation(PrevNextActionsDescriptor installKeyboardNavigation);

  void addChangeListener(ChangeListener listener);

  int getTabCount();

  void insertTab(String title, Icon icon, Component c, String tip, int index);

  void setTabPlacement(int tabPlacement);

  void addMouseListener(MouseListener listener);

  int getSelectedIndex();

  Component getSelectedComponent();

  void setSelectedIndex(int index);

  void removeTabAt(int index);

  void revalidate();

  Color getForegroundAt(int index);

  void setForegroundAt(int index, Color color);

  Component getComponentAt(int i);

  void setTitleAt(int index, String title);

  void setToolTipTextAt(int index, String toolTipText);

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

  boolean isDisposed();
}