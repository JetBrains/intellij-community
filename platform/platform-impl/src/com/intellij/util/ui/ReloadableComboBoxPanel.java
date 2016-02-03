/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class ReloadableComboBoxPanel<T> extends ReloadablePanel<T> {
  protected JComboBox myComboBox;
  protected JPanel myActionPanel;
  private JPanel myMainPanel;

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
