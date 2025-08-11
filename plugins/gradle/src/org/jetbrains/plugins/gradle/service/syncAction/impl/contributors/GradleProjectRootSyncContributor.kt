// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.contributors

import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource
import org.jetbrains.plugins.gradle.service.syncAction.virtualFileUrl
import java.nio.file.Path
import kotlin.io.path.name

internal class GradleProjectRootSyncContributor : GradleSyncContributor {

  override val phase: GradleSyncPhase = GradleSyncPhase.INITIAL_PHASE

  override suspend fun updateProjectModel(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val entitySource = GradleProjectRootEntitySource(context.projectPath, phase)
    val projectRootData = GradleProjectRootData(Path.of(context.projectPath), entitySource)
    storage addEntity createModuleEntity(context, projectRootData)
  }

  private fun createModuleEntity(
    context: ProjectResolverContext,
    projectRootData: GradleProjectRootData,
  ): ModuleEntity.Builder {
    return ModuleEntity(
      name = projectRootData.projectRoot.name,
      entitySource = projectRootData.entitySource,
      dependencies = listOf(
        InheritedSdkDependency,
        ModuleSourceDependency
      )
    ) {
      contentRoots = listOf(
        ContentRootEntity(
          url = context.virtualFileUrl(projectRootData.projectRoot),
          entitySource = entitySource,
          excludedPatterns = emptyList()
        )
      )
    }
  }

  private class GradleProjectRootData(
    val projectRoot: Path,
    val entitySource: EntitySource,
  )

  private data class GradleProjectRootEntitySource(
    override val projectPath: String,
    override val phase: GradleSyncPhase,
  ) : GradleBridgeEntitySource
}