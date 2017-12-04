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
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ObjectUtils;
import gnu.trove.THashSet;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @since 4/15/13 2:29 PM
 */
public class GradleProjectImportBuilder extends AbstractExternalProjectImportBuilder<ImportFromGradleControl> {

  private static final Pattern JAVA_VERSION = Pattern.compile("java version \"(\\d.*)\"");

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

  @Nullable
  @Override
  protected Sdk resolveProjectJdk(@NotNull WizardContext context) {
    // gradle older than 4.2.1 doesn't support new java the version number format like 9.0.1, see https://github.com/gradle/gradle/issues/2992
    Condition<Sdk> sdkCondition = sdk -> {
      String version = getVersion(sdk);
      return StringUtil.compareVersionNumbers(version, "1.6") > 0 &&
             StringUtil.compareVersionNumbers(version, "9") < 0 &&
             ExternalSystemJdkUtil.isValidJdk(sdk.getHomePath());
    };

    Sdk mostRecentSdk = ProjectJdkTable.getInstance().findMostRecentSdk(
      sdk -> sdk.getSdkType() == JavaSdk.getInstance() && sdkCondition.value(sdk));
    if (mostRecentSdk != null) {
      return mostRecentSdk;
    }

    Set<String> existingPaths =
      new THashSet<>(Arrays.stream(ProjectJdkTable.getInstance().getAllJdks()).map(sdk -> sdk.getHomePath()).collect(Collectors.toSet()),
                     FileUtil.PATH_HASHING_STRATEGY);

    for (String javaHome : JavaSdk.getInstance().suggestHomePaths()) {
      if (!existingPaths.contains(FileUtil.toCanonicalPath(javaHome))) {
        JavaSdk javaSdk = JavaSdk.getInstance();
        Sdk jdk = javaSdk.createJdk(ObjectUtils.notNull(javaSdk.suggestSdkName(null, javaHome), ""), javaHome);
        if (sdkCondition.value(jdk)) {
          ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().addJdk(jdk));
          return jdk;
        }
      }
    }

    Project project = context.getProject() != null ? context.getProject() : ProjectManager.getInstance().getDefaultProject();
    final Pair<String, Sdk> sdkPair = ExternalSystemJdkUtil.getAvailableJdk(project);
    if (!ExternalSystemJdkUtil.USE_INTERNAL_JAVA.equals(sdkPair.first)) {
      return sdkPair.second;
    }
    return null;
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

  @Nullable
  private static String getVersion(Sdk sdk) {
    String versionString = sdk.getVersionString();
    if (versionString == null) return null;
    Matcher matcher = JAVA_VERSION.matcher(versionString.trim());
    if (matcher.matches()) {
      return matcher.group(1);
    }
    return versionString;
  }
}
