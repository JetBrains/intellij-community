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

import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.remoteServer.util.CloudAccountSelectionEditor;
import com.intellij.remoteServer.util.CloudBundle;
import com.intellij.remoteServer.util.CloudDeploymentNameConfiguration;
import com.intellij.util.containers.MultiMap;
import git4idea.actions.GitInit;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author michael.golubev
 */
public class CloudGitChooseAccountStep<DC extends CloudDeploymentNameConfiguration> extends ModuleWizardStep {

  private JPanel myAccountSelectionPanelPlaceHolder;
  private JPanel myMainPanel;
  private JLabel myTitleLabel;

  private final CloudGitProjectStructureDetectorBase<DC> myDetector;
  private final ProjectFromSourcesBuilder myBuilder;
  private final ProjectDescriptor myProjectDescriptor;

  private CloudAccountSelectionEditor<?, DC, ?> myEditor;

  public CloudGitChooseAccountStep(CloudGitProjectStructureDetectorBase<DC> detector,
                                   ProjectFromSourcesBuilder builder,
                                   ProjectDescriptor projectDescriptor) {
    myDetector = detector;
    myBuilder = builder;
    myProjectDescriptor = projectDescriptor;

    myTitleLabel.setText(CloudBundle.getText("choose.account.title", detector.getDetectorId()));

    myEditor = detector.createAccountSelectionEditor();
    myAccountSelectionPanelPlaceHolder.add(myEditor.getMainPanel());
    myEditor.initUI();
  }

  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public void updateDataModel() {
    Collection<DetectedProjectRoot> roots = myBuilder.getProjectRoots(myDetector);

    CloudGitProjectRoot lastProjectRoot = null;
    MultiMap<CloudGitProjectRoot, DetectedSourceRoot> project2sourceRoots = new MultiMap<CloudGitProjectRoot, DetectedSourceRoot>();

    for (DetectedProjectRoot root : roots) {
      if (root instanceof CloudGitProjectRoot) {
        lastProjectRoot = (CloudGitProjectRoot)root;
        project2sourceRoots.put(lastProjectRoot, new ArrayList<DetectedSourceRoot>());
      }
      else if (root instanceof DetectedSourceRoot) {
        project2sourceRoots.putValue(lastProjectRoot, (DetectedSourceRoot)root);
      }
    }

    List<ModuleDescriptor> modules = new ArrayList<ModuleDescriptor>();
    for (Map.Entry<CloudGitProjectRoot, Collection<DetectedSourceRoot>> project2sourceRootsEntry : project2sourceRoots.entrySet()) {
      final CloudGitProjectRoot projectRoot = project2sourceRootsEntry.getKey();
      final File directory = projectRoot.getDirectory();
      ModuleDescriptor moduleDescriptor = new ModuleDescriptor(directory, StdModuleTypes.JAVA, project2sourceRootsEntry.getValue());
      final String applicationName = projectRoot.getApplicationName();
      moduleDescriptor.addConfigurationUpdater(new ModuleBuilder.ModuleConfigurationUpdater() {

        @Override
        public void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
          createRunConfiguration(module, applicationName);
          GitInit.refreshAndConfigureVcsMappings(module.getProject(), projectRoot.getRepositoryRoot(), directory.getAbsolutePath());
        }
      });
      modules.add(moduleDescriptor);
    }
    myProjectDescriptor.setModules(modules);
  }

  @Override
  public boolean validate() throws ConfigurationException {
    myEditor.validate();
    return super.validate();
  }

  private void createRunConfiguration(Module module, String applicationName) {
    DC deploymentConfiguration = myDetector.createDeploymentConfiguration();

    boolean defaultName = module.getName().equals(applicationName);
    deploymentConfiguration.setDefaultDeploymentName(defaultName);
    if (!defaultName) {
      deploymentConfiguration.setDeploymentName(applicationName);
    }

    myEditor.createRunConfiguration(module, deploymentConfiguration);
  }
}
