package com.intellij.internal.validation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
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
import java.awt.event.*;

/**
 * @author Denis Fokin
 */
public class MacMessagesTest extends AnAction {


  private static class SimpleDialogWrapper extends DialogWrapper {

    private  JTextArea jbTextField = new JTextArea(1, 30);

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
  public void actionPerformed(final AnActionEvent anActionEvent) {
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

    FocusManagerImpl fmi = FocusManagerImpl.getInstance();
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
          public void run(@NotNull final ProgressIndicator indicator) {
            LaterInvocator.invokeAndWait(new Runnable() {
              @Override
              public void run() {
                FocusManagerImpl fmi = FocusManagerImpl.getInstance();
                final Project p = fmi.getLastFocusedFrame().getProject();
                showTestMessage(p);
              }
            }, ModalityState.any());
          }

          @Override
          public void onCancel() {}
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

