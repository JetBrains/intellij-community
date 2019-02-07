// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi.resolve

import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocation
import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocationProvider
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.BuildNumber

/**
 * Provides coordinates of IntelliJ API external annotations artifacts.
 */
class IdeExternalAnnotationsLocationProvider : AnnotationsLocationProvider {

  override fun getLocations(
    library: Library,
    artifactId: String?,
    groupId: String?,
    version: String?
  ): Collection<AnnotationsLocation> {
    if (groupId == null || artifactId == null || version == null) {
      return emptyList()
    }
    if (isIdeaArtifactDependency(groupId, artifactId)
      || isGradleIntelliJPluginImportedIdea(groupId, artifactId)
      || isKotlinIntelliJDependency(groupId, artifactId)
    ) {
      return getAnnotationsLocations(version)
    }
    return emptyList()
  }

  private fun isIdeaArtifactDependency(groupId: String, artifactId: String): Boolean =
    groupId == "com.jetbrains.intellij.idea" && (artifactId == "ideaIC" || artifactId == "ideaIU")

  private fun isGradleIntelliJPluginImportedIdea(groupId: String, artifactId: String): Boolean =
    groupId == "com.jetbrains" && (artifactId == "ideaIC" || artifactId == "ideaIU")

  private fun isKotlinIntelliJDependency(groupId: String, artifactId: String): Boolean =
    groupId == "kotlin.build.custom.deps" && (artifactId == "intellij" || artifactId == "intellij-core")

  private fun getAnnotationsLocations(ideVersion: String): List<AnnotationsLocation> {
    val annotationsVersion = if (ideVersion.endsWith("-SNAPSHOT")) {
      ideVersion
    } else {
      val ideBuildNumber = BuildNumber.fromStringOrNull(ideVersion) ?: return emptyList()
      "${ideBuildNumber.baselineVersion}.999999"
    }
    return listOf(AnnotationsLocation(
      "com.jetbrains.intellij.idea",
      "ideaIU",
      annotationsVersion,
      PublicIdeExternalAnnotationsRepository.RELEASES_REPO_URL,
      PublicIdeExternalAnnotationsRepository.SNAPSHOTS_REPO_URL
    ))
  }
}
