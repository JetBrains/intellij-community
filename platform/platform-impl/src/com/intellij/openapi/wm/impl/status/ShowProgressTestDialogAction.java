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
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBProgressBar;
import com.intellij.ui.LightColors;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ShowProgressTestDialogAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    new MyDialogWrapper(e.getProject()).show();
  }

  private static class MyDialogWrapper extends DialogWrapper {
    public MyDialogWrapper(Project project) {
      super(project);
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

      panel.add(createPanel(false, false));
      panel.add(createPanel(false, true));
      panel.add(createPanel(true, false));
      panel.add(createPanel(true, true));
      return panel;
    }
  }

  private static JComponent createPanel(boolean indeterminate, boolean opaque) {
    String text = (indeterminate ? "indeterminate" : "determinate") +
                  (opaque ? "; opaque" : "; non opaque");
    JLabel label = new JLabel(text);

    JBProgressBar progress = new JBProgressBar();
    progress.setIndeterminate(indeterminate);
    progress.setValue(30);
    progress.setOpaque(opaque);

    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(progress);
    wrapper.setBackground(LightColors.BLUE);

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(label);
    panel.add(wrapper);
    panel.add(Box.createVerticalStrut(5));

    return panel;
  }
}
