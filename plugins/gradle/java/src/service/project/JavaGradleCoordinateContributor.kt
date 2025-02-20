// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.java.library.MavenCoordinates
import com.intellij.java.library.getMavenCoordinates
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.ExternalSystemCoordinateContributor
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.util.GradleConstants

class JavaGradleCoordinateContributor: ExternalSystemCoordinateContributor {

  override fun findLibraryCoordinate(library: Library): ProjectCoordinate? {
    val librarySource = library.externalSource ?: return null
    if (librarySource.id != GradleConstants.SYSTEM_ID.id) {
      return null
    }
    return library.getMavenCoordinates()?.toProjectCoordinate()
  }

  private fun MavenCoordinates.toProjectCoordinate(): ProjectCoordinate {
    return ProjectId(groupId, artifactId, version)
  }
}