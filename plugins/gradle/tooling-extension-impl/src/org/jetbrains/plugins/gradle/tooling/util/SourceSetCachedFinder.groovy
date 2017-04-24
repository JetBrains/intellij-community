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
package org.jetbrains.plugins.gradle.tooling.util

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.annotations.NotNull

/**
 * @author Vladislav.Soroka
 * @since 5/19/2016
 */
@CompileStatic
class SourceSetCachedFinder {
  private final Map<String, SourceSet> myArtifactsMap = new HashMap<String, SourceSet>()

  @SuppressWarnings("GrUnresolvedAccess")
  SourceSetCachedFinder(@NotNull Project project) {
    def rootProject = project.rootProject
    for (Project p : rootProject.subprojects) {
      SourceSetContainer sourceSetContainer = getSourceSetContainer(p)
      if(sourceSetContainer == null || sourceSetContainer.isEmpty()) continue

      for (SourceSet sourceSet : sourceSetContainer) {
        def task = p.tasks.findByName(sourceSet.getJarTaskName())
        if (task instanceof AbstractArchiveTask) {
          AbstractArchiveTask jarTask = (AbstractArchiveTask)task
          def archivePath = jarTask?.getArchivePath()
          if (archivePath) {
            myArtifactsMap[archivePath.path] = sourceSet
          }
        }
      }
    }
  }

  SourceSet findByArtifact(String artifactPath) {
    myArtifactsMap[artifactPath]
  }

  static JavaPluginConvention getJavaPluginConvention(Project p) {
    p.convention.findPlugin(JavaPluginConvention)
  }

  static SourceSetContainer getSourceSetContainer(Project p) {
    getJavaPluginConvention(p)?.sourceSets
  }
}
