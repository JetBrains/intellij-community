// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public abstract class ReloadableListPanel<T> extends ReloadablePanel<T> {
  protected JList<T> myList;
  private JPanel myMainPanel;
  private JPanel myActionPanel;

  @Override
  public T getSelectedValue() {
    return myList.getSelectedValue();
  }

  protected void createList() {
    myList = new JBList<>();
  }

  @Override
  public @NotNull JPanel getMainPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    myActionPanel = getActionPanel();
    createList();
  }
}
