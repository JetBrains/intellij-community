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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 22, 2009
 * Time: 7:47:01 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class CreateResourceDialog extends DialogWrapper {
  private JTextField myFileNameField;
  private JComboBox myResourceTypeCombo;
  private JPanel myPanel;
  private InputValidator myValidator;

  public CreateResourceDialog(Project project, Collection<CreateTypedResourceFileAction> actions, CreateTypedResourceFileAction selected) {
    super(project, true);
    CreateTypedResourceFileAction[] actionArray = actions.toArray(new CreateTypedResourceFileAction[actions.size()]);
    myResourceTypeCombo.setModel(new DefaultComboBoxModel(actionArray));
    myResourceTypeCombo.setSelectedItem(selected);
    init();
  }

  protected abstract InputValidator createValidator(@NotNull String resourceType);

  @Override
  protected void doOKAction() {
    String fileName = myFileNameField.getText();
    assert getSelectedAction() != null;
    myValidator = createValidator(getSelectedAction().getResourceType());
    if (myValidator.checkInput(fileName) && myValidator.canClose(fileName)) {
      super.doOKAction();
    }
  }

  public InputValidator getValidator() {
    return myValidator;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFileNameField;
  }

  public CreateTypedResourceFileAction getSelectedAction() {
    return (CreateTypedResourceFileAction)myResourceTypeCombo.getSelectedItem();
  }
}
