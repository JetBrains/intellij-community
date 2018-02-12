/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Queue;

/**
 * @author Vladislav.Soroka
 * @since 9/29/2015
 */
public class ExternalProjectDataCache {
  private static final Logger LOG = Logger.getInstance(ExternalProjectDataCache.class);

  public static ExternalProjectDataCache getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ExternalProjectDataCache.class);
  }

  @NotNull private final Project myProject;

  @NotNull private final Map<Pair<ProjectSystemId, File>, ExternalProject> myExternalRootProjects;

  public ExternalProjectDataCache(@NotNull Project project) {
    myProject = project;
    myExternalRootProjects = ConcurrentFactoryMap.createMap(key->
      new ExternalProjectSerializer().load(key.first, key.second),

                                                            () -> ConcurrentCollectionFactory.createMap(ExternalSystemUtil.HASHING_STRATEGY)
    );
  }


  @Nullable
  public ExternalProject getRootExternalProject(@NotNull ProjectSystemId systemId, @NotNull File projectRootDir) {
    ExternalProject externalProject = myExternalRootProjects.get(Pair.create(systemId, projectRootDir));
    if (LOG.isDebugEnabled()) {
      LOG.debug("Can not find data for project at: " + projectRootDir);
      LOG.debug("Existing imported projects paths: " + ContainerUtil.map(
        myExternalRootProjects.entrySet(),
        (Function<Map.Entry<Pair<ProjectSystemId, File>, ExternalProject>, Object>)entry -> {
          //noinspection ConstantConditions
          if (!(entry.getValue() instanceof ExternalProject)) return null;
          return Pair.create(entry.getKey(), entry.getValue().getProjectDir());
        }));
    }
    return externalProject;
  }

  public void saveExternalProject(@NotNull ExternalProject externalProject) {
    DefaultExternalProject value = new DefaultExternalProject(externalProject);
    new ExternalProjectSerializer().save(value);
    myExternalRootProjects.put(
      Pair.create(new ProjectSystemId(externalProject.getExternalSystemId()), externalProject.getProjectDir()),
      value
    );
  }

  @NotNull
  public Map<String, ExternalSourceSet> findExternalProject(@NotNull ExternalProject parentProject, @NotNull Module module) {
    String externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module);
    boolean isSourceSet = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(ExternalSystemApiUtil.getExternalModuleType(module));
    return externalProjectId != null ? findExternalProject(parentProject, externalProjectId, isSourceSet)
                                     : Collections.emptyMap();
  }

  @NotNull
  private static Map<String, ExternalSourceSet> findExternalProject(@NotNull ExternalProject parentProject,
                                                                    @NotNull String externalProjectId,
                                                                    boolean isSourceSet) {
    Queue<ExternalProject> queue = ContainerUtil.newLinkedList();
    queue.add(parentProject);

    while (!queue.isEmpty()) {
      final ExternalProject externalProject = queue.remove();
      final String projectId = externalProject.getId();
      boolean isRelatedProject = projectId.equals(externalProjectId);
      final Map<String, ExternalSourceSet> result = ContainerUtil.newHashMap();
      for (Map.Entry<String, ExternalSourceSet> sourceSetEntry : externalProject.getSourceSets().entrySet()) {
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
