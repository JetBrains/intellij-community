/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class ShowColorPickerAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    JComponent root = rootComponent(project);
    if (root != null) {
      ColorPickerListener[] listeners = ColorPickerListenerFactory.createListenersFor(e.getData(CommonDataKeys.PSI_ELEMENT));
      final ColorPicker.ColorPickerDialog picker =
        new ColorPicker.ColorPickerDialog(root, "Color Picker", null, true, listeners, true);
      picker.setModal(false);
      picker.show();
    }
  }

  private static JComponent rootComponent(Project project) {
    if (project != null) {
      IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
      if (frame != null) return frame.getComponent();
    }

    JFrame frame = WindowManager.getInstance().findVisibleFrame();
    return frame != null ? frame.getRootPane() : null;
  }
}
