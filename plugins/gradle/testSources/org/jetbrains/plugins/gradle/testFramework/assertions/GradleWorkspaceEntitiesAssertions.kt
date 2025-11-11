// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.assertions

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.ide.toPath
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId
import org.jetbrains.plugins.gradle.model.projectModel.GradleModuleEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId
import org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.nio.file.Path

internal fun assertGradleBuildEntity(
  project: Project,
  buildUrl: VirtualFileUrl,
  externalProjectId: ExternalProjectEntityId,
  projectIds: List<GradleProjectEntityId>,
  name: String? = buildUrl.fileName,
) {
  val buildId = GradleBuildEntityId(externalProjectId, buildUrl)
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
  projectUrl: VirtualFileUrl,
  buildId: GradleBuildEntityId,
  path: String,
  linkedProjectId: String,
  identityPath: String,
  projectName: String = projectUrl.fileName,
) {
  val projectId = GradleProjectEntityId(buildId, projectUrl)
  val projectEntity = project.workspaceModel.currentSnapshot.resolve(projectId)
  assertNotNull(projectEntity) {
    "GradleProjectEntity with symbolic ID = $projectId should be available in the storage."
  }
  assertEquals(projectUrl, projectEntity!!.url) {
    "GradleProjectEntity with symbolic ID = $projectId should have expected `url` of its project directory."
  }
  assertEquals(projectName, projectEntity.name) {
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

internal fun assertGradleModuleEntities(
  project: Project,
  vararg moduleNameToProjectIdPairs: Pair<String, GradleProjectEntityId>,
) {
  val storage = project.workspaceModel.currentSnapshot
  val gradleModuleEntities = storage.entities(GradleModuleEntity::class.java)

  val expectedModuleNames = moduleNameToProjectIdPairs.map { it.first }
  val actualModuleNames = gradleModuleEntities.toList().map { it.module.name }
  CollectionAssertions.assertEqualsUnordered(expectedModuleNames, actualModuleNames) {
    "The each expected module name there should exist a GradleModuleEntity, connected with the corresponding ModuleEntity."
  }

  moduleNameToProjectIdPairs.forEach { (moduleName, projectId) ->
    val gradleModuleEntity = gradleModuleEntities.find { it.module.name == moduleName }
    assertNotNull(gradleModuleEntity) {
      "GradleModuleEntity should exist for the module with name = `$moduleName`."
    }
    assertEquals(projectId, gradleModuleEntity!!.gradleProjectId) {
      "GradleModuleEntity for the module with name = `$moduleName` should contain an expected symbolic ID of GradleProjectEntity."
    }
  }
}

internal fun assertVersionCatalogEntities(
  project: Project,
  buildUrl: VirtualFileUrl,
  vararg namePaths: Pair<String, Path>,
  messageSupplier: (() -> String)? = null,
) {
  val storage = project.workspaceModel.currentSnapshot
  val actualNamePaths: List<Pair<String, Path>> = storage.entities(GradleVersionCatalogEntity::class.java)
    .filter { it.build.url == buildUrl }
    .map { it.name to it.url.toPath() }
    .toList()

  val expectedNamePaths = namePaths.map { (name, path) -> name to path.normalize() }
  val message = messageSupplier ?: {
    "For build with the url = $buildUrl, there should be added into the storage all GradleVersionCatalogEntity entities with " +
    "the expected names and paths."
  }
  CollectionAssertions.assertEqualsUnordered(expectedNamePaths, actualNamePaths, message)
}
