/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.remoteServer.util;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Factory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author michael.golubev
 */
public class CloudDeploymentNameEditor<T extends DeploymentNameConfiguration> extends SettingsEditor<T> {

  private JTextField myNameTextField;
  private JCheckBox myCustomNameCheckBox;
  private JPanel myMainPanel;

  public CloudDeploymentNameEditor(Factory<T> deploymentModelFactory, String labelCaption) {
    super(deploymentModelFactory);
    myCustomNameCheckBox.setText(labelCaption);
    myCustomNameCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateNameEnabled();
      }
    });
    myCustomNameCheckBox.setSelected(false);
    updateNameEnabled();
  }

  private void updateNameEnabled() {
    myNameTextField.setEnabled(myCustomNameCheckBox.isSelected());
  }

  public void resetEditorFrom(T settings) {
    myCustomNameCheckBox.setSelected(!settings.isDefaultDeploymentName());
    updateNameEnabled();
    myNameTextField.setText(settings.getDeploymentName());
  }

  public void applyEditorTo(T settings) throws ConfigurationException {
    settings.setDefaultDeploymentName(!myCustomNameCheckBox.isSelected());
    settings.setDeploymentName(myNameTextField.getText());
  }

  @NotNull
  protected JComponent createEditor() {
    return myMainPanel;
  }
}
