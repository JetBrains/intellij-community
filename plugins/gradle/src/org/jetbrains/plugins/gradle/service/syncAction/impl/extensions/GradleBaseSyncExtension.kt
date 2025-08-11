// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.extensions

import com.intellij.openapi.externalSystem.util.Order
import com.intellij.platform.workspace.storage.*
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource

@Order(GradleBaseSyncExtension.ORDER)
internal class GradleBaseSyncExtension : GradleSyncExtension {

  override fun updateProjectStorage(
    context: ProjectResolverContext,
    syncStorage: ImmutableEntityStorage,
    projectStorage: MutableEntityStorage,
    phase: GradleSyncPhase,
  ) {
    projectStorage.replaceBySource({ shouldReplace(context, it, phase) }, syncStorage)
  }

  companion object {

    const val ORDER: Int = 0
  }
}

internal fun shouldReplace(
  context: ProjectResolverContext,
  entitySource: EntitySource,
  phase: GradleSyncPhase,
): Boolean {
  if (entitySource !is GradleEntitySource) return false
  if (entitySource.projectPath != context.projectPath) return false
  if (entitySource.phase > phase) return false
  if (entitySource is GradleBridgeEntitySource && !context.isPhasedSyncEnabled) return false
  return true
}

internal inline fun <reified T : WorkspaceEntity> EntityStorage.entitiesToReplace(
  context: ProjectResolverContext,
  phase: GradleSyncPhase,
): Sequence<T> {
  return entities<T>()
    .filter { shouldReplace(context, it.entitySource, phase) }
}

internal inline fun <reified T : WorkspaceEntity> EntityStorage.entitiesToSkip(
  context: ProjectResolverContext,
  phase: GradleSyncPhase,
): Sequence<T> {
  return entities<T>()
    .filter { !shouldReplace(context, it.entitySource, phase) }
}