/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.internal.validation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ComboboxWithBrowseButton;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Alexander Lobas
 */
public class TestCombo extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    new DialogWrapper(getEventProject(e)) {
      {
        setTitle("Test Combo");
        setSize(400, 300);
        init();
      }

      @Nullable
      @Override
      protected JComponent createCenterPanel() {
        JPanel panel = new JPanel();

        DefaultComboBoxModel model = new DefaultComboBoxModel();
        model.addElement("111111111");
        model.addElement("2222222");
        model.addElement("33333333333");
        model.addElement("4444444");
        model.addElement("555");

        final ComboBox comboBox = new ComboBox();
        comboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE); // TODO: AAAAAAAAAAA!!!!111
        comboBox.setModel(model);
        comboBox.setEditable(true);
        comboBox.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            System.out.println(comboBox.getSelectedItem());
          }
        });

        panel.add(new ComboboxWithBrowseButton(comboBox));

        return panel;
      }
    }.show();
  }
}