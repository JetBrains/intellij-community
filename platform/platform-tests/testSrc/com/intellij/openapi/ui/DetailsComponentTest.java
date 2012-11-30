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
package com.intellij.openapi.ui;

import com.intellij.ui.treeStructure.Tree;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DetailsComponentTest {
  public static void main(String[] args) {
    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout());
    final JPanel content = new JPanel(new BorderLayout());

    final DetailsComponent d = new DetailsComponent();
    content.add(d.getComponent(), BorderLayout.CENTER);

    d.setText("This is a Tree");
    final JTree c = new Tree();
    c.setBorder(new LineBorder(Color.red));
    d.setContent(c);

    frame.getContentPane().add(content, BorderLayout.CENTER);
    final JCheckBox details = new JCheckBox("Details");
    details.setSelected(true);
    frame.getContentPane().add(details, BorderLayout.SOUTH);
    details.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        d.setDetailsModeEnabled(details.isSelected());
      }
    });


    frame.setBounds(300, 300, 300, 300);
    frame.show();
  }
}
