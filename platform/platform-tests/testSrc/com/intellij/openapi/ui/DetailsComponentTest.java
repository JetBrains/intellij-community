// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.ui.tree.TreeTestUtil;
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
    TreeTestUtil.assertTreeUI(c);
    c.setBorder(new LineBorder(Color.red));
    d.setContent(c);

    frame.getContentPane().add(content, BorderLayout.CENTER);
    final JCheckBox details = new JCheckBox("Details");
    details.setSelected(true);
    frame.getContentPane().add(details, BorderLayout.SOUTH);
    details.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        d.setDetailsModeEnabled(details.isSelected());
      }
    });


    frame.setBounds(300, 300, 300, 300);
    frame.show();
  }
}
