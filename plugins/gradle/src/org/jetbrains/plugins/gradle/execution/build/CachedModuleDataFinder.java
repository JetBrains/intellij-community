/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 5/14/2016
 */
public class CachedModuleDataFinder {
  private Map<String, DataNode<ModuleData>> cache = ContainerUtil.newHashMap();

  @Nullable
  public DataNode<ModuleData> findModuleData(final DataNode parentNode, final String projectPath) {
    DataNode<ModuleData> node = cache.get(projectPath);
    if (node != null) return node;

    //noinspection unchecked
    return (DataNode<ModuleData>)ExternalSystemApiUtil.findFirstRecursively(parentNode, node1 -> {
      if ((ProjectKeys.MODULE.equals(node1.getKey()) ||
           GradleSourceSetData.KEY.equals(node1.getKey())) && node1.getData() instanceof ModuleData) {
        String externalProjectPath = ((ModuleData)node1.getData()).getLinkedExternalProjectPath();
        //noinspection unchecked
        DataNode<ModuleData> myNode = (DataNode<ModuleData>)node1;
        cache.put(externalProjectPath, myNode);

        return StringUtil.equals(projectPath, ((ModuleData)node1.getData()).getLinkedExternalProjectPath());
      }

      return false;
    });
  }

  @Nullable
  public DataNode<ModuleData> findModuleData(@NotNull Module module) {
    final String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
    if (rootProjectPath == null) return null;

    final String projectId = ExternalSystemApiUtil.getExternalProjectId(module);
    if (projectId == null) return null;
    final String externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
    if (externalProjectPath == null || StringUtil.endsWith(externalProjectPath, "buildSrc")) return null;

    ExternalProjectInfo projectData =
      ProjectDataManager.getInstance().getExternalProjectData(module.getProject(), GradleConstants.SYSTEM_ID, rootProjectPath);
    if (projectData == null) return null;

    DataNode<ProjectData> projectStructure = projectData.getExternalProjectStructure();
    if (projectStructure == null) return null;

    return findModuleData(projectStructure, externalProjectPath);
  }
}
