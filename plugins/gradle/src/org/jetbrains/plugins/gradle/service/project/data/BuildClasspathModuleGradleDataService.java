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
package org.jetbrains.plugins.gradle.service.project.data;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ExternalModuleBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 8/27/13
 */
@Order(ExternalSystemConstants.UNORDERED)
public class BuildClasspathModuleGradleDataService implements ProjectDataService<BuildScriptClasspathData, Module> {

  @NotNull
  @Override
  public Key<BuildScriptClasspathData> getTargetDataKey() {
    return BuildScriptClasspathData.KEY;
  }

  @Override
  public void importData(@NotNull final Collection<DataNode<BuildScriptClasspathData>> toImport,
                         @NotNull final Project project,
                         boolean synchronous) {
    if (toImport.isEmpty()) {
      return;
    }
    if (!project.isInitialized()) {
      return;
    }

    final GradleInstallationManager gradleInstallationManager = ServiceManager.getService(GradleInstallationManager.class);

    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
    assert manager != null;
    AbstractExternalSystemLocalSettings localSettings = manager.getLocalSettingsProvider().fun(project);

    //noinspection MismatchedQueryAndUpdateOfCollection
    Map<String/* externalProjectPath */, Set<String>> externalProjectGradleSdkLibs = new FactoryMap<String, Set<String>>() {
      @Nullable
      @Override
      protected Set<String> create(String externalProjectPath) {
        GradleProjectSettings settings = GradleSettings.getInstance(project).getLinkedProjectSettings(externalProjectPath);
        if (settings == null || settings.getDistributionType() == null) return null;

        final Set<String> gradleSdkLibraries = ContainerUtil.newLinkedHashSet();
        File gradleHome =
          gradleInstallationManager.getGradleHome(settings.getDistributionType(), externalProjectPath, settings.getGradleHome());
        if (gradleHome != null && gradleHome.isDirectory()) {

          final Collection<File> libraries = gradleInstallationManager.getClassRoots(project, externalProjectPath);
          if (libraries != null) {
            for (File library : libraries) {
              gradleSdkLibraries.add(FileUtil.toCanonicalPath(library.getPath()));
            }
          }
        }
        return gradleSdkLibraries;
      }
    };

    for (final DataNode<BuildScriptClasspathData> node : toImport) {
      if (GradleConstants.SYSTEM_ID.equals(node.getData().getOwner())) {


        DataNode<ProjectData> projectDataNode = ExternalSystemApiUtil.findParent(node, ProjectKeys.PROJECT);
        assert projectDataNode != null;

        String linkedExternalProjectPath = projectDataNode.getData().getLinkedExternalProjectPath();
        DataNode<ModuleData> moduleDataNode = ExternalSystemApiUtil.findParent(node, ProjectKeys.MODULE);
        if (moduleDataNode == null) continue;

        String externalModulePath = moduleDataNode.getData().getLinkedExternalProjectPath();
        GradleProjectSettings settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedExternalProjectPath);
        if (settings == null || settings.getDistributionType() == null) continue;

        final Set<String> buildClasspath = ContainerUtil.newLinkedHashSet();
        BuildScriptClasspathData buildScriptClasspathData = node.getData();
        for (BuildScriptClasspathData.ClasspathEntry classpathEntry : buildScriptClasspathData.getClasspathEntries()) {
          for (String path : classpathEntry.getSourcesFile()) {
            buildClasspath.add(FileUtil.toCanonicalPath(path));
          }

          for (String path : classpathEntry.getClassesFile()) {
            buildClasspath.add(FileUtil.toCanonicalPath(path));
          }
        }

        ExternalProjectBuildClasspathPojo projectBuildClasspathPojo =
          localSettings.getProjectBuildClasspath().get(linkedExternalProjectPath);
        if (projectBuildClasspathPojo == null) {
          projectBuildClasspathPojo = new ExternalProjectBuildClasspathPojo(
            moduleDataNode.getData().getExternalName(),
            ContainerUtil.<String>newArrayList(),
            ContainerUtil.<String, ExternalModuleBuildClasspathPojo>newHashMap());
          localSettings.getProjectBuildClasspath().put(linkedExternalProjectPath, projectBuildClasspathPojo);
        }

        List<String> projectBuildClasspath = ContainerUtil.newArrayList(externalProjectGradleSdkLibs.get(linkedExternalProjectPath));
        // add main java root of buildSrc project
        projectBuildClasspath.add(linkedExternalProjectPath + "/buildSrc/src/main/java");
        // add main groovy root of buildSrc project
        projectBuildClasspath.add(linkedExternalProjectPath + "/buildSrc/src/main/groovy");

        projectBuildClasspathPojo.setProjectBuildClasspath(projectBuildClasspath);
        projectBuildClasspathPojo.getModulesBuildClasspath().put(
          externalModulePath, new ExternalModuleBuildClasspathPojo(externalModulePath, ContainerUtil.newArrayList(buildClasspath)));
      }
    }

    GradleBuildClasspathManager.getInstance(project).reload();
  }

  @Override
  public void removeData(@NotNull Collection<? extends Module> toRemove, @NotNull Project project, boolean synchronous) {
  }
}
