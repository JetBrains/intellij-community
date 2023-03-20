// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.data;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
@Service(Service.Level.PROJECT)
public final class ExternalProjectDataCache {
  @ApiStatus.Internal public static final @NotNull Key<ExternalProject> KEY = Key.create(ExternalProject.class, ProjectKeys.TASK.getProcessingWeight() + 1);
  private final Project myProject;

  public static ExternalProjectDataCache getInstance(@NotNull Project project) {
    return project.getService(ExternalProjectDataCache.class);
  }

  public ExternalProjectDataCache(@NotNull Project project) {
    myProject = project;
  }

  public @Nullable ExternalProject getRootExternalProject(@NotNull String externalProjectPath) {
    ExternalProjectInfo projectData =
      ProjectDataManager.getInstance().getExternalProjectData(myProject, GradleConstants.SYSTEM_ID, externalProjectPath);
    if (projectData == null) return null;
    DataNode<ProjectData> projectStructure = projectData.getExternalProjectStructure();
    if (projectStructure == null) return null;
    DataNode<ExternalProject> projectDataNode = ExternalSystemApiUtil.find(projectStructure, KEY);
    if (projectDataNode == null) return null;
    return projectDataNode.getData();
  }

  public @NotNull Map<String, ExternalSourceSet> findExternalProject(@NotNull ExternalProject parentProject, @NotNull Module module) {
    String externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module);
    boolean isSourceSet = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(ExternalSystemApiUtil.getExternalModuleType(module));
    return externalProjectId != null ? findExternalProject(parentProject, externalProjectId, isSourceSet)
                                     : Collections.emptyMap();
  }

  private static @NotNull Map<String, ExternalSourceSet> findExternalProject(@NotNull ExternalProject parentProject,
                                                                             @NotNull String externalProjectId,
                                                                             boolean isSourceSet) {
    ArrayDeque<ExternalProject> queue = new ArrayDeque<>();
    queue.add(parentProject);

    ExternalProject externalProject;
    while ((externalProject = queue.pollFirst()) != null) {
      final String projectId = externalProject.getId();
      boolean isRelatedProject = projectId.equals(externalProjectId);
      final Map<String, ExternalSourceSet> result = new HashMap<>();
      for (Map.Entry<String, ? extends ExternalSourceSet> sourceSetEntry : externalProject.getSourceSets().entrySet()) {
        final String sourceSetName = sourceSetEntry.getKey();
        final String sourceSetId = projectId + ":" + sourceSetName;
        if (isRelatedProject || (isSourceSet && externalProjectId.equals(sourceSetId))) {
          result.put(sourceSetName, sourceSetEntry.getValue());
        }
      }
      if(!result.isEmpty() || isRelatedProject) return result;

      queue.addAll(externalProject.getChildProjects().values());
    }

    return Collections.emptyMap();
  }


}
