// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.assertions

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId
import org.jetbrains.plugins.gradle.model.projectModel.GradleModuleEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId
import org.jetbrains.plugins.gradle.model.projectModel.gradleModuleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

internal fun assertGradleBuildEntity(
  project: Project,
  buildUrl: VirtualFileUrl,
  externalProjectId: ExternalProjectEntityId,
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
}

internal fun assertGradleProjectEntity(
  project: Project,
  projectUrl: VirtualFileUrl,
  buildId: GradleBuildEntityId,
  path: String,
  linkedProjectId: String,
  identityPath: String,
  moduleName: String?,
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

  if (moduleName == null) {
    assertNull(projectEntity.gradleModuleEntity) {
      "It's not expected for GradleProjectEntity with `url` = $projectUrl to have a GradleModuleEntity."
    }
  }
  else {
    assertNotNull(projectEntity.gradleModuleEntity) {
      "GradleProjectEntity with `url` = $projectUrl should have GradleModuleEntity, to connect it with ModuleEntity for `$moduleName` module."
    }
    assertEquals(ModuleId(moduleName), projectEntity.gradleModuleEntity!!.module.symbolicId) {
      "GradleProjectEntity with `url` = $projectUrl should be connected with `$moduleName` module via GradleModuleEntity."
    }
  }
}

internal fun assertGradleModuleEntities(
  project: Project,
  vararg expectedModuleNames: String,
  messageSupplier: (() -> String)? = null,
) {
  val storage = project.workspaceModel.currentSnapshot
  val entities = storage.entities(GradleModuleEntity::class.java)
  val actualModuleNames = entities.map { it.module.name }.toList()

  val message = messageSupplier ?: {
    "For each module name from the expected list, there should be a GradleModuleEntity connected with ModuleEntity with the same name."
  }
  CollectionAssertions.assertEqualsUnordered(expectedModuleNames.toList(), actualModuleNames, message)
}
