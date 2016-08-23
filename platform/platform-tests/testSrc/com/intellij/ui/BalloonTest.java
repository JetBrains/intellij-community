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
package com.intellij.ui;

import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public class BalloonTest {
  public static void main(String[] args) {
    IconLoader.activate();

    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout());
    final JPanel content = new JPanel(new BorderLayout());
    frame.getContentPane().add(content, BorderLayout.CENTER);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    final JTree tree = new Tree();
    content.add(tree);

    final Ref<BalloonImpl> balloon = new Ref<>();

    tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        if (balloon.get() != null && balloon.get().isVisible()) {
          //noinspection SSBasedInspection
          balloon.get().dispose();
        }
        else {
          //JLabel pane1 = new JLabel("Hello, world!");
          //JLabel pane2 = new JLabel("Hello, again");
          //JPanel pane = new JPanel(new BorderLayout());
          //pane.add(pane1, BorderLayout.CENTER);
          //pane.add(pane2, BorderLayout.SOUTH);
          //pane.setBorder(new LineBorder(Color.blue));

          balloon.set(new BalloonImpl(
            new JLabel("Content"), Color.black, null , MessageType.ERROR.getPopupBackground(), true, true, true, true, true, true, 0, true, false, null,
            false, 500, 25, 0, 0, false, "This is the title", JBUI.insets(2), true, false, false, Balloon.Layer.normal, false));
          balloon.get().setShowPointer(true);

          if (e.isShiftDown()) {
            balloon.get().show(new RelativePoint(e), Balloon.Position.above);
          }
          else if (e.isAltDown()) {
            balloon.get().show(new RelativePoint(e), Balloon.Position.below);
          }
          else if (e.isMetaDown()) {
            balloon.get().show(new RelativePoint(e), Balloon.Position.atLeft);
          }
          else {
            balloon.get().show(new RelativePoint(e), Balloon.Position.atRight);
          }
        }
      }
    });

    tree.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        System.out.println(e.getPoint());
      }
    });

    frame.setBounds(300, 300, 300, 300);
    frame.setVisible(true);
  }
}
