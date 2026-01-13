// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel.Companion.getInstance
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.model.projectModel.gradleInfo

fun getGradleVersion(project: Project, file: VirtualFile): GradleVersion? {
  val module = ModuleUtilCore.findModuleForFile(file, project)
  val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return null
  val entityStorage = getInstance(project).currentSnapshot
  val entity = entityStorage.resolve(ExternalProjectEntityId(rootProjectPath)) ?: return null
  val version = entity.gradleInfo.gradleVersion
  return GradleVersion.version(version)
}