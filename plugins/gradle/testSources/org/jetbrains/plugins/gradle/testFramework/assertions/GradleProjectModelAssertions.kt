// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.assertions

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

internal fun assertGradleBuildEntity(
  project: Project,
  buildId: GradleBuildEntityId,
  externalProjectId: ExternalProjectEntityId,
  buildUrl: VirtualFileUrl,
  projectIds: List<GradleProjectEntityId>,
  name: String? = buildUrl.fileName,
) {
  val buildEntity = project.workspaceModel.currentSnapshot.resolve(buildId)
  assertNotNull(buildEntity) {
    "GradleBuildEntity with symbolic ID = $buildId should be available in the storage."
  }
  assertEquals(buildUrl, buildEntity!!.url) {
    "GradleBuildEntity with symbolic ID = $buildId should have expected `url` of its build directory."
  }
  assertEquals(name, buildEntity.name) {
    "GradleBuildEntity with url = $buildUrl should have expected build `name`."
  }
  assertEquals(externalProjectId, buildEntity.externalProject.symbolicId) {
    "GradleBuildEntity with url = $buildUrl should be attached to an ExternalProjectEntity with proper symbolic ID"
  }
  assertEqualsUnordered(projectIds, buildEntity.projects.map { it.symbolicId }) {
    "GradleBuildEntity with url = $buildUrl should have projects with expected symbolic IDs."
  }
}

internal fun assertGradleProjectEntity(
  project: Project,
  projectId: GradleProjectEntityId,
  projectUrl: VirtualFileUrl,
  buildId: GradleBuildEntityId,
  path: String,
  linkedProjectId: String,
  identityPath: String,
  name: String? = projectUrl.fileName,
) {
  val projectEntity = project.workspaceModel.currentSnapshot.resolve(projectId)
  assertNotNull(projectEntity) {
    "GradleProjectEntity with symbolic ID = $projectId should be available in the storage."
  }
  assertEquals(projectUrl, projectEntity!!.url) {
    "GradleProjectEntity with symbolic ID = $projectId should have expected `url` of its project directory."
  }
  assertEquals(name, projectEntity.name) {
    "GradleProjectEntity with `url` = $projectUrl should have expected project `name`."
  }
  assertEquals(buildId, projectEntity.build.symbolicId) {
    "GradleProjectEntity with `url` = $projectUrl should be attached to a GradleBuildEntity with expected symbolic ID"
  }
  assertEquals(buildId, projectEntity.buildId) {
    "GradleProjectEntity with `url` = $projectUrl should have expected `buildId` of the build it belongs to."
  }
  assertEquals(linkedProjectId, projectEntity.linkedProjectId) {
    "GradleProjectEntity with `url` = $projectUrl should have expected `linkedProjectId`."
  }
  assertEquals(identityPath, projectEntity.identityPath) {
    "GradleProjectEntity with `url` = $projectUrl should have expected `identityPath`."
  }
  assertEquals(path, projectEntity.path) {
    "GradleProjectEntity with `url` = $projectUrl should have expected `path`."
  }
}
