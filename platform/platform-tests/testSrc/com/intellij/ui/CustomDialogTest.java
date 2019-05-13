// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;

public class CustomDialogTest {
  public static void main(String[] args) {
    final JFrame frame = new JFrame("Custom Dialog Test");
    frame.setLocation(0, 0);
    frame.setSize(800, 600);
    frame.setVisible(true);
    final JDialog dialog = new JDialog(frame, true);
    dialog.setSize(400, 200);
    dialog.setUndecorated(true);
    Container contentPane = dialog.getContentPane();
    contentPane.setLayout(new BorderLayout());
    JButton button = new JButton("Minimize");
    button.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          dialog.setVisible(false);
          frame.setExtendedState(JFrame.ICONIFIED);
        }
      }
    );
    contentPane.add(button, BorderLayout.CENTER);
    dialog.setVisible(true);
    frame.addWindowStateListener(
      new WindowStateListener() {
        @Override
        public void windowStateChanged(WindowEvent e) {
          if (e.getOldState() == JFrame.ICONIFIED && (e.getNewState() == JFrame.NORMAL || e.getNewState() == JFrame.MAXIMIZED_BOTH)) {
            dialog.setVisible(true);
          }
        }
      }
    );
  }
}
