// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ArtifactCoordinatesUtil")

package org.jetbrains.plugins.gradle.service.sources

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.function.Predicate

private const val ANDROID_LIBRARY_SUFFIX = "@aar"

fun LibraryOrderEntry.getArtifactCoordinates(): String? {
  val libraryName = getLibraryName() ?: return null
  val artifactCoordinates = libraryName.removePrefix("${GradleConstants.SYSTEM_ID.readableName}: ")
  if (libraryName == artifactCoordinates) {
    return null
  }
  return parseArtifactCoordinates(artifactCoordinates) { idCandidate ->
    isArtifactId(idCandidate, this@getArtifactCoordinates)
  }
}

@VisibleForTesting
fun parseArtifactCoordinates(artifactCoordinates: String, artifactIdChecker: Predicate<String>): String {
  val rawCoordinates = artifactCoordinates.split(":").let {
    when {
      it.size == 4 && artifactIdChecker.test(it[1]) -> it[0] + ":" + it[1] + ":" + it[3]
      it.size == 5 -> it[0] + ":" + it[1] + ":" + it[4]
      else -> artifactCoordinates
    }
  }
  return rawCoordinates.removeSuffix(ANDROID_LIBRARY_SUFFIX)
}

fun getLibraryUnifiedCoordinates(sourceArtifactNotation: String): UnifiedCoordinates? =
  sourceArtifactNotation.replace(ANDROID_LIBRARY_SUFFIX, "")
    .split(":")
    .let { if (it.size < 3) null else UnifiedCoordinates(it[0], it[1], it[2]) }

private fun isArtifactId(artifactIdCandidate: String, libraryOrderEntry: LibraryOrderEntry): Boolean {
  val rootFiles = libraryOrderEntry.getRootFiles(OrderRootType.CLASSES)
  return rootFiles.size == 0 || rootFiles.any { file -> file.getName().startsWith(artifactIdCandidate) }
}
