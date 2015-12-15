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

import com.intellij.externalSystem.JavaProjectData;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import icons.GradleIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.settings.ImportFromGradleControl;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.io.File;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 4/15/13 2:29 PM
 */
public class GradleProjectImportBuilder extends AbstractExternalProjectImportBuilder<ImportFromGradleControl> {

  public GradleProjectImportBuilder(@NotNull ProjectDataManager dataManager) {
    super(dataManager, new ImportFromGradleControl(), GradleConstants.SYSTEM_ID);
  }

  @NotNull
  @Override
  public String getName() {
    return GradleBundle.message("gradle.name");
  }

  @Override
  public Icon getIcon() {
    return GradleIcons.Gradle;
  }

  @Override
  protected void doPrepare(@NotNull WizardContext context) {
    String pathToUse = getFileToImport();
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(pathToUse);
    if (file != null && !file.isDirectory() && file.getParent() != null) {
      pathToUse = file.getParent().getPath();
    }

    final ImportFromGradleControl importFromGradleControl = getControl(context.getProject());
    importFromGradleControl.setLinkedProjectPath(pathToUse);
    final Pair<String, Sdk> sdkPair = ExternalSystemJdkUtil.getAvailableJdk(context.getProject());
    if (sdkPair != null && !ExternalSystemJdkUtil.USE_INTERNAL_JAVA.equals(sdkPair.first)) {
      importFromGradleControl.getProjectSettings().setGradleJvm(sdkPair.first);
    }
  }

  @Override
  protected void beforeCommit(@NotNull DataNode<ProjectData> dataNode, @NotNull Project project) {
    DataNode<JavaProjectData> javaProjectNode = ExternalSystemApiUtil.find(dataNode, JavaProjectData.KEY);
    if (javaProjectNode == null) {
      return;
    }

    final LanguageLevel externalLanguageLevel = javaProjectNode.getData().getLanguageLevel();
    final LanguageLevelProjectExtension languageLevelExtension = LanguageLevelProjectExtension.getInstance(project);
    if (externalLanguageLevel != languageLevelExtension.getLanguageLevel()) {
      languageLevelExtension.setLanguageLevel(externalLanguageLevel);
    }
  }

  @Override
  protected void applyExtraSettings(@NotNull WizardContext context) {
    DataNode<ProjectData> node = getExternalProjectNode();
    if (node == null) {
      return;
    }

    DataNode<JavaProjectData> javaProjectNode = ExternalSystemApiUtil.find(node, JavaProjectData.KEY);
    if (javaProjectNode != null) {
      JavaProjectData data = javaProjectNode.getData();
      context.setCompilerOutputDirectory(data.getCompileOutputPath());
      JavaSdkVersion version = data.getJdkVersion();
      Sdk jdk = findJdk(version);
      if (jdk != null) {
        context.setProjectJdk(jdk);
      }
    }
  }

  @Nullable
  private static Sdk findJdk(@NotNull JavaSdkVersion version) {
    JavaSdk javaSdk = JavaSdk.getInstance();
    List<Sdk> javaSdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdk);
    Sdk candidate = null;
    for (Sdk sdk : javaSdks) {
      JavaSdkVersion v = javaSdk.getVersion(sdk);
      if (v == version) {
        return sdk;
      }
      else if (candidate == null && v != null && version.getMaxLanguageLevel().isAtLeast(version.getMaxLanguageLevel())) {
        candidate = sdk;
      }
    }
    return candidate;
  }

  @NotNull
  @Override
  protected File getExternalProjectConfigToUse(@NotNull File file) {
    return file.isDirectory() ? file : file.getParentFile();
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return sdk == JavaSdk.getInstance();
  }
}
