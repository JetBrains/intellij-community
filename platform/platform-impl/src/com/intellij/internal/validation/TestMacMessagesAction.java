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
          System.out.println(Messages.showYesNoCancelDialog("Message", "Title", "YesText", "NoText", "CancelText", null));
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

        alertWithButtons(panel, "Show Buttons Alert", new String[]{"Button1", "Button2", "Button3", "Button4", "Button5"});
        alertWithButtons(panel, "Show Buttons with Cancel Alert", new String[]{"Button1", "Cancel", "Button3", "Button4", "Button5"});
        alertWithButtons(panel, "Show Buttons with Cancel Alert", new String[]{"Button1", "Button2", "Button3", "Cancel", "Button5"});
        alertWithButtons(panel, "Show Buttons with Cancel Alert", new String[]{"Button1", "Button2", "Button3", "Button4", "Cancel"});

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


        JButton changeTitleDialog = new JButton("Dialog(dynamic title) -> Alert");
        changeTitleDialog.addActionListener(event -> new DialogWrapper(e.getProject()) {
          {
            setSize(400, 400);
            setTitle("Dialog [0]");
            init();
          }

          @Override
          protected JComponent createCenterPanel() {
            final JButton b = new JButton("Run");
            b.addActionListener(new ActionListener() {
              @Override
              public void actionPerformed(ActionEvent e1) {
                setTitle("Dialog [0]");
                Thread thread = new Thread(() -> {
                  try {
                    for (int i = 0; i < 300; i++) {
                      final int ii = i;
                      SwingUtilities.invokeLater(() -> setTitle("Dialog [" + ii + "]"));
                    }
                  }
                  catch (Exception ignore) {
                  }
                }, "");
                thread.start();
                Messages.showYesNoDialog(b, "Message", "Title", Messages.getQuestionIcon());
                if (thread.isAlive()) {
                  thread.interrupt();
                }
              }
            });
            return b;
          }
        }.show());
        panel.add(changeTitleDialog);

        JButton secondDialog = new JButton("Dialog -> Alert || Modal Dialog (~5sec)");
        secondDialog.addActionListener(event -> {
          new Thread(() -> {
            try {
              if (false) Thread.sleep(5000);
            }
            catch (InterruptedException ignore) {
            }
            SwingUtilities.invokeLater(() -> {
              DialogWrapper dialog = new DialogWrapper(e.getProject()) {
                {
                  setSize(400, 400);
                  setTitle("Dialog");
                  init();
                }

                @Override
                protected JComponent createCenterPanel() {
                  return new JCheckBox("Check me!");
                }
              };
              dialog.setModal(true);
              dialog.show();
            });
          }, "").start();
          Messages.showYesNoDialog(secondDialog, "Message", "Title", Messages.getQuestionIcon());
        });
        panel.add(secondDialog);

        JButton secondAlert = new JButton("Dialog -> Alert || Alert (~5sec)");
        secondAlert.addActionListener(event -> {
          new Thread(() -> {
            try {
              Thread.sleep(5000);
            }
            catch (InterruptedException ignore) {
            }
            SwingUtilities.invokeLater(() -> Messages.showInfoMessage("Message", "Title"));
          }, "").start();
          Messages.showYesNoDialog(secondAlert, "Message", "Title", Messages.getQuestionIcon());
        });
        panel.add(secondAlert);

        JButton html = new JButton("Show Alert with HTML content");
        html.addActionListener(event -> {
          Messages.showInfoMessage(
            "<html>Message <a>Message</a> Message<br>Test <b>Test</b> Test<br>&nbsp;&nbsp;&nbsp;Foo &lt;&gt;&amp;&#39;&quot; Foo</html>",
            "Title");
        });
        panel.add(html);

        return panel;
      }
    }.show();
  }

  private static void alertWithButtons(JPanel panel, String title, String[] buttons) {
    JButton button = new JButton(title);
    button.addActionListener(event -> {
      System.out.println(Messages.showDialog(button, "Message", "Title", buttons, 0, null));
    });
    panel.add(button);
  }
}
