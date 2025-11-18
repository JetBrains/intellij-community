// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.io.File
import java.nio.file.Path

val ProjectResolverContext.virtualFileUrlManager: VirtualFileUrlManager
  get() = project.workspaceModel.getVirtualFileUrlManager()

fun ProjectResolverContext.virtualFileUrl(path: Path): VirtualFileUrl {
  return path.toVirtualFileUrl(virtualFileUrlManager)
}

fun ProjectResolverContext.virtualFileUrl(file: File): VirtualFileUrl {
  return virtualFileUrl(file.toPath())
}

fun ProjectResolverContext.virtualFileUrl(path: String): VirtualFileUrl {
  return virtualFileUrl(Path.of(path))
}

internal val ProjectResolverContext.externalProjectEntityId: ExternalProjectEntityId
  get() = ExternalProjectEntityId(externalProjectPath)

internal fun GradleLightBuild.buildUrl(context: ProjectResolverContext): VirtualFileUrl {
  val buildRootPath = buildIdentifier.rootDir.toPath()
  return buildRootPath.toVirtualFileUrl(context.virtualFileUrlManager)
}

internal fun GradleLightBuild.buildEntityId(context: ProjectResolverContext): GradleBuildEntityId {
  return GradleBuildEntityId(context.externalProjectEntityId, buildUrl(context))
}

internal fun GradleLightProject.projectUrl(context: ProjectResolverContext): VirtualFileUrl {
  val projectRootPath = projectDirectory.toPath()
  return projectRootPath.toVirtualFileUrl(context.virtualFileUrlManager)
}

internal fun GradleLightProject.projectEntityId(context: ProjectResolverContext): GradleProjectEntityId {
  return GradleProjectEntityId(build.buildEntityId(context), identityPath)
}