// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.validation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.impl.FocusManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Denis Fokin
 */
public class MacMessagesTest extends AnAction {


  private static class SimpleDialogWrapper extends DialogWrapper {

    private final JTextArea jbTextField = new JTextArea(1, 30);

    SimpleDialogWrapper(@Nullable Project project) {
      super(project);
      setSize(500, 500);
      getWindow().setLocationRelativeTo(getWindow().getParent());
      init();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return jbTextField;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {

      JPanel jPanel = new JPanel();

      jPanel.add(jbTextField);
      return jPanel;
    }
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent anActionEvent) {
    JDialog controlDialog = new JDialog();
    controlDialog.setTitle("Messages testing control panel");
    controlDialog.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
    controlDialog.setModal(false);
    controlDialog.setFocusableWindowState(false);
    controlDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    Container cp = controlDialog.getContentPane();
    cp.setLayout(new FlowLayout());
    JButton showDialogWrapperButton = new JButton("Show a dialog wrapper");
    showDialogWrapperButton.setFocusable(false);

    FocusManagerImpl fmi = (FocusManagerImpl)FocusManagerImpl.getInstance();
    final Project p = fmi.getLastFocusedFrame().getProject();

    showDialogWrapperButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        DialogWrapper dw = new SimpleDialogWrapper(p);
        dw.setTitle(dw.getWindow().getName());
        dw.show();
      }
    });

    JButton showMessageButton = new JButton("Show a message");
    showDialogWrapperButton.setFocusable(false);

    showMessageButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        showTestMessage(p);
      }
    });

    JButton showProgressIndicatorButton = new JButton("Show progress indicator");

    showProgressIndicatorButton.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        final Task task = new Task.Modal(null, "Test task", true) {
          @Override
          public void run(@NotNull final ProgressIndicator indicator) {
            ApplicationManager.getApplication().invokeAndWait(() -> {
              FocusManagerImpl fmi1 = (FocusManagerImpl)FocusManagerImpl.getInstance();
              final Project p1 = fmi1.getLastFocusedFrame().getProject();
              showTestMessage(p1);
            }, ModalityState.any());
          }
        };
        ProgressManager.getInstance().run(task);


      }
    });

    cp.add(showDialogWrapperButton);
    cp.add(showMessageButton);
    cp.add(showProgressIndicatorButton);

    controlDialog.pack();

    controlDialog.setVisible(true);

  }

  private static void showTestMessage(Project p) {
    Messages.showDialog(p, "Test message", "Test Title", new String[]{"Option one", "Option two", "Option three"}, 0, null);
  }
}

