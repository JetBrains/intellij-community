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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ShowProgressTestDialogAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    new MyDialogWrapper(e.getProject()).show();
  }

  private static class MyDialogWrapper extends DialogWrapper {
    private final List<JProgressBar> pbList = new ArrayList<>();
    private final Alarm alarm = new Alarm(getDisposable());

    public MyDialogWrapper(Project project) {
      super(project);
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

      panel.add(createPanel(false, null, false));
      panel.add(createPanel(false, ColorProgressBar.RED, false));
      panel.add(createPanel(false, ColorProgressBar.GREEN, false));
      panel.add(createPanel(false, null, true));
      panel.add(createPanel(false, ColorProgressBar.RED, true));
      panel.add(createPanel(false, ColorProgressBar.GREEN, true));
      panel.add(createPanel(true, null, false));
      panel.add(createPanel(true, null, true));
      panel.add(createPanel(true, ColorProgressBar.RED, false));
      panel.add(createPanel(true, ColorProgressBar.GREEN, false));
      panel.add(createPanel(true, ColorProgressBar.RED, true));
      panel.add(createPanel(true, ColorProgressBar.GREEN, true));

      for(JProgressBar pb : pbList) {
        if (!pb.isIndeterminate()) {
          Runnable request = new Runnable() {
            @Override public void run() {
              if (pb.getValue() < pb.getMaximum()) {
                pb.setValue(pb.getValue() + 1);
                alarm.addRequest(this, 100);
              }
            }
          };
          alarm.addRequest(request, 200, ModalityState.any());
        }
      }

      return panel;
    }

    private JComponent createPanel(boolean indeterminate, Color foreground, boolean modeless) {
      String text = (indeterminate ? "indeterminate" : "determinate");
      JLabel label = new JLabel(text);

      JProgressBar progress = new JProgressBar(0, 100);
      progress.setIndeterminate(indeterminate);
      progress.setValue(0);
      progress.setForeground(foreground);

      if (modeless) {
        progress.putClientProperty("ProgressBar.stripeWidth", 2);
      }

      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      panel.add(label);
      panel.add(progress);
      panel.add(Box.createVerticalStrut(5));

      pbList.add(progress);

      return panel;
    }
  }
}
