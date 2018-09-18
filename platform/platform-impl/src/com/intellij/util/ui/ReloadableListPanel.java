// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class ReloadableListPanel<T> extends ReloadablePanel<T> {
  protected JList myList;
  private JPanel myMainPanel;
  private JPanel myActionPanel;

  @Override
  @SuppressWarnings("unchecked")
  public T getSelectedValue() {
    return (T)myList.getSelectedValue();
  }

  protected void createList() {
    myList = new JBList();
  }

  @NotNull
  @Override
  public JPanel getMainPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    myActionPanel = getActionPanel();
    createList();
  }
}
