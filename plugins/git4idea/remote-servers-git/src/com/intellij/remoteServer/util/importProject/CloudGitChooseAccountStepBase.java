/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remoteServer.util.importProject;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.util.CloudAccountSelectionEditor;
import com.intellij.remoteServer.util.CloudBundle;
import com.intellij.remoteServer.util.CloudDeploymentNameConfiguration;
import com.intellij.remoteServer.util.CloudGitDeploymentDetector;

import javax.swing.*;
import java.util.Collections;

/**
 * @author michael.golubev
 */
public class CloudGitChooseAccountStepBase extends ModuleWizardStep {

  private JPanel myAccountSelectionPanelPlaceHolder;
  private JPanel myMainPanel;
  private JLabel myTitleLabel;

  private final CloudAccountSelectionEditor myEditor;

  private final CloudGitDeploymentDetector myDeploymentDetector;
  private final WizardContext myContext;

  public CloudGitChooseAccountStepBase(CloudGitDeploymentDetector deploymentDetector, WizardContext context) {
    myDeploymentDetector = deploymentDetector;
    myContext = context;
    ServerType cloudType = deploymentDetector.getCloudType();
    myTitleLabel.setText(CloudBundle.getText("choose.account.title", cloudType.getPresentableName()));
    myEditor = new CloudAccountSelectionEditor(Collections.singletonList(cloudType));
    myAccountSelectionPanelPlaceHolder.add(myEditor.getMainPanel());
  }

  protected CloudGitDeploymentDetector getDeploymentDetector() {
    return myDeploymentDetector;
  }

  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    myEditor.validate();
    return super.validate();
  }

  @Override
  public void updateDataModel() {
    myEditor.setAccountOnContext(myContext);
  }

  public void createRunConfiguration(Module module, String applicationName) {
    CloudDeploymentNameConfiguration deploymentConfiguration = myDeploymentDetector.createDeploymentConfiguration();

    boolean defaultName = module.getName().equals(applicationName);
    deploymentConfiguration.setDefaultDeploymentName(defaultName);
    if (!defaultName) {
      deploymentConfiguration.setDeploymentName(applicationName);
    }

    CloudAccountSelectionEditor.createRunConfiguration(myContext, myDeploymentDetector.getCloudType(), module, deploymentConfiguration);
  }
}
