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

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder;
import com.intellij.openapi.externalSystem.service.project.wizard.ExternalModuleSettingsStep;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.settings.GradleProjectSettingsControl;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;

/**
 * @author Denis Zhdanov
 * @since 6/26/13 11:10 AM
 */
public class GradleModuleBuilder extends AbstractExternalModuleBuilder<GradleProjectSettings> {

  public GradleModuleBuilder() {
    super(GradleConstants.SYSTEM_ID, new GradleProjectSettings());
  }

  @Nullable
  @Override
  protected VirtualFile getExternalProjectConfigFile(@NotNull VirtualFile contentRootDir) {
    File gradleScript = new File(contentRootDir.getPath(), GradleConstants.DEFAULT_SCRIPT_NAME);
    FileUtilRt.createIfNotExists(gradleScript);
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(gradleScript);
  }

  @Nullable
  @Override
  protected String getTemplateConfigName(@NotNull GradleProjectSettings settings) {
    final String templateName;
    if (settings.getDistributionType() == DistributionType.WRAPPED) {
      templateName = "Gradle Build Script with wrapper.gradle";
    }
    else {
      templateName = "Gradle Build Script.gradle";
    }
    return templateName;
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    final GradleProjectSettingsControl settingsControl = new GradleProjectSettingsControl(getExternalProjectSettings());
    return new ModuleWizardStep[]{new ExternalModuleSettingsStep<GradleProjectSettings>(this, settingsControl)};
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return sdk instanceof JavaSdkType;
  }

  @Override
  public String getGroupName() {
    return JavaModuleType.JAVA_GROUP;
  }

  @Override
  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }
}
