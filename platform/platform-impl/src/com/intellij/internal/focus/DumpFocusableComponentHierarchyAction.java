// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.focus;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public class DumpFocusableComponentHierarchyAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {

    JFrame visibleFrame = WindowManager.getInstance().findVisibleFrame();
    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    Component focusedComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    CopyOnWriteArrayList<String> dump = new CopyOnWriteArrayList<>();

    dump.add("Active Window: " +  activeWindow.getClass().getName());
    dump.add("Focused Window: " + (focusedWindow == null ? "null" : focusedWindow.getClass().getName()));
    dump.add("Focused Component: " + (focusedComponent == null ? "null" : focusedComponent.getClass().getName()));

    ArrayList<Component> componentTrace = new ArrayList<>();

    Component c = focusedComponent;

    while (c != null) {
      componentTrace.add(c);
      c = c.getParent();
    }

    for (int i = componentTrace.size() - 1; i >= 0; i--) {
      dump.add(componentTrace.get(i).getClass().getName());
      if (i != 0) {
        dump.add("^");
      }
    }

    dump.add("Children count in focused component: " + (focusedComponent == null ? "null" : ((JComponent)focusedComponent).getComponentCount()));

    JPanel jPanel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int FONT_HEIGHT = 30;

        g.setColor(JBColor.BLACK);
        g.fillRect(0, 0, activeWindow.getBounds().width, activeWindow.getBounds().height);
        g.setColor(JBColor.WHITE);

        for (int i = dump.size() - 1; i >= 0; i --) {
          g.drawString(dump.get(i), 20, 50 + (i * FONT_HEIGHT));
        }
      }
    };

    jPanel.setPreferredSize(visibleFrame.getSize());

    Popup popup = PopupFactory.getSharedInstance().getPopup(visibleFrame, jPanel, visibleFrame.getX(), visibleFrame.getY());
    popup.show();

    jPanel.add(new JButton(new AbstractAction("Close and copy into the clipboard") {
      @Override
      public void actionPerformed(ActionEvent e) {
        StringBuilder dumpAsString = new StringBuilder();
        for (int i = dump.size() - 1; i >= 0; i --) {
          dumpAsString.append(dump.get(i)).append("\n");
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(dumpAsString.toString()), null);
        popup.hide();
      }
    }), BorderLayout.SOUTH);
  }
}
