// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.data;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ExternalModuleBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.externalSystem.settings.ProjectBuildClasspathManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DependencyAccessorsModel;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
@Order(ExternalSystemConstants.UNORDERED)
public final class BuildClasspathModuleGradleDataService extends AbstractProjectDataService<BuildScriptClasspathData, Module> {
  private static final Logger LOG = Logger.getInstance(BuildClasspathModuleGradleDataService.class);

  @Override
  public @NotNull Key<BuildScriptClasspathData> getTargetDataKey() {
    return BuildScriptClasspathData.KEY;
  }

  @Override
  public void importData(final @NotNull Collection<? extends DataNode<BuildScriptClasspathData>> toImport,
                         final @Nullable ProjectData projectData,
                         final @NotNull Project project,
                         final @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (projectData == null || toImport.isEmpty()) {
      return;
    }

    final GradleInstallationManager gradleInstallationManager = GradleInstallationManager.getInstance();

    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
    assert manager != null;
    ProjectBuildClasspathManager buildClasspathManager = project.getService(ProjectBuildClasspathManager.class);

    final String linkedExternalProjectPath = projectData.getLinkedExternalProjectPath();
    final File gradleHomeDir = toImport.iterator().next().getData().getGradleHomeDir();
    final GradleLocalSettings gradleLocalSettings = GradleLocalSettings.getInstance(project);
    if (gradleHomeDir != null) {
      gradleLocalSettings.setGradleHome(linkedExternalProjectPath, gradleHomeDir.getPath());
    }
    final GradleProjectSettings settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedExternalProjectPath);

    Interner<List<String>> interner = Interner.createInterner();
    final NotNullLazyValue<List<String>> externalProjectGradleSdkLibs = NotNullLazyValue.lazy(() -> {
      final Set<String> gradleSdkLibraries = new LinkedHashSet<>();
      File gradleHome = gradleInstallationManager.getGradleHome(project, linkedExternalProjectPath);
      if (gradleHome != null && gradleHome.isDirectory()) {
        final Collection<File> libraries = gradleInstallationManager.getClassRoots(project, linkedExternalProjectPath);
        if (libraries != null) {
          for (File library : libraries) {
            gradleSdkLibraries.add(FileUtil.toCanonicalPath(library.getPath()));
          }
        }
      }
      return interner.intern(new ArrayList<>(gradleSdkLibraries));
    });

    final Map<String, ExternalProjectBuildClasspathPojo> localProjectBuildClasspath =
      new HashMap<>(buildClasspathManager.getProjectBuildClasspath());

    for (final DataNode<BuildScriptClasspathData> node : toImport) {
      if (GradleConstants.SYSTEM_ID.equals(node.getData().getOwner())) {
        DataNode<ModuleData> moduleDataNode = ExternalSystemApiUtil.findParent(node, ProjectKeys.MODULE);
        if (moduleDataNode == null) continue;

        String externalModulePath = moduleDataNode.getData().getLinkedExternalProjectPath();
        if (settings == null || settings.getDistributionType() == null) {
          LOG.warn("Gradle SDK distribution type was not configured for the project at " + linkedExternalProjectPath);
        }

        final Set<String> buildClasspathSources = new LinkedHashSet<>();
        final Set<String> buildClasspathClasses = new LinkedHashSet<>();
        BuildScriptClasspathData buildScriptClasspathData = node.getData();
        for (BuildScriptClasspathData.ClasspathEntry classpathEntry : buildScriptClasspathData.getClasspathEntries()) {
          for (String path : classpathEntry.getSourcesFile()) {
            buildClasspathSources.add(FileUtil.toCanonicalPath(path));
          }

          for (String path : classpathEntry.getClassesFile()) {
            buildClasspathClasses.add(FileUtil.toCanonicalPath(path));
          }
        }

        ExternalProjectBuildClasspathPojo projectBuildClasspathPojo = localProjectBuildClasspath.get(linkedExternalProjectPath);
        if (projectBuildClasspathPojo == null) {
          projectBuildClasspathPojo = new ExternalProjectBuildClasspathPojo(
            moduleDataNode.getData().getExternalName(), new ArrayList<>(), new HashMap<>());
          localProjectBuildClasspath.put(linkedExternalProjectPath, projectBuildClasspathPojo);
        }

        projectBuildClasspathPojo.setProjectBuildClasspath(externalProjectGradleSdkLibs.getValue());

        List<String> buildClasspath = new ArrayList<>(buildClasspathSources.size() + buildClasspathClasses.size());
        buildClasspath.addAll(buildClasspathSources);
        buildClasspath.addAll(buildClasspathClasses);
        buildClasspath = interner.intern(buildClasspath);

        projectBuildClasspathPojo.getModulesBuildClasspath().put(externalModulePath,
                                                                 new ExternalModuleBuildClasspathPojo(externalModulePath, buildClasspath));

        DataNode<ProjectData> projectDataNode = ExternalSystemApiUtil.findParent(moduleDataNode, ProjectKeys.PROJECT);
        if (projectDataNode != null) {
          DataNode<DependencyAccessorsModel> dependenciesAccessorsModelNode =
            ExternalSystemApiUtil.find(projectDataNode, BuildScriptClasspathData.ACCESSORS);
          if (dependenciesAccessorsModelNode != null) {
            DependencyAccessorsModel accessorsModel = dependenciesAccessorsModelNode.getData();
            buildClasspath.addAll(accessorsModel.getSources());
            buildClasspath.addAll(accessorsModel.getClasses());
          }
        }
      }
    }

    buildClasspathManager.setProjectBuildClasspathSync(localProjectBuildClasspath);
  }

  @Override
  public void onSuccessImport(@NotNull Collection<DataNode<BuildScriptClasspathData>> imported,
                              @Nullable ProjectData projectData,
                              @NotNull Project project,
                              @NotNull IdeModelsProvider modelsProvider) {
    if (!project.isDisposed()) {
      project.getService(ProjectBuildClasspathManager.class).removeUnavailableClasspaths();
      GradleBuildClasspathManager.getInstance(project).reload();
    }
  }
}
