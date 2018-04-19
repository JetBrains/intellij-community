// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.messages;

import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class InputDialogWithCheckbox extends Messages.InputDialog {
  private JCheckBox myCheckBox;

  public InputDialogWithCheckbox(String message,
                                 @Nls(capitalization = Nls.Capitalization.Title) String title,
                                 String checkboxText,
                                 boolean checked,
                                 boolean checkboxEnabled,
                                 @Nullable Icon icon,
                                 @Nullable String initialValue,
                                 @Nullable InputValidator validator) {
    super(message, title, icon, initialValue, validator);
    myCheckBox.setText(checkboxText);
    myCheckBox.setSelected(checked);
    myCheckBox.setEnabled(checkboxEnabled);
  }

  @NotNull
  @Override
  protected JPanel createMessagePanel() {
    JPanel messagePanel = new JPanel(new BorderLayout());
    if (myMessage != null) {
      JComponent textComponent = createTextComponent();
      messagePanel.add(textComponent, BorderLayout.NORTH);
    }

    myField = createTextFieldComponent();
    messagePanel.add(createScrollableTextComponent(), BorderLayout.CENTER);

    myCheckBox = new JCheckBox();
    messagePanel.add(myCheckBox, BorderLayout.SOUTH);

    return messagePanel;
  }

  public Boolean isChecked() {
    return myCheckBox.isSelected();
  }
}
