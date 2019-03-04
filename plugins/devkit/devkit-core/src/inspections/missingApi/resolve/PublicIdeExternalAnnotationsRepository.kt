// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi.resolve

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor

/**
 * Default implementation of [IdeExternalAnnotationsRepository] that delegates to [JarRepositoryManager]
 * for searching and downloading artifacts from the IntelliJ Artifacts Repositories.
 */
class PublicIdeExternalAnnotationsRepository(private val project: Project) : IdeExternalAnnotationsRepository {

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

    private val allProductCodes = listOf("IU", "IC", "RM", "PY", "PC", "PE", "PS", "WS", "OC", "CL", "DB", "RD", "GO", "MPS", "AI")

    private val ideaIUAnnotationsCoordinates = "com.jetbrains.intellij.idea" to "ideaIU"

    //Currently, for any IDE download ideaIU's annotations.
    private val productCodeToAnnotationsCoordinates = allProductCodes.associate {
      it to ideaIUAnnotationsCoordinates
    }

    fun hasAnnotationsForProduct(productCode: String): Boolean =
      productCode in productCodeToAnnotationsCoordinates
  }

  override fun downloadExternalAnnotations(ideBuildNumber: BuildNumber): IdeExternalAnnotations? {
    val productCode = ideBuildNumber.productCode.takeIf { it.isNotEmpty() } ?: "IU"
    val (groupId, artifactId) = productCodeToAnnotationsCoordinates[productCode] ?: return null

    val lastReleaseVersion = "${ideBuildNumber.baselineVersion}.999999"
    val lastReleaseAnnotations = tryDownload(groupId, artifactId, lastReleaseVersion, listOf(RELEASES_REPO_DESCRIPTION))
    if (lastReleaseAnnotations != null && lastReleaseAnnotations.annotationsBuild >= ideBuildNumber) {
      return lastReleaseAnnotations
    }

    val snapshotVersion = "${ideBuildNumber.baselineVersion}-SNAPSHOT"
    val snapshotAnnotations = tryDownload(groupId, artifactId, snapshotVersion, listOf(SNAPSHOTS_REPO_DESCRIPTION))
    if (snapshotAnnotations != null && snapshotAnnotations.annotationsBuild >= ideBuildNumber) {
      return snapshotAnnotations
    }

    val latestTrunkSnapshot = "LATEST-TRUNK-SNAPSHOT"
    val latestTrunkSnapshotAnnotations = tryDownload(groupId, artifactId, latestTrunkSnapshot, listOf(SNAPSHOTS_REPO_DESCRIPTION))
    if (latestTrunkSnapshotAnnotations != null && latestTrunkSnapshotAnnotations.annotationsBuild >= ideBuildNumber) {
      return latestTrunkSnapshotAnnotations
    }

    return sequenceOf(lastReleaseAnnotations, snapshotAnnotations, latestTrunkSnapshotAnnotations).filterNotNull().maxBy { it.annotationsBuild }
  }

  private fun tryDownload(
    groupId: String,
    artifactId: String,
    version: String,
    repos: List<RemoteRepositoryDescription>
  ): IdeExternalAnnotations? {
    val annotations = tryDownloadAnnotationsArtifact(groupId, artifactId, version, repos)
    if (annotations != null) {
      val buildNumber = getAnnotationsBuildNumber(annotations)
      if (buildNumber != null) {
        return IdeExternalAnnotations(buildNumber, annotations)
      }
    }
    return null
  }

  private fun tryDownloadAnnotationsArtifact(
    groupId: String,
    artifactId: String,
    version: String,
    repos: List<RemoteRepositoryDescription>
  ): VirtualFile? {
    return JarRepositoryManager.loadDependenciesSync(
      project,
      JpsMavenRepositoryLibraryDescriptor(groupId, artifactId, version),
      setOf(ArtifactKind.ANNOTATIONS),
      repos,
      null
    )?.firstOrNull()?.file
  }

}