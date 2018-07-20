// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.messages;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.messages.MessageDialog;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;

import static com.intellij.openapi.ui.Messages.CANCEL_BUTTON;
import static com.intellij.openapi.ui.Messages.OK_BUTTON;

/**
 * It looks awful!
 */
@Deprecated
public class ChooseDialog extends MessageDialog {
  private ComboBox<String> myComboBox;
  private InputValidator myValidator;

  public ChooseDialog(Project project,
                      String message,
                      @Nls(capitalization = Nls.Capitalization.Title) String title,
                      @Nullable Icon icon,
                      @NotNull String[] values,
                      String initialValue,
                      @NotNull String[] options,
                      int defaultOption) {
    super(project, message, title, options, defaultOption, icon, true);
    myComboBox.setModel(new DefaultComboBoxModel<>(values));
    myComboBox.setSelectedItem(initialValue);
  }

  public ChooseDialog(@Nullable Project project,
                      @Nullable Component parent,
                      String message,
                      @Nls(capitalization = Nls.Capitalization.Title) String title,
                      @Nullable Icon icon,
                      String[] values,
                      String initialValue) {
    super(project, parent, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, -1, icon, null, true);
    myComboBox.setModel(new DefaultComboBoxModel<>(values));
    myComboBox.setSelectedItem(initialValue);
  }

  public ChooseDialog(String message,
                      @Nls(capitalization = Nls.Capitalization.Title) String title,
                      @Nullable Icon icon,
                      String[] values,
                      String initialValue) {
    super(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
    myComboBox.setModel(new DefaultComboBoxModel<>(values));
    myComboBox.setSelectedItem(initialValue);
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    final Action[] actions = new Action[myOptions.length];
    for (int i = 0; i < myOptions.length; i++) {
      String option = myOptions[i];
      final int exitCode = i;
      if (i == myDefaultOptionIndex) {
        actions[i] = new AbstractAction(option) {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (myValidator == null || myValidator.checkInput(myComboBox.getSelectedItem().toString().trim())) {
              close(exitCode);
            }
          }
        };
        actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
        myComboBox.addItemListener(e -> actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(myComboBox.getSelectedItem().toString().trim())));
        final JTextField textField = (JTextField)myComboBox.getEditor().getEditorComponent();
        textField.getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          public void textChanged(DocumentEvent event) {
            actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(textField.getText().trim()));
          }
        });
      }
      else { // "Cancel" action
        actions[i] = new AbstractAction(option) {
          @Override
          public void actionPerformed(ActionEvent e) {
            close(exitCode);
          }
        };
      }
    }
    return actions;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = createIconPanel();
    JPanel messagePanel = createMessagePanel();

    myComboBox = new ComboBox<>(220);
    messagePanel.add(myComboBox, BorderLayout.SOUTH);
    panel.add(messagePanel, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected void doOKAction() {
    String inputString = myComboBox.getSelectedItem().toString().trim();
    if (myValidator == null || myValidator.checkInput(inputString) && myValidator.canClose(inputString)) {
      super.doOKAction();
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myComboBox;
  }

  @Nullable
  public String getInputString() {
    if (getExitCode() == 0) {
      return myComboBox.getSelectedItem().toString();
    }
    return null;
  }

  public int getSelectedIndex() {
    if (getExitCode() == 0) {
      return myComboBox.getSelectedIndex();
    }
    return -1;
  }

  public JComboBox getComboBox() {
    return myComboBox;
  }

  public void setValidator(@Nullable InputValidator validator) {
    myValidator = validator;
  }
}
