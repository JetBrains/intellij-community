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

package org.jetbrains.android.actions;

import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 22, 2009
 * Time: 7:47:01 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class CreateResourceDialog extends DialogWrapper {
  private JTextField myFileNameField;
  private TemplateKindCombo myResourceTypeCombo;
  private JPanel myPanel;
  private JLabel myUpDownHint;
  private JLabel myResTypeLabel;
  private InputValidator myValidator;

  private final Map<String, CreateTypedResourceFileAction> myResType2ActionMap = new HashMap<String, CreateTypedResourceFileAction>();

  public CreateResourceDialog(Project project, Collection<CreateTypedResourceFileAction> actions, CreateTypedResourceFileAction selected) {
    super(project);
    myResTypeLabel.setLabelFor(myResourceTypeCombo);
    myResourceTypeCombo.registerUpDownHint(myFileNameField);
    myUpDownHint.setIcon(PlatformIcons.UP_DOWN_ARROWS);
    CreateTypedResourceFileAction[] actionArray = actions.toArray(new CreateTypedResourceFileAction[actions.size()]);
    for (CreateTypedResourceFileAction action : actionArray) {
      String resType = action.getResourceType();
      assert !myResType2ActionMap.containsKey(resType);
      myResType2ActionMap.put(resType, action);
      myResourceTypeCombo.addItem(action.toString(), null, resType);
    }
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
    return myResType2ActionMap.get(myResourceTypeCombo.getSelectedName());
  }
}
