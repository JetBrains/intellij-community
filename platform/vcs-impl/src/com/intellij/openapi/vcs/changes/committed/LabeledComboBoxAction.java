/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public abstract class LabeledComboBoxAction extends AnAction implements CustomComponentAction {

  @NotNull private final JLabel myLabel;
  @Nullable private JPanel myPanel;
  @NotNull private final ComboBox myComboBox;

  protected LabeledComboBoxAction(@NotNull String label) {
    myComboBox = new ComboBox();
    myLabel = new JLabel(label);
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
  }

  @NotNull
  public JComponent createCustomComponent(Presentation presentation) {
    if (myPanel == null) {
      myPanel = new JPanel(new BorderLayout());
      myPanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
      myPanel.add(myLabel, BorderLayout.WEST);
      myComboBox.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          selectionChanged(myComboBox.getSelectedItem());
        }
      });
      myComboBox.setModel(createModel());
      myPanel.add(myComboBox, BorderLayout.CENTER);
    }
    return myPanel;
  }

  protected abstract void selectionChanged(Object selection);

  @NotNull
  protected abstract ComboBoxModel createModel();

  @NotNull
  protected JComboBox getComboBox() {
    return myComboBox;
  }
}
