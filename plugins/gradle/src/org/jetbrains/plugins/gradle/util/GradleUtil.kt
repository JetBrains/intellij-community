// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.module.Module
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.intellij.workspaceModel.ide.toPath
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity
import org.jetbrains.plugins.gradle.model.projectModel.gradleInfo
import org.jetbrains.plugins.gradle.model.projectModel.gradleModuleEntity
import java.nio.file.Path

/**
 * @return the active Gradle version of the project related to the module, or null if unavailable
 */
fun Module.getGradleVersion(): GradleVersion? {
  val externalProject = getRelatedGradleBuild()?.externalProject ?: return null
  val version = externalProject.gradleInfo.gradleVersion
  return GradleVersion.version(version)
}

fun Module.getRelatedGradleBuildPath(): Path? = getRelatedGradleBuild()?.url?.toPath()

fun Module.getRelatedGradleBuild(): GradleBuildEntity? {
  val moduleEntity = findModuleEntity() ?: return null
  val gradleModuleEntity = moduleEntity.gradleModuleEntity ?: return null
  val storage = project.workspaceModel.currentSnapshot
  return storage.resolve(gradleModuleEntity.gradleProjectId.buildId)
}