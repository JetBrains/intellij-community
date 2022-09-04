// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ShowColorPickerAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Window root = parent();
    if (root != null) {
      List<ColorPickerListener> listeners = ColorPickerListenerFactory.createListenersFor(e.getData(CommonDataKeys.PSI_ELEMENT));
      ColorPicker.ColorPickerDialog picker = new ColorPicker.ColorPickerDialog(root, IdeBundle.message("dialog.title.color.picker"), null, true, listeners, true);
      picker.setModal(false);
      picker.show();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    if (component == null || !(SwingUtilities.getWindowAncestor(component) instanceof Frame)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private static Window parent() {
    Window activeWindow = null;
    for (Window w : Window.getWindows()) {
      if (w.isActive()) {activeWindow = w;}
    }
    return activeWindow;
  }
}