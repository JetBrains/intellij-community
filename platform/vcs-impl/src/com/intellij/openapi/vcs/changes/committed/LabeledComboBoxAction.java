// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public abstract class LabeledComboBoxAction extends ComboBoxAction {

  @NotNull private final @NlsContexts.Label String myLabel;

  protected LabeledComboBoxAction(@NlsContexts.Label @NotNull String label) {
    myLabel = label;
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    ComboBoxButton button = createComboBoxButton(presentation);

    JLabel label = new JLabel(myLabel);
    label.setLabelFor(button);

    JPanel panel = new JPanel(new BorderLayout(JBUI.scale(3), 0));
    panel.add(BorderLayout.WEST, label);
    panel.add(BorderLayout.CENTER, button);
    panel.setBorder(JBUI.Borders.empty(0, 6, 0, 3));
    return panel;
  }
}
