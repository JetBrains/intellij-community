// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.focus;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class DumpFocusableComponentHierarchyAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Util.drawOnActiveFrameGraphics(g -> {

      Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      Component focusedComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

      int y = 0;
      int FONT_HEIGHT = 30;

      g.setColor(Color.BLACK);
      g.fillRect(0,0, activeWindow.getBounds().width, activeWindow.getBounds().height);
      g.setColor(Color.WHITE);
      g.drawString("Active Window: " + activeWindow.getClass().getName(), 0, FONT_HEIGHT*y++);
      g.drawString("Focused Window: " + focusedWindow.getClass().getName(), 0, FONT_HEIGHT*y++);
      g.drawString("Focused Component: " + focusedComponent.getClass().getName(), 0, 60);

      ArrayList<Component> componentTrace = new ArrayList<>();

      Component c = focusedComponent;

      while (c != null) {
        componentTrace.add(c);
        c = c.getParent();
      }

      y++;

      for (int i = componentTrace.size() - 1 ; i >= 0; i--) {
        g.drawString(componentTrace.get(i).getClass().getName(), 0, FONT_HEIGHT*y++);
        g.drawString("^", 0, FONT_HEIGHT*y + 15);
      }

      g.drawString("Children count in focused component: " + ((JComponent)focusedComponent).getComponentCount(), 0, FONT_HEIGHT * y);

    });
  }
}
