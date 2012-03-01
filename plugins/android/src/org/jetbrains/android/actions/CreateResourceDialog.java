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

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.uipreview.DeviceConfiguratorPanel;
import org.jetbrains.android.uipreview.InvalidOptionValueException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
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
  private JPanel myDeviceConfiguratorWrapper;
  private JBLabel myErrorLabel;
  private JTextField myDirectoryNameTextField;
  private InputValidator myValidator;

  private final Map<String, CreateTypedResourceFileAction> myResType2ActionMap = new HashMap<String, CreateTypedResourceFileAction>();
  private final DeviceConfiguratorPanel myDeviceConfiguratorPanel;

  public CreateResourceDialog(Project project, Collection<CreateTypedResourceFileAction> actions, CreateTypedResourceFileAction selected) {
    super(project);
    myResTypeLabel.setLabelFor(myResourceTypeCombo);
    myResourceTypeCombo.registerUpDownHint(myFileNameField);
    myUpDownHint.setIcon(PlatformIcons.UP_DOWN_ARROWS);
    CreateTypedResourceFileAction[] actionArray = actions.toArray(new CreateTypedResourceFileAction[actions.size()]);

    Arrays.sort(actionArray, new Comparator<CreateTypedResourceFileAction>() {
      @Override
      public int compare(CreateTypedResourceFileAction a1, CreateTypedResourceFileAction a2) {
        return a1.toString().compareTo(a2.toString());
      }
    });

    for (CreateTypedResourceFileAction action : actionArray) {
      String resType = action.getResourceType();
      assert !myResType2ActionMap.containsKey(resType);
      myResType2ActionMap.put(resType, action);
      myResourceTypeCombo.addItem(action.toString(), null, resType);
    }

    myDeviceConfiguratorPanel = new DeviceConfiguratorPanel(null) {
      @Override
      public void applyEditors() {
        try {
          doApplyEditors();
          final FolderConfiguration config = myDeviceConfiguratorPanel.getConfiguration();
          final CreateTypedResourceFileAction selectedAction = getSelectedAction();
          myErrorLabel.setText("");
          myDirectoryNameTextField.setText("");
          if (selectedAction != null) {
            final String resTypeStr = selectedAction.getResourceType();
            if (resTypeStr != null) {
              final ResourceFolderType resFolderType = ResourceFolderType.getTypeByName(resTypeStr);
              if (resFolderType != null) {
                myDirectoryNameTextField.setText(config.getFolderName(resFolderType));
              }
            }
          }
        }
        catch (InvalidOptionValueException e) {
          myErrorLabel.setText("<html><body><font color=\"red\">" + e.getMessage() + "</font></body></html>");
          myDirectoryNameTextField.setText("");
        }
        setOKActionEnabled(myDirectoryNameTextField.getText().length() > 0);
      }
    };

    myResourceTypeCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDeviceConfiguratorPanel.applyEditors();
      }
    });

    myDeviceConfiguratorPanel.updateAll();
    myDeviceConfiguratorWrapper.add(myDeviceConfiguratorPanel, BorderLayout.CENTER);
    setOKActionEnabled(myDirectoryNameTextField.getText().length() > 0);

    init();
  }

  protected abstract InputValidator createValidator(@NotNull String subdirName);

  @Override
  protected void doOKAction() {
    String fileName = myFileNameField.getText();
    assert getSelectedAction() != null;

    final String subdirName = myDirectoryNameTextField.getText();
    assert subdirName != null && subdirName.length() > 0;

    myValidator = createValidator(subdirName);
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
