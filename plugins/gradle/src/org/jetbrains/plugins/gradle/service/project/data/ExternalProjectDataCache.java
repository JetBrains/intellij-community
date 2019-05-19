// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.data;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.FilePathHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
public class ExternalProjectDataCache {
  private static final Logger LOG = Logger.getInstance(ExternalProjectDataCache.class);

  public static ExternalProjectDataCache getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ExternalProjectDataCache.class);
  }

  @NotNull private final Map<String, ExternalProject> myExternalRootProjects;

  public ExternalProjectDataCache() {
    myExternalRootProjects = ConcurrentFactoryMap.create(key-> new ExternalProjectSerializer().load(GradleConstants.SYSTEM_ID, new File(key)),
                                                         () -> ConcurrentCollectionFactory.createMap(FilePathHashingStrategy.create()));
  }

  /**
   * Kept for compatibility with Kotlin
   * @deprecated since 2019.1
   */
  @Deprecated
  @Nullable
  public ExternalProject getRootExternalProject(@NotNull ProjectSystemId systemId, @NotNull File projectRootDir) {
    if (GradleConstants.SYSTEM_ID != systemId) {
      throw new IllegalStateException("Attempt to use Gradle-specific cache with illegal system id [" + systemId.getReadableName() + "]");
    }
    return getRootExternalProject(ExternalSystemApiUtil.toCanonicalPath(projectRootDir.getAbsolutePath()));
  }

  @Nullable
  public ExternalProject getRootExternalProject(@NotNull String externalProjectPath) {
    ExternalProject externalProject = myExternalRootProjects.get(externalProjectPath);
    if (externalProject == null && LOG.isDebugEnabled()) {
      LOG.debug("Can not find data for project at: " + externalProjectPath);
      LOG.debug("Existing imported projects paths: " + ContainerUtil.map(
        myExternalRootProjects.entrySet(),
        (Function<Map.Entry<String, ExternalProject>, Object>)entry -> {
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
      ExternalSystemApiUtil.toCanonicalPath(externalProject.getProjectDir().getAbsolutePath()),
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
