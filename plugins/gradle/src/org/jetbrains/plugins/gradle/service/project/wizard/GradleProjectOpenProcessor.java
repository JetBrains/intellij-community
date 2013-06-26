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
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.externalSystem.service.project.wizard.SelectExternalProjectStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

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
    return BUILD_FILE_EXTENSIONS;
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
    AddModuleWizard dialog = new AddModuleWizard(null, file.getPath(), new GradleProjectImportProvider(getBuilder()));
    getBuilder().prepare(wizardContext);
    getBuilder().getControl(null).setLinkedProjectPath(file.getPath());
    dialog.getWizardContext().setProjectBuilder(getBuilder());
    dialog.navigateToStep(new Function<Step, Boolean>() {
      @Override
      public Boolean fun(Step step) {
        return step instanceof SelectExternalProjectStep;
      }
    });
    if (StringUtil.isEmpty(wizardContext.getProjectName())) {
      final String projectName = dialog.getWizardContext().getProjectName();
      if (!StringUtil.isEmpty(projectName)) {
        wizardContext.setProjectName(projectName);
      }
    }

    dialog.show();
    return dialog.isOK();
  }

  @Override
  public boolean lookForProjectsInDirectory() {
    return false;
  }
}
