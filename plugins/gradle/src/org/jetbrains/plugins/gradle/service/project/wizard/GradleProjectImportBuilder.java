// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard;

import com.intellij.externalSystem.JavaProjectData;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder;
import com.intellij.openapi.externalSystem.service.ui.ExternalProjectDataSelectorDialog;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import icons.GradleIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.settings.ImportFromGradleControl;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
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

  /**
   * @deprecated use {@link GradleProjectImportBuilder#GradleProjectImportBuilder(ProjectDataManager)}
   */
  public GradleProjectImportBuilder(@NotNull com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager dataManager) {
    this((ProjectDataManager)dataManager);
  }

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
  protected ExternalProjectRefreshCallback createFinalImportCallback(@NotNull final Project project,
                                                                     @NotNull ExternalProjectSettings projectSettings) {
    return new ExternalProjectRefreshCallback() {
      @Override
      public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
        if (externalProject == null) return;
        Runnable selectDataTask = () -> {
          ExternalProjectDataSelectorDialog dialog = new ExternalProjectDataSelectorDialog(
            project, new InternalExternalProjectInfo(
            GradleConstants.SYSTEM_ID, projectSettings.getExternalProjectPath(), externalProject));
          if (dialog.hasMultipleDataToSelect()) {
            dialog.showAndGet();
          }
          else {
            Disposer.dispose(dialog.getDisposable());
          }
        };

        Runnable importTask = () -> ServiceManager.getService(ProjectDataManager.class).importData(externalProject, project, false);

        boolean showSelectiveImportDialog = GradleSettings.getInstance(project).showSelectiveImportDialogOnInitialImport();
        if (showSelectiveImportDialog && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
          ApplicationManager.getApplication().invokeLater(() -> {
            selectDataTask.run();
            ApplicationManager.getApplication().executeOnPooledThread(importTask);
          });
        }
        else {
          importTask.run();
        }
      }

      @Override
      public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
      }
    };
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

  @Nullable
  @Override
  public Project createProject(String name, String path) {
    Project project = super.createProject(name, path);
    if (project != null) {
      GradleProjectSettings settings = getControl(project).getProjectSettings();
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(settings.isStoreProjectFilesExternally());
    }
    return project;
  }
}
