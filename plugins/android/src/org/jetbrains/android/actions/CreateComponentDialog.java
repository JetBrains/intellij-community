/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author coyote
 */
class CreateComponentDialog extends DialogWrapper {
  protected JTextField myNameTextField;
  protected JPanel myPanel;
  protected JTextField myLabelTextField;
  protected final InputValidator myValidator;

  public CreateComponentDialog(Project project, InputValidator validator) {
    super(project, true);
    myValidator = validator;
    init();
    myNameTextField.setText("");
    myLabelTextField.setText("");
  }

  public String getLabel() {
    return myLabelTextField.getText();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  protected void doOKAction() {
    final String inputString = myNameTextField.getText().trim();
    if (myValidator.checkInput(inputString) && myValidator.canClose(inputString)) {
      close(OK_EXIT_CODE);
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameTextField;
  }
}
