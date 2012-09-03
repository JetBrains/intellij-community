/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.uipreview.DeviceConfiguratorPanel;
import org.jetbrains.android.uipreview.InvalidOptionValueException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class CreateResourceDirectoryDialog extends DialogWrapper {
  private JComboBox myResourceTypeComboBox;
  private JPanel myDeviceConfiguratorWrapper;
  private JTextField myDirectoryNameTextField;
  private JPanel myContentPanel;
  private JBLabel myErrorLabel;

  private final DeviceConfiguratorPanel myDeviceConfiguratorPanel;
  private InputValidator myValidator;

  public CreateResourceDirectoryDialog(@NotNull Project project, @Nullable ResourceFolderType resType) {
    super(project);

    myResourceTypeComboBox.setModel(new EnumComboBoxModel<ResourceFolderType>(ResourceFolderType.class));
    myResourceTypeComboBox.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof ResourceFolderType) {
          setText(((ResourceFolderType)value).getName());
        }
      }
    });

    myDeviceConfiguratorPanel = new DeviceConfiguratorPanel() {
      @Override
      public void applyEditors() {
        try {
          doApplyEditors();
          final FolderConfiguration config = myDeviceConfiguratorPanel.getConfiguration();
          final ResourceFolderType selectedResourceType = (ResourceFolderType)myResourceTypeComboBox.getSelectedItem();
          myDirectoryNameTextField.setText(selectedResourceType != null ? config.getFolderName(selectedResourceType) : "");
          myErrorLabel.setText("");
        }
        catch (InvalidOptionValueException e) {
          myErrorLabel.setText("<html><body><font color=\"red\">" + e.getMessage() + "</font></body></html>");
          myDirectoryNameTextField.setText("");
        }
        setOKActionEnabled(myDirectoryNameTextField.getText().length() > 0);
      }
    };

    myDeviceConfiguratorWrapper.add(myDeviceConfiguratorPanel, BorderLayout.CENTER);
    myResourceTypeComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDeviceConfiguratorPanel.applyEditors();
      }
    });

    if (resType != null) {
      myResourceTypeComboBox.setSelectedItem(resType);
      myResourceTypeComboBox.setEnabled(false);
    }

    myDeviceConfiguratorPanel.updateAll();
    setOKActionEnabled(myDirectoryNameTextField.getText().length() > 0);
    init();
  }

  protected abstract InputValidator createValidator();

  @Override
  protected void doOKAction() {
    final String dirName = myDirectoryNameTextField.getText();
    assert dirName != null;
    myValidator = createValidator();
    if (myValidator.checkInput(dirName) && myValidator.canClose(dirName)) {
      super.doOKAction();
    }
  }

  @Override
  protected String getDimensionServiceKey() {
    return "AndroidCreateResourceDirectoryDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myResourceTypeComboBox.isEnabled()) {
      return myResourceTypeComboBox;
    }
    else {
      return myDirectoryNameTextField;
    }
  }

  public InputValidator getValidator() {
    return myValidator;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }
}
