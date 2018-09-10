// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.focus;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
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

    dump.add("Active Window: " +  (activeWindow == null ? "null" : activeWindow.getClass().getName()));
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

    int FONT_HEIGHT = 30;

    JPanel jPanel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(JBColor.BLACK);
        g.fillRect(0, 0, getBounds().width, getBounds().height);
        g.setColor(JBColor.WHITE);

        for (int i = dump.size() - 1; i >= 0; i --) {
          g.drawString(dump.get(i), 20, 50 + (i * FONT_HEIGHT));
        }
      }
    };

    jPanel.setPreferredSize(new Dimension(visibleFrame.getWidth(), dump.size() * FONT_HEIGHT + 200));

    JScrollPane scrollPane = new JBScrollPane(jPanel);
    scrollPane.setPreferredSize(visibleFrame.getSize());

    Popup popup = PopupFactory.getSharedInstance().getPopup(visibleFrame, scrollPane, visibleFrame.getX(), visibleFrame.getY());
    popup.show();

    JButton closeButton = new JButton(new AbstractAction("Close and copy into the clipboard") {
      @Override
      public void actionPerformed(ActionEvent e) {
        StringBuilder dumpAsString = new StringBuilder();
        for (int i = dump.size() - 1; i >= 0; i--) {
          dumpAsString.append(dump.get(i)).append("\n");
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(dumpAsString.toString()), null);
        popup.hide();
      }
    });

    jPanel.add(closeButton, BorderLayout.SOUTH);
  }
}
