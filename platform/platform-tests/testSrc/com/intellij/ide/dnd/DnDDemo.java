// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.tree.TreeTestUtil;
import com.intellij.ui.treeStructure.Tree;

import javax.swing.*;
import java.awt.*;

public class DnDDemo implements DnDEvent.DropTargetHighlightingType {
  public static void main(String[] args) {

    JFrame frame = new JFrame("DnD Demo");
    frame.getContentPane().setLayout(new BorderLayout());

    JPanel panel = new JPanel(new BorderLayout());
    final JTree source = new Tree();
    TreeTestUtil.assertTreeUI(source);
    panel.add(source, BorderLayout.WEST);
    final DnDManager dndManager = new DnDManagerImpl();
    dndManager.registerSource(new DnDSource() {
      @Override
      public boolean canStartDragging(DnDAction action, Point dragOrigin) {
        return true;
      }

      @Override
      public DnDDragStartBean startDragging(DnDAction action, Point point) {
        return new DnDDragStartBean(source.getLastSelectedPathComponent().toString());
      }
    }, source);


    JTabbedPane tabs = new JBTabbedPane();

    JPanel delegates = new JPanel(new FlowLayout());
    final JLabel delegate1Label = new JLabel("Delegate 1");
    delegates.add(delegate1Label);
    final JLabel delegate2Label = new JLabel("Delegate 2");
    delegates.add(delegate2Label);
    final DnDTarget delegee1 = new DnDTarget() {
      @Override
      public boolean update(DnDEvent aEvent) {
        aEvent.setDropPossible(true, "Delegee 1");
        aEvent.setHighlighting(delegate1Label, H_ARROWS | RECTANGLE);
        return false;
      }

      @Override
      public void drop(DnDEvent aEvent) {
        System.out.println("Delegee 1 accepted drop");
      }
    };

    final DnDTarget delegee2 = new DnDTarget() {
      @Override
      public boolean update(DnDEvent aEvent) {
        aEvent.setDropPossible("Delegee 2", new DropActionHandler() {
          @Override
          public void performDrop(DnDEvent aEvent) {
            System.out.println("Delegee 2 accepted drop");
          }
        });
        aEvent.setHighlighting(delegate2Label, V_ARROWS | RECTANGLE);
        return false;
      }

      @Override
      public void drop(DnDEvent aEvent) {

      }
    };

    dndManager.registerTarget(new DnDTarget() {
      @Override
      public boolean update(DnDEvent aEvent) {
        if (aEvent.getCurrentOverComponent() == delegate1Label) {
          return aEvent.delegateUpdateTo(delegee1);
        } else if (aEvent.getCurrentOverComponent() == delegate2Label) {
          return aEvent.delegateUpdateTo(delegee2);
        }

        aEvent.setDropPossible(false, "Nothing can be dropped here");
        return false;
      }

      @Override
      public void drop(DnDEvent aEvent) {
        if (aEvent.getCurrentOverComponent() == delegate1Label) {
          aEvent.delegateDropTo(delegee1);
        }
      }
    }, delegates);

    tabs.add("Delegates", delegates);

    final JPanel xy = new JPanel();
    dndManager.registerTarget(new DnDTarget() {
      @Override
      public boolean update(DnDEvent aEvent) {
        aEvent.setDropPossible(true, "Drop to " + asXyString(aEvent));
        return false;
      }

      @Override
      public void drop(DnDEvent aEvent) {
        System.out.println("Droppped to " + asXyString(aEvent));
      }
    }, xy);

    tabs.add("XY drop", xy);

    panel.add(tabs, BorderLayout.CENTER);

    frame.getContentPane().add(panel, BorderLayout.CENTER);
    frame.setBounds(100, 100, 500, 500);
    frame.show();
  }

  public static String asXyString(DnDEvent aEvent) {
    return "[" + aEvent.getPoint().x + "," + aEvent.getPoint().y + "]";
  }
}