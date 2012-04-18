/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.validation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;

import javax.swing.*;
import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
public class ValidationTest extends DialogWrapper {
  private ValidTest myPanel;
  final ValidationInfo[] ERRORS;

  protected ValidationTest(Project project) {
    super(project);
    myPanel = new ValidTest();
    ERRORS = new ValidationInfo[]{
      new ValidationInfo("Field1 should not be empty", myPanel.field1),
      new ValidationInfo("Field2 is zip. It should contain 5 digits", myPanel.field2),
      new ValidationInfo("Field3. Value is not chosen", myPanel.field3),
      new ValidationInfo("Field4: Select A or B", myPanel.p4),
      new ValidationInfo("Field5: You should accept license agreement<br/>text text text text text text text text text text text text<br/>text text text text text text text text text text text text")
    };

    init();
  }


  @Override
  protected boolean postponeValidation() {
    return true;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.field2;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    Messages.showInfoMessage("on OK", "Info");
  }

  @Override
  protected ValidationInfo doValidate() {
    if (myPanel.field1.getText().isEmpty()) return ERRORS[0];
    if (!Pattern.compile("[0-9]{5}").matcher(myPanel.field2.getText()).matches()) return ERRORS[1];
    if (myPanel.field3.getSelectedItem() == null || "".equals(myPanel.field3.getSelectedItem())) return ERRORS[2];
    if (!myPanel.field4A.isSelected() && !myPanel.field4B.isSelected()) return ERRORS[3];
    if (!myPanel.field5.isSelected()) return ERRORS[4];
    return null;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel.panel;
  }
}
