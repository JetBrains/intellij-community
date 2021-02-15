// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi.resolve

import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocation
import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocationProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.BuildNumber

/**
 * Provides coordinates of external annotations artifacts for IntelliJ SDK.
 */
class IdeExternalAnnotationsLocationProvider : AnnotationsLocationProvider {

  override fun getLocations(
    project: Project,
    library: Library,
    artifactId: String?,
    groupId: String?,
    version: String?
  ): Collection<AnnotationsLocation> {
    if (groupId == null || artifactId == null || version == null) {
      return emptyList()
    }
    val libraries = LibrariesWithIntellijClassesSetting.getInstance(project).state.intellijApiContainingLibraries
    if (libraries.any { it.groupId == groupId && it.artifactId == artifactId }) {
      return getAnnotationsLocations(version)
    }
    return emptyList()
  }

  @Suppress("HardCodedStringLiteral")
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
      PublicIntelliJSdkExternalAnnotationsRepository.RELEASES_REPO_URL,
      PublicIntelliJSdkExternalAnnotationsRepository.SNAPSHOTS_REPO_URL
    ))
  }
}
