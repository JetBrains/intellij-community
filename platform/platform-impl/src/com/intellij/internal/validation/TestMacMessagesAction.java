/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.internal.validation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.panels.VerticalLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Konstantin Bulenkov
 */
public class TestMacMessagesAction extends AnAction {
  static int num = 1;
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    new DialogWrapper(e.getProject()) {
      {
        setSize(500, 500);
        setTitle("Dialog 1");
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new VerticalLayout(5));

        JButton yesNoCancel = new JButton("Show YesNoCancel Alert");
        yesNoCancel.addActionListener(event -> {
          Messages.showYesNoCancelDialog("Message", "Title", "YesText", "NoText", "CancelText", null);
        });
        panel.add(yesNoCancel);

        JButton yesNo = new JButton("Show YesNo Alert");
        yesNo.addActionListener(event -> {
          Messages.showYesNoDialog(null, "Message", "Title", Messages.getQuestionIcon(), new DoNotAskOption() {
            @Override
            public boolean isToBeShown() {
              return false;
            }

            @Override
            public void setToBeShown(boolean toBeShown, int exitCode) {
            }

            @Override
            public boolean canBeHidden() {
              return true;
            }

            @Override
            public boolean shouldSaveOptionsOnCancel() {
              return false;
            }

            @Override
            public @NotNull @NlsContexts.Checkbox String getDoNotShowMessage() {
              return "Do not ask me again";
            }
          });
        });
        panel.add(yesNo);

        JButton ok = new JButton("Show Ok Alert");
        ok.addActionListener(event -> {
          Messages.showInfoMessage("Message", "Title");
        });
        panel.add(ok);

        JButton error = new JButton("Show Error Alert");
        error.addActionListener(event -> {
          Messages.showErrorDialog((Project)null, "Message", "Title");
        });
        panel.add(error);

        JButton buttons = new JButton("Show Buttons Alert");
        buttons.addActionListener(event -> {
          System.out.println(Messages.showDialog(buttons, "Message", "Title", new String[]{"Button1", "Button2", "Button3", "Button4", "Button5"}, 0, null));
        });
        panel.add(buttons);

        JButton dialogAlert = new JButton("Dialog -> YesNo Alert");
        dialogAlert.addActionListener(event -> new DialogWrapper(e.getProject()) {
          {
            setSize(400, 400);
            setTitle("Dialog 2");
            init();
          }
          @Override
          protected JComponent createCenterPanel() {
            final JButton b = new JButton("Click me again " + num);
            num++;
            b.addActionListener(new ActionListener() {
              @Override
              public void actionPerformed(ActionEvent e1) {
                Messages.showYesNoDialog(b, "Message", "Title", Messages.getQuestionIcon());
              }
            });
            return b;
          }
        }.show());
        panel.add(dialogAlert);

        return panel;
      }
    }.show();
  }
}
