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
package org.jetbrains.plugins.gradle.service.project.wizard;

import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.wizard.SelectExternalProjectStep;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.settings.GradleProjectSettingsControl;
import org.jetbrains.plugins.gradle.service.settings.GradleSystemSettingsControl;
import org.jetbrains.plugins.gradle.service.settings.ImportFromGradleControl;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import static org.jetbrains.plugins.gradle.util.GradleEnvironment.Headless.*;

/**
 * @author Denis Zhdanov
 * @since 4/30/13 11:22 PM
 */
public class GradleProjectOpenProcessor extends ProjectOpenProcessorBase<GradleProjectImportBuilder> {

  @NotNull public static final String[] BUILD_FILE_EXTENSIONS = {GradleConstants.EXTENSION};

  public GradleProjectOpenProcessor(@NotNull GradleProjectImportBuilder builder) {
    super(builder);
  }

  @Nullable
  @Override
  public String[] getSupportedExtensions() {
    return new String[] {GradleConstants.DEFAULT_SCRIPT_NAME, GradleConstants.SETTINGS_FILE_NAME};
  }

  @Override
  public boolean canOpenProject(VirtualFile file) {
    if (!file.isDirectory()) {
      String fileName = file.getName();
      for (String extension : BUILD_FILE_EXTENSIONS) {
        if (fileName.endsWith(extension)) {
          return true;
        }
      }
    }
    return super.canOpenProject(file);
  }

  @Override
  protected boolean doQuickImport(VirtualFile file, WizardContext wizardContext) {
    final GradleProjectImportProvider projectImportProvider = new GradleProjectImportProvider(getBuilder());
    getBuilder().setFileToImport(file.getPath());
    getBuilder().prepare(wizardContext);

    final String pathToUse;
    if (!file.isDirectory() && file.getParent() != null) {
      pathToUse = file.getParent().getPath();
    }
    else {
      pathToUse = file.getPath();
    }
    getBuilder().getControl(null).setLinkedProjectPath(pathToUse);

    final boolean result;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      result = setupGradleProjectSettingsInHeadlessMode(projectImportProvider, wizardContext);
    }
    else {
      AddModuleWizard dialog = new AddModuleWizard(null, file.getPath(), projectImportProvider);
      dialog.getWizardContext().setProjectBuilder(getBuilder());
      dialog.navigateToStep(step -> step instanceof SelectExternalProjectStep);
      result = dialog.showAndGet();
    }
    if (result && getBuilder().getExternalProjectNode() != null) {
      wizardContext.setProjectName(getBuilder().getExternalProjectNode().getData().getInternalName());
    }
    return result;
  }

  private boolean setupGradleProjectSettingsInHeadlessMode(GradleProjectImportProvider projectImportProvider,
                                                           WizardContext wizardContext) {
    final ModuleWizardStep[] wizardSteps = projectImportProvider.createSteps(wizardContext);
    if (wizardSteps.length > 0 && wizardSteps[0] instanceof SelectExternalProjectStep) {
      SelectExternalProjectStep selectExternalProjectStep = (SelectExternalProjectStep)wizardSteps[0];
      wizardContext.setProjectBuilder(getBuilder());
      try {
        selectExternalProjectStep.updateStep();
        final ImportFromGradleControl importFromGradleControl = getBuilder().getControl(wizardContext.getProject());

        GradleProjectSettingsControl gradleProjectSettingsControl =
          (GradleProjectSettingsControl)importFromGradleControl.getProjectSettingsControl();

        final GradleProjectSettings projectSettings = gradleProjectSettingsControl.getInitialSettings();

        if (GRADLE_DISTRIBUTION_TYPE != null) {
          for (DistributionType type : DistributionType.values()) {
            if (type.name().equals(GRADLE_DISTRIBUTION_TYPE)) {
              projectSettings.setDistributionType(type);
              break;
            }
          }
        }
        if (GRADLE_HOME != null) {
          projectSettings.setGradleHome(GRADLE_HOME);
        }
        gradleProjectSettingsControl.reset();

        final GradleSystemSettingsControl systemSettingsControl =
          (GradleSystemSettingsControl)importFromGradleControl.getSystemSettingsControl();
        assert systemSettingsControl != null;
        final GradleSettings gradleSettings = systemSettingsControl.getInitialSettings();
        if (GRADLE_VM_OPTIONS != null) {
          gradleSettings.setGradleVmOptions(GRADLE_VM_OPTIONS);
        }
        if (GRADLE_OFFLINE != null) {
          gradleSettings.setOfflineWork(Boolean.parseBoolean(GRADLE_OFFLINE));
        }
        String serviceDirectory = GRADLE_SERVICE_DIRECTORY;
        if (GRADLE_SERVICE_DIRECTORY != null) {
          gradleSettings.setServiceDirectoryPath(serviceDirectory);
        }
        systemSettingsControl.reset();

        if (!selectExternalProjectStep.validate()) {
          return false;
        }
      }
      catch (ConfigurationException e) {
        Messages.showErrorDialog(wizardContext.getProject(), e.getMessage(), e.getTitle());
        return false;
      }
      selectExternalProjectStep.updateDataModel();
    }
    return true;
  }

  @Override
  public boolean lookForProjectsInDirectory() {
    return false;
  }
}
