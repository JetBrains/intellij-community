// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.evaluate;

import org.jetbrains.annotations.Nls;

import javax.swing.*;

public class ExpressionInputForm {
  private JPanel myLanguageChooserPanel;
  private JPanel myExpressionPanel;
  private JPanel myMainPanel;
  private JLabel myNameLabel;

  public void addLanguageComponent(JComponent component) {
    myLanguageChooserPanel.add(component);
  }

  public void addExpressionComponent(JComponent component) {
    myExpressionPanel.add(component);
  }

  public void setName(@Nls String name) {
    myNameLabel.setText(name);
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }
}
