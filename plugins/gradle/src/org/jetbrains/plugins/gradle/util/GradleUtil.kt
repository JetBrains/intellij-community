// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel.Companion.getInstance
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.workspaceModel.ide.toPath
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.model.projectModel.gradleBuilds
import org.jetbrains.plugins.gradle.model.projectModel.gradleInfo
import java.nio.file.Path

fun getGradleVersion(project: Project, file: VirtualFile): GradleVersion? {
  val module = ModuleUtilCore.findModuleForFile(file, project)
  val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return null
  val entityStorage = getInstance(project).currentSnapshot
  val entity = entityStorage.resolve(ExternalProjectEntityId(rootProjectPath)) ?: return null
  val version = entity.gradleInfo.gradleVersion
  return GradleVersion.version(version)
}

/**
 * Finds the root path of the (included) project based on the module.
 */
fun Module.getIncludedProjectRootPath(): Path? {
  val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(this) ?: return null
  val entityStorage = getInstance(project).currentSnapshot
  val entity = entityStorage.resolve(ExternalProjectEntityId(rootProjectPath)) ?: return null
  val projectPath = ExternalSystemApiUtil.getExternalProjectPath(this)?.toNioPathOrNull() ?: return null
  return entity.gradleBuilds.find { includedBuild ->
    includedBuild.projects.any { project -> project.url.toPath() == projectPath }
  }?.url?.toPath()
}