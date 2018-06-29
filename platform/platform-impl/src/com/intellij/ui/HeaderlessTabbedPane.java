// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;

/** @deprecated not particularly useful wrapper around {@link TabbedPaneWrapper} (to be removed in IDEA 2019) */
@SuppressWarnings("ALL")
public class HeaderlessTabbedPane extends JPanel {
  private final TabbedPaneWrapper myTabbedPane;
  private @Nullable JComponent myTabContent;
  private @Nullable String myTabTitle;

  public HeaderlessTabbedPane(Disposable disposable) {
    super(new BorderLayout());
    myTabbedPane = new TabbedPaneWrapper(disposable);
    add(myTabbedPane.getComponent(), BorderLayout.CENTER);
  }

  public void addTab(String title, JComponent component) {
    if (myTabContent != null) {
      throw new IllegalStateException("Please show header before changing tabs");
    }
    myTabbedPane.addTab(title, component);
  }

  public void setSelectedIndex(int index) {
    if (myTabContent != null) {
      if (index != 0) {
        throw new IllegalArgumentException("Invalid index");
      }
      return;
    }
    myTabbedPane.setSelectedIndex(index);
  }

  public int getTabCount() {
    if (myTabContent != null) {
      return 0;
    }
    else {
      return myTabbedPane.getTabCount();
    }
  }

  public void addChangeListener(ChangeListener listener) {
    myTabbedPane.addChangeListener(listener);
  }

  public int indexOfComponent(JComponent component) {
    if (myTabContent != null) {
      return component == myTabContent ? 0 : -1;
    }
    return myTabbedPane.indexOfComponent(component);
  }

  public void removeTabAt(int index) {
    if (myTabContent != null) {
      throw new IllegalStateException("Please show header before changing tabs");
    }
    myTabbedPane.removeTabAt(index);
  }

  public int getSelectedIndex() {
    if (myTabContent != null) {
      return 0;
    }
    else {
      return myTabbedPane.getSelectedIndex();
    }
  }

  public void setHeaderVisible(boolean visible) {
    if (visible == (myTabContent == null)) {
      return;
    }

    if (visible) {
      remove(myTabContent);
      myTabbedPane.addTab(myTabTitle, myTabContent);
      add(myTabbedPane.getComponent());
      myTabTitle = null;
      myTabContent = null;
    }
    else {
      if (myTabbedPane.getTabCount() != 1) {
        throw new IllegalStateException("Please leave one tab before hiding header, now we have " + myTabbedPane.getTabCount() + " tabs");
      }

      add(myTabbedPane.getComponent(), BorderLayout.CENTER);
      myTabContent = myTabbedPane.getComponentAt(0);
      myTabTitle = myTabbedPane.getTitleAt(0);
      myTabbedPane.removeTabAt(0);
      remove(myTabbedPane.getComponent());
      add(myTabContent, BorderLayout.CENTER);
    }
  }
}