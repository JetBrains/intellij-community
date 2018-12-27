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

    val PRODUCT_CODE_GROUP_ID_AND_ARTIFACT_ID = listOf(
      Triple("IU", "com.jetbrains.intellij.idea", "ideaIU")
    )

    fun getGroupId(productCode: String): String? =
      PRODUCT_CODE_GROUP_ID_AND_ARTIFACT_ID.find { it.first == productCode }?.second

    fun getArtifactId(productCode: String): String? =
      PRODUCT_CODE_GROUP_ID_AND_ARTIFACT_ID.find { it.first == productCode }?.third
  }

  override fun downloadExternalAnnotations(ideBuildNumber: BuildNumber): VirtualFile? {
    val productCode = ideBuildNumber.productCode.takeIf { it.isNotEmpty() } ?: "IU"
    val groupId = getGroupId(productCode) ?: return null
    val artifactId = getArtifactId(productCode) ?: return null

    if (ideBuildNumber.isSnapshot) {
      val snapshotVersion = "${ideBuildNumber.baselineVersion}.SNAPSHOT"
      val snapshotAnnotations = tryDownload(groupId, artifactId, snapshotVersion, listOf(SNAPSHOTS_REPO_DESCRIPTION))
      if (snapshotAnnotations != null) {
        return snapshotAnnotations
      }
    }

    val lastReleaseAnnotationsVersion = "${ideBuildNumber.baselineVersion}.999999"
    return tryDownload(groupId, artifactId, lastReleaseAnnotationsVersion, listOf(RELEASES_REPO_DESCRIPTION))
  }

  private fun tryDownload(
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