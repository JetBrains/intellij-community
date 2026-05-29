// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.nio.file.Path
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

val ProjectResolverContext.virtualFileUrlManager: VirtualFileUrlManager
  get() = project.workspaceModel.getVirtualFileUrlManager()

fun ProjectResolverContext.virtualFileUrl(path: Path): VirtualFileUrl {
  return path.toVirtualFileUrl(virtualFileUrlManager)
}

@Suppress("IO_FILE_USAGE")
fun ProjectResolverContext.virtualFileUrl(file: java.io.File): VirtualFileUrl {
  return virtualFileUrl(file.toPath())
}

fun ProjectResolverContext.virtualFileUrl(path: String): VirtualFileUrl {
  return virtualFileUrl(Path.of(path))
}

internal val ProjectResolverContext.externalProjectEntityId: ExternalProjectEntityId
  get() = ExternalProjectEntityId(externalProjectPath)

internal fun GradleLightBuild.buildUrl(context: ProjectResolverContext): VirtualFileUrl {
  return context.virtualFileUrl(buildIdentifier.rootDir)
}

internal fun GradleLightBuild.buildEntityId(context: ProjectResolverContext): GradleBuildEntityId {
  return GradleBuildEntityId(context.externalProjectEntityId, buildUrl(context))
}

@Suppress("IO_FILE_USAGE")
internal fun GradleLightProject.projectUrl(context: ProjectResolverContext): VirtualFileUrl {
  return context.virtualFileUrl(projectDirectory)
}

internal fun GradleLightProject.projectEntityId(context: ProjectResolverContext): GradleProjectEntityId {
  return GradleProjectEntityId(build.buildEntityId(context), identityPath)
}

@Experimental
inline fun <reified E : GradleEntitySource> gradleEntitySource(
  context: ProjectResolverContext,
  crossinline filter: (E) -> Boolean = { true },
): (EntitySource) -> Boolean = {
  isGradleEntitySource<E>(context, it) && filter(it)
}

@Experimental
@OptIn(ExperimentalContracts::class)
inline fun <reified E : GradleEntitySource> isGradleEntitySource(
  context: ProjectResolverContext,
  entitySource: EntitySource,
): Boolean {
  contract {
    returns(true) implies (entitySource is E)
  }
  return entitySource is E && entitySource.projectPath == context.projectPath
}