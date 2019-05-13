// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class ReloadableComboBoxPanel<T> extends ReloadablePanel<T> {
  protected JComboBox myComboBox;
  protected JPanel myActionPanel;
  private JPanel myMainPanel;

  @Override
  @SuppressWarnings("unchecked")
  public T getSelectedValue() {
    return (T)myComboBox.getSelectedItem();
  }

  @NotNull
  protected JComboBox createValuesComboBox() {
    return new ComboBox();
  }

  @NotNull
  @Override
  public JPanel getMainPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    myComboBox = createValuesComboBox();
    myActionPanel = getActionPanel();
  }
}
