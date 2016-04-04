/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ShowColorPickerAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Window root = parent();
    if (root != null) {
      List<ColorPickerListener> listeners = ColorPickerListenerFactory.createListenersFor(e.getData(CommonDataKeys.PSI_ELEMENT));
      ColorPicker.ColorPickerDialog picker = new ColorPicker.ColorPickerDialog(root, "Color Picker", null, true, listeners, true);
      picker.setModal(false);
      picker.show();
    }
  }

  @Override
  public void update(AnActionEvent e) {
    Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());
    if (component == null || !(SwingUtilities.getWindowAncestor(component) instanceof Frame)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(true);
  }

  private static Window parent() {
    Window activeWindow = null;
    for (Window w : Window.getWindows()) {
      if (w.isActive()) {activeWindow = w;};
    }
    return activeWindow;
  }
}
