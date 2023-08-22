// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class ReloadableComboBoxPanel<T> extends ReloadablePanel<T> {
  protected JComboBox<T> myComboBox;
  protected JPanel myActionPanel;
  private JPanel myMainPanel;

  @Override
  @SuppressWarnings("unchecked")
  public T getSelectedValue() {
    return (T)myComboBox.getSelectedItem();
  }

  protected @NotNull JComboBox<T> createValuesComboBox() {
    return new ComboBox<>();
  }

  @Override
  public @NotNull JPanel getMainPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    myComboBox = createValuesComboBox();
    myActionPanel = getActionPanel();
  }
}
