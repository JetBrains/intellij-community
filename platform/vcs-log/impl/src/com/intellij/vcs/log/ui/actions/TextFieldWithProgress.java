// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.project.Project;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

@ApiStatus.Internal
public class TextFieldWithProgress extends JPanel {
  private final @NotNull TextFieldWithCompletion myTextField;
  private final @NotNull AsyncProcessIcon myProgressIcon;

  public TextFieldWithProgress(@NotNull Project project,
                               @NotNull TextCompletionProvider completionProvider) {
    super(new BorderLayout());

    myProgressIcon = new AsyncProcessIcon("Loading commits");
    myTextField = new TextFieldWithCompletion(project, completionProvider, "", true, true, false) {
      @Override
      public void setBackground(Color bg) {
        super.setBackground(bg);
        myProgressIcon.setBackground(bg);
      }

      @Override
      protected boolean processKeyBinding(KeyStroke ks, final KeyEvent e, int condition, boolean pressed) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          onOk();
          return true;
        }
        return false;
      }
    };
    myTextField.setBorder(JBUI.Borders.empty());

    myProgressIcon.setOpaque(true);
    myProgressIcon.setBackground(myTextField.getBackground());

    add(myTextField, BorderLayout.CENTER);
    add(myProgressIcon, BorderLayout.EAST);

    hideProgress();
  }

  public JComponent getPreferableFocusComponent() {
    return myTextField;
  }

  public void showProgress() {
    myTextField.setEnabled(false);
    myProgressIcon.setVisible(true);
  }

  public void hideProgress() {
    myTextField.setEnabled(true);
    myProgressIcon.setVisible(false);
  }

  public String getText() {
    return myTextField.getText();
  }

  public void onOk() {
  }
}
