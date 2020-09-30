// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Factory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @deprecated obsolete utility, about to be removed
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
@Deprecated
public class CloudDeploymentNameEditor<T extends DeploymentNameConfiguration> extends SettingsEditor<T> {

  private JTextField myNameTextField;
  private JCheckBox myCustomNameCheckBox;
  private JPanel myMainPanel;

  public CloudDeploymentNameEditor(Factory<T> deploymentModelFactory, @Nls String labelCaption) {
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

  @Override
  public void resetEditorFrom(@NotNull T settings) {
    myCustomNameCheckBox.setSelected(!settings.isDefaultDeploymentName());
    updateNameEnabled();
    myNameTextField.setText(settings.getDeploymentName());
  }

  @Override
  public void applyEditorTo(@NotNull T settings) throws ConfigurationException {
    settings.setDefaultDeploymentName(!myCustomNameCheckBox.isSelected());
    settings.setDeploymentName(myNameTextField.getText());
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    return myMainPanel;
  }
}
