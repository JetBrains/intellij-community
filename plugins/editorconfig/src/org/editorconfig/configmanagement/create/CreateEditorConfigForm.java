// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.create;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBRadioButton;

import javax.swing.*;

public class CreateEditorConfigForm {
  private JPanel     myTopPanel;
  private JBCheckBox myStandardPropertiesCb;
  private JBCheckBox myIntelliJPropertiesCb;

  public JPanel getTopPanel() {
    return myTopPanel;
  }

  boolean isStandardProperties() {
    return myStandardPropertiesCb.isSelected();
  }

  boolean isIntelliJProperties() {
    return myIntelliJPropertiesCb.isSelected();
  }
}
