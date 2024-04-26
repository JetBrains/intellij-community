// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.missingApi.resolve

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.indeterminateStep
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor

/**
 * Default implementation of [IntelliJSdkExternalAnnotationsRepository] that delegates to [JarRepositoryManager]
 * for searching and downloading artifacts from the IntelliJ Artifacts Repositories.
 */
class PublicIntelliJSdkExternalAnnotationsRepository(private val project: Project) : IntelliJSdkExternalAnnotationsRepository {

  companion object {
    const val RELEASES_REPO_URL = "https://www.jetbrains.com/intellij-repository/releases/"
    const val SNAPSHOTS_REPO_URL = "https://www.jetbrains.com/intellij-repository/snapshots/"

    val RELEASES_REPO_DESCRIPTION = RemoteRepositoryDescription(
      "IntelliJ Artifacts Repository (Releases)",
      "IntelliJ Artifacts Repository (Releases)",
      RELEASES_REPO_URL
    )

    val SNAPSHOTS_REPO_DESCRIPTION = RemoteRepositoryDescription(
      "IntelliJ Artifacts Repository (Snapshots)",
      "IntelliJ Artifacts Repository (Snapshots)",
      SNAPSHOTS_REPO_URL
    )

  }

  private fun getAnnotationsCoordinates(): Pair<String, String> {
    //Currently, for any IDE download ideaIU's annotations.
    return "com.jetbrains.intellij.idea" to "ideaIU"
  }

  override suspend fun downloadExternalAnnotations(ideBuildNumber: BuildNumber): IntelliJSdkExternalAnnotations? {
    val (groupId, artifactId) = getAnnotationsCoordinates()

    val lastReleaseVersion = "${ideBuildNumber.baselineVersion}.999999"
    val lastReleaseAnnotations = tryDownload(groupId, artifactId, lastReleaseVersion, listOf(RELEASES_REPO_DESCRIPTION))
    if (lastReleaseAnnotations != null && lastReleaseAnnotations.annotationsBuild >= ideBuildNumber) {
      return lastReleaseAnnotations
    }

    @NonNls val snapshotVersion = "${ideBuildNumber.baselineVersion}-SNAPSHOT"
    val snapshotAnnotations = tryDownload(groupId, artifactId, snapshotVersion, listOf(SNAPSHOTS_REPO_DESCRIPTION))
    if (snapshotAnnotations != null && snapshotAnnotations.annotationsBuild >= ideBuildNumber) {
      return snapshotAnnotations
    }

    @NonNls val latestTrunkSnapshot = "LATEST-TRUNK-SNAPSHOT"
    val latestTrunkSnapshotAnnotations = tryDownload(groupId, artifactId, latestTrunkSnapshot, listOf(SNAPSHOTS_REPO_DESCRIPTION))
    if (latestTrunkSnapshotAnnotations != null && latestTrunkSnapshotAnnotations.annotationsBuild >= ideBuildNumber) {
      return latestTrunkSnapshotAnnotations
    }

    return sequenceOf(lastReleaseAnnotations, snapshotAnnotations,
                      latestTrunkSnapshotAnnotations).filterNotNull().maxByOrNull { it.annotationsBuild }
  }

  private suspend fun tryDownload(
    groupId: String,
    artifactId: String,
    version: String,
    repos: List<RemoteRepositoryDescription>
  ): IntelliJSdkExternalAnnotations? {
    val annotations = tryDownloadAnnotationsArtifact(groupId, artifactId, version, repos)
    if (annotations != null) {
      val buildNumber = getAnnotationsBuildNumber(annotations)
      if (buildNumber != null) {
        return IntelliJSdkExternalAnnotations(buildNumber, annotations)
      }
    }
    return null
  }

  private suspend fun tryDownloadAnnotationsArtifact(
    groupId: String,
    artifactId: String,
    version: String,
    repos: List<RemoteRepositoryDescription>
  ): VirtualFile? = indeterminateStep {
    coroutineToIndicator {
      JarRepositoryManager.loadDependenciesSync(
        project,
        JpsMavenRepositoryLibraryDescriptor(groupId, artifactId, version),
        setOf(ArtifactKind.ANNOTATIONS),
        repos,
        null
      )?.firstOrNull()?.file
    }
  }
}