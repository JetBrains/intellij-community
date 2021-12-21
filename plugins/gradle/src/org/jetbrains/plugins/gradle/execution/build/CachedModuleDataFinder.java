// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleModuleData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Vladislav.Soroka
 */
public class CachedModuleDataFinder {
  private final @Nullable Project myProject;
  private final Map<String, DataNode<? extends ModuleData>> cache = new ConcurrentHashMap<>();

  /**
   * @deprecated use {@link #getInstance(Project)}
   */
  @Deprecated
  public CachedModuleDataFinder() {
    myProject = null;
  }

  private CachedModuleDataFinder(@NotNull Project project) {
    myProject = project;
  }

  public static CachedModuleDataFinder getInstance(@NotNull Project project) {
    return new CachedModuleDataFinder(project);
  }

  @ApiStatus.Experimental
  public static @Nullable GradleModuleData getGradleModuleData(@NotNull Module module) {
    DataNode<? extends ModuleData> moduleData = getInstance(module.getProject()).findMainModuleData(module);
    return moduleData != null ? new GradleModuleData(moduleData) : null;
  }

  public static @Nullable DataNode<? extends ModuleData> findModuleData(@NotNull Project project, @NotNull String modulePath) {
    var projectNode = ExternalSystemApiUtil.findProjectData(project, GradleConstants.SYSTEM_ID, modulePath);
    if (projectNode == null) return null;

    return getInstance(project).findModuleData(projectNode, modulePath);
  }

  @Nullable
  private DataNode<? extends ModuleData> findModuleData(final DataNode<ProjectData> projectNode, final String projectPath) {
    DataNode<? extends ModuleData> cachedNode = getCache().get(projectPath);
    if (cachedNode != null) return cachedNode;

    return ExternalSystemApiUtil.find(projectNode, ProjectKeys.MODULE, node -> {
      String externalProjectPath = node.getData().getLinkedExternalProjectPath();
      getCache().put(externalProjectPath, node);
      return StringUtil.equals(projectPath, node.getData().getLinkedExternalProjectPath());
    });
  }

  @Nullable
  public DataNode<? extends ModuleData> findModuleData(@NotNull Module module) {
    DataNode<? extends ModuleData> mainModuleData = findMainModuleData(module);
    if (mainModuleData == null) return null;

    boolean isSourceSet = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(ExternalSystemApiUtil.getExternalModuleType(module));
    if (!isSourceSet) {
      return mainModuleData;
    }
    else {
      String projectId = ExternalSystemApiUtil.getExternalProjectId(module);
      DataNode<? extends ModuleData> cachedNode = getCache().get(projectId);
      if (cachedNode != null) return cachedNode;

      return ExternalSystemApiUtil.find(mainModuleData, GradleSourceSetData.KEY, node -> {
        String id = node.getData().getId();
        getCache().put(id, node);
        return StringUtil.equals(projectId, id);
      });
    }
  }

  @Nullable
  public DataNode<? extends ModuleData> findMainModuleData(@NotNull Module module) {
    final String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
    if (rootProjectPath == null) return null;

    final String projectId = ExternalSystemApiUtil.getExternalProjectId(module);
    if (projectId == null) return null;
    final String externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
    if (externalProjectPath == null) return null;

    ExternalProjectInfo projectData =
      ProjectDataManager.getInstance().getExternalProjectData(module.getProject(), GradleConstants.SYSTEM_ID, rootProjectPath);
    if (projectData == null) return null;

    DataNode<ProjectData> projectStructure = projectData.getExternalProjectStructure();
    if (projectStructure == null) return null;

    return findModuleData(projectStructure, externalProjectPath);
  }

  private Map<String, DataNode<? extends ModuleData>> getCache() {
    return myProject == null ? cache : getCache(myProject);
  }

  private static Map<String, DataNode<? extends ModuleData>> getCache(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      return CachedValueProvider.Result.create(new ConcurrentHashMap<>(), ExternalProjectsDataStorage.getInstance(project));
    });
  }
}
