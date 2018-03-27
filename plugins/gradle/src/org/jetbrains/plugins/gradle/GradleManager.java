/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SearchScopeProvider;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.ExternalSystemConfigurableAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.autoimport.CachingExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.service.ui.DefaultExternalSystemUiAware;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.messages.MessageBusConnection;
import icons.GradleIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleSettingsListenerAdapter;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleConsoleProperties;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.service.project.GradleAutoImportAware;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.settings.GradleConfigurable;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.settings.*;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static com.intellij.openapi.util.io.FileUtil.pathsEqual;

/**
 * @author Denis Zhdanov
 * @since 4/10/13 1:19 PM
 */
public class GradleManager
  implements ExternalSystemConfigurableAware, ExternalSystemUiAware, ExternalSystemAutoImportAware, StartupActivity, ExternalSystemManager<
  GradleProjectSettings,
  GradleSettingsListener,
  GradleSettings,
  GradleLocalSettings,
  GradleExecutionSettings> {

  private static final Logger LOG = Logger.getInstance(GradleManager.class);

  @NotNull private final ExternalSystemAutoImportAware myAutoImportDelegate =
    new CachingExternalSystemAutoImportAware(new GradleAutoImportAware());

  @NotNull
  private final GradleInstallationManager myInstallationManager;

  @NotNull private static final NotNullLazyValue<List<GradleProjectResolverExtension>> RESOLVER_EXTENSIONS =
    new AtomicNotNullLazyValue<List<GradleProjectResolverExtension>>() {
      @NotNull
      @Override
      protected List<GradleProjectResolverExtension> compute() {
        List<GradleProjectResolverExtension> result = ContainerUtilRt.newArrayList();
        Collections.addAll(result, GradleProjectResolverExtension.EP_NAME.getExtensions());
        ExternalSystemApiUtil.orderAwareSort(result);
        return result;
      }
    };

  public GradleManager(@NotNull GradleInstallationManager manager) {
    myInstallationManager = manager;
  }

  @NotNull
  @Override
  public ProjectSystemId getSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @NotNull
  @Override
  public Function<Project, GradleSettings> getSettingsProvider() {
    return project -> GradleSettings.getInstance(project);
  }

  @NotNull
  @Override
  public Function<Project, GradleLocalSettings> getLocalSettingsProvider() {
    return project -> GradleLocalSettings.getInstance(project);
  }

  @NotNull
  @Override
  public Function<Pair<Project, String>, GradleExecutionSettings> getExecutionSettingsProvider() {
    return pair -> {
      final Project project = pair.first;
      GradleSettings settings = GradleSettings.getInstance(project);
      File gradleHome = myInstallationManager.getGradleHome(project, pair.second);
      String localGradlePath = null;
      if (gradleHome != null) {
        try {
          // Try to resolve symbolic links as there were problems with them at the gradle side.
          localGradlePath = gradleHome.getCanonicalPath();
        }
        catch (IOException e) {
          localGradlePath = gradleHome.getAbsolutePath();
        }
      }

      GradleProjectSettings projectLevelSettings = settings.getLinkedProjectSettings(pair.second);
      final DistributionType distributionType;
      if (projectLevelSettings == null) {
        distributionType =
          GradleUtil.isGradleDefaultWrapperFilesExist(pair.second) ? DistributionType.DEFAULT_WRAPPED : DistributionType.BUNDLED;
      }
      else {
        distributionType =
          projectLevelSettings.getDistributionType() == null ? DistributionType.LOCAL : projectLevelSettings.getDistributionType();
      }

      GradleExecutionSettings result = new GradleExecutionSettings(localGradlePath,
                                                                   settings.getServiceDirectoryPath(),
                                                                   distributionType,
                                                                   settings.getGradleVmOptions(),
                                                                   settings.isOfflineWork());
      for (GradleProjectResolverExtension extension : RESOLVER_EXTENSIONS.getValue()) {
        result.addResolverExtensionClass(ClassHolder.from(extension.getClass()));
      }

      final String rootProjectPath = projectLevelSettings != null ? projectLevelSettings.getExternalProjectPath() : pair.second;
      final Sdk gradleJdk = myInstallationManager.getGradleJdk(project, rootProjectPath);
      final String javaHome = gradleJdk != null ? gradleJdk.getHomePath() : null;
      if (!StringUtil.isEmpty(javaHome)) {
        LOG.info("Instructing gradle to use java from " + javaHome);
      }
      result.setJavaHome(javaHome);
      String ideProjectPath;
      if (project.getBasePath() == null ||
          (project.getProjectFilePath() != null && StringUtil.endsWith(project.getProjectFilePath(), ".ipr"))) {
        ideProjectPath = rootProjectPath;
      }
      else {
        ideProjectPath = project.getBasePath() + "/.idea/modules";
      }
      result.setIdeProjectPath(ideProjectPath);
      if (projectLevelSettings != null) {
        result.setResolveModulePerSourceSet(projectLevelSettings.isResolveModulePerSourceSet());
        result.setUseQualifiedModuleNames(projectLevelSettings.isUseQualifiedModuleNames());
      }

      configureExecutionWorkspace(projectLevelSettings, settings, result, project, pair.second);
      return result;
    };
  }

  /**
   * Add composite participants
   */
  private static void configureExecutionWorkspace(@Nullable GradleProjectSettings compositeRootSettings,
                                                  GradleSettings settings,
                                                  GradleExecutionSettings result,
                                                  Project project,
                                                  String projectPath) {
    if (compositeRootSettings == null || compositeRootSettings.getCompositeBuild() == null) return;

    GradleProjectSettings.CompositeBuild compositeBuild = compositeRootSettings.getCompositeBuild();
    if (compositeBuild.getCompositeDefinitionSource() == CompositeDefinitionSource.SCRIPT) {
      if (pathsEqual(compositeRootSettings.getExternalProjectPath(), projectPath)) return;

      for (BuildParticipant buildParticipant : compositeBuild.getCompositeParticipants()) {
        if (pathsEqual(buildParticipant.getRootPath(), projectPath)) continue;
        if (buildParticipant.getProjects().stream().anyMatch(path -> pathsEqual(path, projectPath))) {
          continue;
        }
        result.getExecutionWorkspace().addBuildParticipant(new GradleBuildParticipant(buildParticipant.getRootPath()));
      }
      return;
    }

    for (GradleProjectSettings projectSettings : settings.getLinkedProjectsSettings()) {
      if (projectSettings == compositeRootSettings) continue;
      if (compositeBuild.getCompositeParticipants()
        .stream()
        .noneMatch(participant -> pathsEqual(participant.getRootPath(), projectSettings.getExternalProjectPath()))) {
        continue;
      }

      GradleBuildParticipant buildParticipant = new GradleBuildParticipant(projectSettings.getExternalProjectPath());
      ExternalProjectInfo projectData = ProjectDataManager.getInstance()
        .getExternalProjectData(project, GradleConstants.SYSTEM_ID, projectSettings.getExternalProjectPath());

      if (projectData == null || projectData.getExternalProjectStructure() == null) continue;

      Collection<DataNode<ModuleData>> moduleNodes =
        ExternalSystemApiUtil.findAll(projectData.getExternalProjectStructure(), ProjectKeys.MODULE);
      for (DataNode<ModuleData> moduleNode : moduleNodes) {
        ModuleData moduleData = moduleNode.getData();
        if (moduleData.getArtifacts().isEmpty()) {
          Collection<DataNode<GradleSourceSetData>> sourceSetNodes = ExternalSystemApiUtil.findAll(moduleNode, GradleSourceSetData.KEY);
          for (DataNode<GradleSourceSetData> sourceSetNode : sourceSetNodes) {
            buildParticipant.addModule(sourceSetNode.getData());
          }
        }
        else {
          buildParticipant.addModule(moduleData);
        }
      }
      result.getExecutionWorkspace().addBuildParticipant(buildParticipant);
    }
  }

  @Override
  public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) throws ExecutionException {
    final Set<String> additionalEntries = ContainerUtilRt.newHashSet();
    for (GradleProjectResolverExtension extension : RESOLVER_EXTENSIONS.getValue()) {
      ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(extension.getClass()));
      for (Class aClass : extension.getExtraProjectModelClasses()) {
        ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(aClass));
      }
      extension.enhanceRemoteProcessing(parameters);
    }

    final PathsList classPath = parameters.getClassPath();
    for (String entry : additionalEntries) {
      classPath.add(entry);
    }

    parameters.getVMParametersList().addProperty(
      ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY, GradleConstants.SYSTEM_ID.getId());
  }

  @Override
  public void enhanceLocalProcessing(@NotNull List<URL> urls) {
  }

  @NotNull
  @Override
  public Class<? extends ExternalSystemProjectResolver<GradleExecutionSettings>> getProjectResolverClass() {
    return GradleProjectResolver.class;
  }

  @Override
  public Class<? extends ExternalSystemTaskManager<GradleExecutionSettings>> getTaskManagerClass() {
    return GradleTaskManager.class;
  }

  @NotNull
  @Override
  public Configurable getConfigurable(@NotNull Project project) {
    return new GradleConfigurable(project);
  }

  @Nullable
  @Override
  public FileChooserDescriptor getExternalProjectConfigDescriptor() {
    // project *.gradle script can be absent for gradle subproject
    return FileChooserDescriptorFactory.createSingleFolderDescriptor();
  }

  @Nullable
  @Override
  public Icon getProjectIcon() {
    return GradleIcons.Gradle;
  }

  @Nullable
  @Override
  public Icon getTaskIcon() {
    return DefaultExternalSystemUiAware.INSTANCE.getTaskIcon();
  }

  @NotNull
  @Override
  public String getProjectRepresentationName(@NotNull String targetProjectPath, @Nullable String rootProjectPath) {
    return ExternalSystemApiUtil.getProjectRepresentationName(targetProjectPath, rootProjectPath);
  }

  @NotNull
  @Override
  public String getProjectRepresentationName(@NotNull Project project,
                                             @NotNull String targetProjectPath,
                                             @Nullable String rootProjectPath) {
    GradleProjectSettings projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(targetProjectPath);
    if (projectSettings != null && projectSettings.getCompositeBuild() != null) {
      for (BuildParticipant buildParticipant : projectSettings.getCompositeBuild().getCompositeParticipants()) {
        if (buildParticipant.getProjects().contains(targetProjectPath)) {
          return ExternalSystemApiUtil.getProjectRepresentationName(targetProjectPath, buildParticipant.getRootPath());
        }
      }
    }
    return ExternalSystemApiUtil.getProjectRepresentationName(targetProjectPath, rootProjectPath);
  }

  @Nullable
  @Override
  public String getAffectedExternalProjectPath(@NotNull String changedFileOrDirPath, @NotNull Project project) {
    return myAutoImportDelegate.getAffectedExternalProjectPath(changedFileOrDirPath, project);
  }

  @Override
  public List<File> getAffectedExternalProjectFiles(String projectPath, @NotNull Project project) {
    return myAutoImportDelegate.getAffectedExternalProjectFiles(projectPath, project);
  }

  @NotNull
  @Override
  public FileChooserDescriptor getExternalProjectDescriptor() {
    return GradleUtil.getGradleProjectFileChooserDescriptor();
  }

  @Nullable
  @Override
  public GlobalSearchScope getSearchScope(@NotNull Project project, @NotNull ExternalSystemTaskExecutionSettings taskExecutionSettings) {
    String projectPath = taskExecutionSettings.getExternalProjectPath();
    if (StringUtil.isEmpty(projectPath)) return null;

    GradleProjectSettings projectSettings = getSettingsProvider().fun(project).getLinkedProjectSettings(projectPath);
    if (projectSettings == null) return null;

    if (!projectSettings.isResolveModulePerSourceSet()) {
      // use default implementation which will find target module using projectPathFile
      return null;
    }
    else {
      Module[] modules = Arrays.stream(ModuleManager.getInstance(project).getModules())
        .filter(module -> StringUtil.equals(projectPath, ExternalSystemApiUtil.getExternalProjectPath(module)))
        .toArray(Module[]::new);
      return modules.length > 0 ? SearchScopeProvider.createSearchScope(modules) : null;
    }
  }

  @Nullable
  @Override
  public Object createTestConsoleProperties(@NotNull Project project,
                                            @NotNull Executor executor,
                                            @NotNull RunConfiguration runConfiguration) {
    if (runConfiguration instanceof ExternalSystemRunConfiguration) {
      return new GradleConsoleProperties((ExternalSystemRunConfiguration)runConfiguration, executor);
    }
    return null;
  }

  @Override
  public void runActivity(@NotNull final Project project) {
    // We want to automatically refresh linked projects on gradle service directory change.
    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(GradleSettings.getInstance(project).getChangesTopic(), new GradleSettingsListenerAdapter() {

      @Override
      public void onServiceDirectoryPathChange(@Nullable String oldPath, @Nullable String newPath) {
        for (GradleProjectSettings projectSettings : GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
          ExternalProjectsManager.getInstance(project).getExternalProjectsWatcher().markDirty(projectSettings.getExternalProjectPath());
        }
      }

      @Override
      public void onGradleHomeChange(@Nullable String oldPath, @Nullable String newPath, @NotNull String linkedProjectPath) {
        ExternalProjectsManager.getInstance(project).getExternalProjectsWatcher().markDirty(linkedProjectPath);
      }

      @Override
      public void onGradleDistributionTypeChange(DistributionType currentValue, @NotNull String linkedProjectPath) {
        ExternalProjectsManager.getInstance(project).getExternalProjectsWatcher().markDirty(linkedProjectPath);
      }
    });

    // We used to assume that gradle scripts are always named 'build.gradle' and kept path to that build.gradle file at ide settings.
    // However, it was found out that that is incorrect assumption (IDEA-109064). Now we keep paths to gradle script's directories
    // instead. However, we don't want to force old users to re-import gradle projects because of that. That's why we check gradle
    // config and re-point it from build.gradle to the parent dir if necessary.
    Map<String, String> adjustedPaths = patchLinkedProjects(project);
    if (adjustedPaths == null) {
      return;
    }

    GradleLocalSettings localSettings = GradleLocalSettings.getInstance(project);
    patchRecentTasks(adjustedPaths, localSettings);
    patchAvailableProjects(adjustedPaths, localSettings);
    patchAvailableTasks(adjustedPaths, localSettings);
  }

  @Nullable
  private static Map<String, String> patchLinkedProjects(@NotNull Project project) {
    GradleSettings settings = GradleSettings.getInstance(project);
    Collection<GradleProjectSettings> correctedSettings = ContainerUtilRt.newArrayList();
    Map<String/* old path */, String/* new path */> adjustedPaths = ContainerUtilRt.newHashMap();
    for (GradleProjectSettings projectSettings : settings.getLinkedProjectsSettings()) {
      String oldPath = projectSettings.getExternalProjectPath();
      if (oldPath != null && new File(oldPath).isFile() && FileUtilRt.extensionEquals(oldPath, GradleConstants.EXTENSION)) {
        try {
          String newPath = new File(oldPath).getParentFile().getCanonicalPath();
          projectSettings.setExternalProjectPath(newPath);
          adjustedPaths.put(oldPath, newPath);
        }
        catch (IOException e) {
          LOG.warn(String.format(
            "Unexpected exception occurred on attempt to re-point linked gradle project path from build.gradle to its parent dir. Path: %s",
            oldPath
          ), e);
        }
      }
      correctedSettings.add(projectSettings);
    }
    if (adjustedPaths.isEmpty()) {
      return null;
    }

    settings.setLinkedProjectsSettings(correctedSettings);
    return adjustedPaths;
  }

  private static void patchAvailableTasks(@NotNull Map<String, String> adjustedPaths, @NotNull GradleLocalSettings localSettings) {
    Map<String, Collection<ExternalTaskPojo>> adjustedAvailableTasks = ContainerUtilRt.newHashMap();
    for (Map.Entry<String, Collection<ExternalTaskPojo>> entry : localSettings.getAvailableTasks().entrySet()) {
      String newPath = adjustedPaths.get(entry.getKey());
      if (newPath == null) {
        adjustedAvailableTasks.put(entry.getKey(), entry.getValue());
      }
      else {
        for (ExternalTaskPojo task : entry.getValue()) {
          String newTaskPath = adjustedPaths.get(task.getLinkedExternalProjectPath());
          if (newTaskPath != null) {
            task.setLinkedExternalProjectPath(newTaskPath);
          }
        }
        adjustedAvailableTasks.put(newPath, entry.getValue());
      }
    }
    localSettings.setAvailableTasks(adjustedAvailableTasks);
  }

  private static void patchAvailableProjects(@NotNull Map<String, String> adjustedPaths, @NotNull GradleLocalSettings localSettings) {
    Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> adjustedAvailableProjects = ContainerUtilRt.newHashMap();
    for (Map.Entry<ExternalProjectPojo, Collection<ExternalProjectPojo>> entry : localSettings.getAvailableProjects().entrySet()) {
      String newPath = adjustedPaths.get(entry.getKey().getPath());
      if (newPath == null) {
        adjustedAvailableProjects.put(entry.getKey(), entry.getValue());
      }
      else {
        adjustedAvailableProjects.put(new ExternalProjectPojo(entry.getKey().getName(), newPath), entry.getValue());
      }
    }
    localSettings.setAvailableProjects(adjustedAvailableProjects);
  }

  private static void patchRecentTasks(@NotNull Map<String, String> adjustedPaths, @NotNull GradleLocalSettings localSettings) {
    for (ExternalTaskExecutionInfo taskInfo : localSettings.getRecentTasks()) {
      ExternalSystemTaskExecutionSettings s = taskInfo.getSettings();
      String newPath = adjustedPaths.get(s.getExternalProjectPath());
      if (newPath != null) {
        s.setExternalProjectPath(newPath);
      }
    }
  }
}
