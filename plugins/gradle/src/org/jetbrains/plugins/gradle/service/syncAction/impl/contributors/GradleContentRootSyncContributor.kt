// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.contributors

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleModuleEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.*
import org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource
import org.jetbrains.plugins.gradle.util.GradleConstants

private val LOG: Logger = Logger.getInstance(GradleContentRootSyncContributor::class.java)

internal class GradleContentRootSyncContributor : GradleSyncContributor {

  override val name: String = "Gradle Content Root"

  override val phase: GradleSyncPhase = GradleSyncPhase.PROJECT_MODEL_PHASE

  override suspend fun createProjectModel(
    context: ProjectResolverContext,
    storage: ImmutableEntityStorage,
  ): ImmutableEntityStorage {
    val builder = storage.toBuilder()

    addGradleProjectModelEntities(builder, context)

    val entitySource = GradleContentRootEntitySource(context.projectPath, phase)

    for (buildModel in context.allBuilds) {
      for (projectModel in buildModel.projects) {

        checkCanceled()

        val externalProject = context.getProjectModel(projectModel, ExternalProject::class.java) ?: continue
        val contentRootData = GradleContentRootData(projectModel, externalProject, entitySource)
        val moduleEntity = createModuleEntity(context, contentRootData)
        builder addEntity moduleEntity

        addGradleModuleEntity(builder, projectModel, context, moduleEntity)
      }
    }

    return builder.toSnapshot()
  }

  private fun createModuleEntity(
    context: ProjectResolverContext,
    contentRootData: GradleContentRootData,
  ): ModuleEntity.Builder {
    val projectModel = contentRootData.projectModel
    val externalProject = contentRootData.externalProject
    return ModuleEntity(
      name = GradleProjectResolverUtil.getHolderModuleName(context, projectModel, externalProject),
      entitySource = contentRootData.entitySource,
      dependencies = listOf(
        InheritedSdkDependency,
        ModuleSourceDependency
      )
    ).apply {
      exModuleOptions = createModuleOptionsEntity(context, contentRootData)
      contentRoots = listOf(
        ContentRootEntity(
          url = context.virtualFileUrl(projectModel.projectDirectory),
          entitySource = contentRootData.entitySource,
          excludedPatterns = emptyList()
        )
      )
    }
  }

  private fun createModuleOptionsEntity(
    context: ProjectResolverContext,
    sourceRootData: GradleContentRootData,
  ): ExternalSystemModuleOptionsEntity.Builder {
    val externalProject = sourceRootData.externalProject
    return ExternalSystemModuleOptionsEntity(
      entitySource = sourceRootData.entitySource
    ) {
      externalSystem = GradleConstants.SYSTEM_ID.id
      linkedProjectId = GradleProjectResolverUtil.getModuleId(context, externalProject)
      linkedProjectPath = externalProject.projectDir.path
      rootProjectPath = context.projectPath

      externalSystemModuleGroup = externalProject.group
      externalSystemModuleVersion = externalProject.version
      externalSystemModuleType = null
    }
  }

  private class GradleContentRootData(
    val projectModel: GradleLightProject,
    val externalProject: ExternalProject,
    val entitySource: EntitySource,
  )

  private data class GradleContentRootEntitySource(
    override val projectPath: String,
    override val phase: GradleSyncPhase,
  ) : GradleBridgeEntitySource

  private fun addGradleProjectModelEntities(
    storage: MutableEntityStorage,
    context: ProjectResolverContext,
  ) {
    val entitySource = GradleProjectModelEntitySource(context.projectPath, phase)
    val externalProjectBuilder = ExternalProjectEntity(context.externalProjectPath, entitySource)
    storage addEntity externalProjectBuilder

    for (buildModel in context.allBuilds) {
      val buildEntityBuilder = createGradleBuildEntity(buildModel, externalProjectBuilder, context, entitySource)
      storage addEntity buildEntityBuilder

      for (projectModel in buildModel.projects) {
        val projectEntityBuilder = createGradleProjectEntity(projectModel, buildModel, context, entitySource)
        storage addEntity projectEntityBuilder
      }
    }
  }

  private fun createGradleBuildEntity(
    buildModel: GradleLightBuild,
    externalProjectBuilder: ExternalProjectEntity.Builder,
    context: ProjectResolverContext,
    entitySource: GradleProjectModelEntitySource,
  ): GradleBuildEntity.Builder = GradleBuildEntity(
    externalProjectId = context.externalProjectEntityId,
    name = buildModel.name,
    url = buildModel.buildUrl(context),
    entitySource = entitySource
  ) {
    externalProject = externalProjectBuilder
  }

  private fun createGradleProjectEntity(
    projectModel: GradleLightProject,
    buildModel: GradleLightBuild,
    context: ProjectResolverContext,
    entitySource: GradleProjectModelEntitySource,
  ): GradleProjectEntity.Builder = GradleProjectEntity(
    buildId = buildModel.buildEntityId(context),
    name = projectModel.name,
    path = projectModel.path,
    identityPath = projectModel.identityPath,
    url = projectModel.projectUrl(context),
    linkedProjectId = GradleProjectResolverUtil.getModuleId(context, projectModel),
    entitySource = entitySource
  )

  private fun addGradleModuleEntity(
    storage: MutableEntityStorage,
    projectModel: GradleLightProject,
    context: ProjectResolverContext,
    moduleEntity: ModuleEntity.Builder,
  ) {
    val projectEntity = storage.resolve(projectModel.projectEntityId(context)) ?: run {
      LOG.warn("GradleProjectEntity is not found: it should be already created for the project ${projectModel.projectDirectory}")
      return
    }
    storage addEntity GradleModuleEntity(projectEntity.symbolicId, projectEntity.entitySource) {
      module = moduleEntity
    }
  }

  private data class GradleProjectModelEntitySource(
    override val projectPath: String,
    override val phase: GradleSyncPhase,
  ) : GradleEntitySource
}