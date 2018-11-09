// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public abstract class LabeledComboBoxAction extends ComboBoxAction {

  @NotNull private final String myLabel;

  protected LabeledComboBoxAction(@NotNull String label) {
    myLabel = label;
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation) {
    JPanel panel = new JPanel(new BorderLayout());

    panel.add(new JLabel(myLabel), BorderLayout.WEST);
    panel.add(super.createCustomComponent(presentation), BorderLayout.CENTER);
    UIUtil.addInsets(panel, JBUI.insets(0, 6, 0, 0));

    return panel;
  }
}
