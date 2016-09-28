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

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.annotations.NotNull

/**
 * @author Vladislav.Soroka
 * @since 5/19/2016
 */
class SourceSetCachedFinder {
  private final myArtifactsMap = new HashMap<String, SourceSet>()

  @SuppressWarnings("GrUnresolvedAccess")
  SourceSetCachedFinder(@NotNull Project project) {
    def rootProject = project.rootProject
    rootProject.subprojects.each { Project p ->
      if (p.hasProperty("sourceSets") && (p.sourceSets instanceof SourceSetContainer)) {
        p.sourceSets.each { SourceSet sourceSet ->
          def jarTask = p.tasks.findByName(sourceSet.getJarTaskName())
          def file = jarTask?.archivePath as File
          if (file) {
            myArtifactsMap[file.path] = sourceSet
          }
        }
      }
    }
  }

  SourceSet findByArtifact(String artifactPath) {
    return myArtifactsMap[artifactPath]
  }
}
