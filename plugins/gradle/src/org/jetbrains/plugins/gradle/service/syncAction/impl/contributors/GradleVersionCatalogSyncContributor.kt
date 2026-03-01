// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.contributors

import com.intellij.openapi.progress.checkCanceled
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.VersionCatalogsModel
import org.jetbrains.plugins.gradle.model.projectModel.modifyGradleBuildEntity
import org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntity
import org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntityBuilder
import org.jetbrains.plugins.gradle.model.versionCatalogs.versionCatalogs
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.buildEntityId
import org.jetbrains.plugins.gradle.service.syncAction.virtualFileUrl
import java.nio.file.Path

internal class GradleVersionCatalogSyncContributor : GradleSyncContributor {

  override val phase: GradleSyncPhase = GradleSyncPhase.ADDITIONAL_MODEL_PHASE

  override suspend fun createProjectModel(
    context: ProjectResolverContext,
    storage: ImmutableEntityStorage,
  ): ImmutableEntityStorage {
    val builder = storage.toBuilder()
    val entitySource = GradleVersionCatalogEntitySource(context.projectPath, phase)

    for (buildModel in context.allBuilds) {
      checkCanceled()
      val buildEntity = builder.resolve(buildModel.buildEntityId(context)) ?: continue
      builder.modifyGradleBuildEntity(buildEntity) {
        versionCatalogs = createVersionCatalogEntities(context, buildModel, entitySource)
      }
    }
    return builder.toSnapshot()
  }

  private fun createVersionCatalogEntities(
    context: ProjectResolverContext,
    buildModel: GradleLightBuild,
    entitySource: GradleVersionCatalogEntitySource,
  ): List<GradleVersionCatalogEntityBuilder> {
    val versionCatalogModel = context.getBuildModel(buildModel, VersionCatalogsModel::class.java) ?: return emptyList()

    return versionCatalogModel.catalogsLocations.map { (catalogName, catalogPath) ->
      val catalogUrl = context.virtualFileUrl(Path.of(catalogPath))
      GradleVersionCatalogEntity(catalogName, catalogUrl, entitySource)
    }
  }

  private data class GradleVersionCatalogEntitySource(
    override val projectPath: String,
    override val phase: GradleSyncPhase,
  ) : GradleEntitySource
}