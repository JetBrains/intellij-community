// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.messages;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

class PasswordInputDialog extends Messages.InputDialog {
  PasswordInputDialog(String message,
                             @Nls(capitalization = Nls.Capitalization.Title) String title,
                             @Nullable Icon icon,
                             @Nullable InputValidator validator) {
    super(message, title, icon, null, validator);
  }

  PasswordInputDialog(Project project,
                             String message,
                             @Nls(capitalization = Nls.Capitalization.Title) String title,
                             @Nullable Icon icon,
                             @Nullable InputValidator validator) {
    super(project, message, title, icon, null, validator);
  }

  PasswordInputDialog(@NotNull Component component, String message, String title, Icon icon, @Nullable InputValidator validator) {
    super(component, message, title, icon, null, validator);
  }

  @Override
  protected JTextComponent createTextFieldComponent() {
    return new JPasswordField(30);
  }

  @Override
  public JPasswordField getTextField() {
    return (JPasswordField)super.getTextField();
  }
}
