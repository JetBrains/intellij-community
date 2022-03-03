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

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.newItemPopup.NewItemPopupUtil;
import com.intellij.ide.ui.newItemPopup.NewItemSimplePopupPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;

/**
 * @author Konstantin Bulenkov
 */
public class TestMacMessagesAction extends AnAction {
  static int num = 1;
  static String TITLE = "Title";
  static String MESSAGE = "Message";
  static String DONT_ASK_TEXT = "Do not ask me again";

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getProject();

    new DialogWrapper(project) {
      {
        setSize(500, 500);
        setTitle("Dialog 1");
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new VerticalLayout(5));

        JLabel titleLabel = new JLabel("Title:");
        JTextField titleField = new JTextField(TITLE);
        Insets insets = titleField.getBorder().getBorderInsets(titleField);
        titleField.getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          protected void textChanged(@NotNull DocumentEvent e) {
            TITLE = titleField.getText();
          }
        });
        titleLabel.setLabelFor(titleField);

        JPanel titlePanel = new BorderLayoutPanel(5, 5);
        titlePanel.add(titleLabel, BorderLayout.WEST);
        titlePanel.add(titleField, BorderLayout.CENTER);
        panel.add(titlePanel);

        JLabel messageLabel = new JLabel("Message:");
        JTextArea messageArea = new JTextArea(MESSAGE, 3, 30);
        messageArea.getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          protected void textChanged(@NotNull DocumentEvent e) {
            MESSAGE = messageArea.getText();
          }
        });
        messageLabel.setLabelFor(messageArea);

        panel.add(createMessagePanel(insets, messageLabel, messageArea));

        JLabel dontAskLabel = new JLabel("Don't ask text:");
        JTextArea dontAskArea = new JTextArea(DONT_ASK_TEXT, 3, 30);
        dontAskArea.getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          protected void textChanged(@NotNull DocumentEvent e) {
            DONT_ASK_TEXT = dontAskArea.getText();
          }
        });
        dontAskLabel.setLabelFor(dontAskArea);

        panel.add(createMessagePanel(insets, dontAskLabel, dontAskArea));

        JButton yesNoCancel = new JButton("Show YesNoCancel Alert");
        yesNoCancel.addActionListener(event -> {
          System.out.println(Messages.showYesNoCancelDialog(MESSAGE, TITLE, "YesText", "NoText", "CancelText", null));
        });
        panel.add(yesNoCancel);

        JButton yesNo1 = new JButton("Show YesNo Alert");
        yesNo1.addActionListener(event -> {
          Messages.showYesNoDialog((Project)null, MESSAGE, TITLE, Messages.getQuestionIcon());
        });
        panel.add(yesNo1);

        JButton yesNo2 = new JButton("Show YesNo Alert with DoNotAsk");
        yesNo2.addActionListener(event -> {
          DoNotAskOption option = createDoNotAskOption(DONT_ASK_TEXT);
          Messages.showYesNoDialog(null, MESSAGE, TITLE, Messages.getQuestionIcon(), option);
        });
        panel.add(yesNo2);

        JButton ok = new JButton("Show Ok Alert");
        ok.addActionListener(event -> {
          Messages.showInfoMessage(ok, MESSAGE, TITLE);
        });
        panel.add(ok);

        JButton warn = new JButton("Show Warning Alert");
        warn.addActionListener(event -> {
          Messages.showWarningDialog((Project)null, MESSAGE, TITLE);
        });
        panel.add(warn);

        JButton error = new JButton("Show Error Alert");
        error.addActionListener(event -> {
          Messages.showErrorDialog((Project)null, MESSAGE, TITLE);
        });
        panel.add(error);

        JButton help = new JButton("Show Alert with help button");
        help.addActionListener(event -> {
          MessageDialogBuilder.yesNoCancel(TITLE, MESSAGE).help("my.help.id").doNotAsk(createDoNotAskOption(DONT_ASK_TEXT)).show(project);
        });
        panel.add(help);


        alertWithButtons(panel, "Show Buttons with Cancel Alert", new String[]{"Button1", "Cancel", "Button3", "Button4", "Button5"}, 1);
        alertWithButtons(panel, "Show Buttons with Cancel Alert", new String[]{"Button1", "Button2", "Button3", "Cancel", "Button5"}, 2);
        alertWithButtons(panel, "Show Buttons with Cancel Alert", new String[]{"Button1", "Button2", "Button3", "Button4", "Cancel"}, 4);

        JButton dialogAlert = new JButton("Dialog -> YesNo Alert");
        dialogAlert.addActionListener(event -> new DialogWrapper(project) {
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
                getWindow().setVisible(false);
                Messages.showYesNoDialog(b, MESSAGE, TITLE, Messages.getQuestionIcon());
              }
            });
            return b;
          }
        }.show());
        panel.add(dialogAlert);


        JButton changeTitleDialog = new JButton("Dialog(dynamic title) -> Alert");
        changeTitleDialog.addActionListener(event -> new DialogWrapper(project) {
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
                }, "Dialog [0]");
                thread.start();
                Messages.showYesNoDialog(b, MESSAGE, TITLE, Messages.getQuestionIcon());
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
              DialogWrapper dialog = new DialogWrapper(project) {
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
          }, secondDialog.getText()).start();
          Messages.showYesNoDialog(secondDialog, MESSAGE, TITLE, Messages.getQuestionIcon());
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
          }, secondAlert.getText()).start();
          Messages.showYesNoDialog(secondAlert, MESSAGE, TITLE, Messages.getQuestionIcon());
        });
        panel.add(secondAlert);

        JButton html = new JButton("Show Alert with HTML content");
        html.addActionListener(event -> {
          Messages.showDialog(
            "<html>Message <a>Message</a> Message<br>Test <b>Test</b> Test<br>&nbsp;&nbsp;&nbsp;Foo &lt;&gt;&amp;&#39;&quot; Foo</html>",
            "<html>Title <b>AA</b></html>", new String[]{"Y&es", "<html>ZZZZ</html>", "Cancel"}, 0, null, new DoNotAskOption() {
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
              public @NotNull String getDoNotShowMessage() {
                return "<html>Delete branch:</br>EE-F1-0A</html>";
              }
            });
        });
        panel.add(html);

        JButton decompiler = new JButton("Decompiler");
        decompiler.addActionListener(event -> {
          System.out.println(Messages.showDialog(decompiler,
                                                 "IMPORTANT: BY ACCESSING AND USING JETBRAINS DECOMPILER, YOU AGREE TO THE CERTAIN TERMS AND CONDITIONS SET FORTH IN THE END-USER LICENSE AGREEMENT AND QUOTED BELOW. IF YOU DO NOT AGREE WITH THESE TERMS OR CONDITIONS, DO NOT ACCESS OR USE JETBRAINS DECOMPILER. The Software includes decompiling functionality (\"\"JetBrains Decompiler\"\") that enables reproducing source code from the original binary code. Licensee aknowledges that binary code and source code might be protected by copyright and trademark laws. Before using JetBrains Decompiler, Licensee should make sure that decompilation of binary code is not prohibited by the applicable license agreement (except to the extent that Licensee may be expressly permitted under applicable law) or that Licensee has obtained permission to decompile the binary code from the copyright owner. Using JetBrains Decompiler is entirely optional. Licensor does neither encourage nor condone the use of JetBrains Decompiler, and disclaims any liability for Licensee's use of  JetBrains Decompiler in violation of applicable laws.",
                                                 "Decompiler Legal Notice — Accept", new String[]{"Yes", "No"}, 0, AllIcons.General.Tip));
        });
        panel.add(decompiler);

        JButton progress = new JButton("Progress");
        progress.addActionListener(event -> {
          if (Messages.showYesNoDialog("Continue?", TITLE, null) != Messages.YES) {
            return;
          }
          Runnable runnable = () -> {
            ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
            Semaphore done = new Semaphore();
            done.down();

            ApplicationManager.getApplication().executeOnPooledThread(() -> {
              try {
                int count = new Random(System.currentTimeMillis()).nextInt(10);
                indicator.setText("Count: " + count);
                for (int i = 0; i < count; i++) {
                  Thread.sleep(1000);
                  indicator.setText("Count: " + i + "/" + count);
                }
              }
              catch (InterruptedException exception) {
                exception.printStackTrace();
              }
              finally {
                done.up();
              }
            });

            while (!done.waitFor(1000)) {
              if (indicator.isCanceled()) {
                break;
              }
            }
            ApplicationManager.getApplication().invokeAndWait(() -> Messages.showInfoMessage("Finish11111", "Title"), ModalityState.any());
          };
          ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, "Progress", true, null, progress);
          Messages.showInfoMessage("Finish", TITLE);
        });
        panel.add(progress);

        JButton popupButton = new JButton("Popup");
        popupButton.addActionListener(event -> {
          NewItemSimplePopupPanel contentPanel = new NewItemSimplePopupPanel();
          JTextField nameField = contentPanel.getTextField();
          JBPopup popup = NewItemPopupUtil.createNewItemPopup(IdeBundle.message("title.new.file"), contentPanel, nameField);
          contentPanel.setApplyAction(_event -> {
            Messages.showYesNoCancelDialog(MESSAGE, TITLE, "YesText", "NoText", "CancelText", null);
          });
          popup.showCenteredInCurrentWindow(project);
        });
        panel.add(popupButton);

        return panel;
      }
    }.show();
  }

  private static @NotNull JPanel createMessagePanel(Insets insets, JLabel messageLabel, JTextArea messageArea) {
    JPanel messagePanel = new BorderLayoutPanel(5, 5);
    messagePanel.add(messageLabel, BorderLayout.WEST);

    JBScrollPane scrollPane = new JBScrollPane(messageArea);
    scrollPane.setBorder(new CompoundBorder(new LineBorder(UIUtil.getPanelBackground(), insets.top), scrollPane.getBorder()));
    messagePanel.add(scrollPane, BorderLayout.CENTER);

    return messagePanel;
  }

  private static @NotNull DialogWrapper.DoNotAskOption createDoNotAskOption(@NotNull String text) {
    return new DialogWrapper.DoNotAskOption() {
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
        return text;
      }
    };
  }

  private static void alertWithButtons(JPanel panel, String title, String[] buttons, int index) {
    JButton button = new JButton(title);
    button.addActionListener(event -> {
      System.out.println(Messages.showDialog(button, MESSAGE, TITLE, buttons, index, null));
    });
    panel.add(button);
  }
}
