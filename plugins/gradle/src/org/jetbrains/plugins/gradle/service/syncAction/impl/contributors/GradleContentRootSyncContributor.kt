// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.contributors

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
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityBuilder
import org.jetbrains.plugins.gradle.model.projectModel.GradleModuleEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityBuilder
import org.jetbrains.plugins.gradle.model.projectModel.gradleBuilds
import org.jetbrains.plugins.gradle.model.projectModel.gradleModuleEntity
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.*
import org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource
import org.jetbrains.plugins.gradle.util.GradleConstants

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
        builder addEntity createModuleEntity(context, contentRootData)
      }
    }

    return builder.toSnapshot()
  }

  private fun createModuleEntity(
    context: ProjectResolverContext,
    contentRootData: GradleContentRootData,
  ): ModuleEntityBuilder {
    val projectModel = contentRootData.projectModel
    val externalProject = contentRootData.externalProject
    val entitySource = contentRootData.entitySource
    return ModuleEntity(
      name = GradleProjectResolverUtil.getHolderModuleName(context, projectModel, externalProject),
      entitySource = entitySource,
      dependencies = listOf(
        InheritedSdkDependency,
        ModuleSourceDependency
      )
    ).apply {
      exModuleOptions = createModuleOptionsEntity(context, contentRootData)
      contentRoots = listOf(
        ContentRootEntity(
          url = context.virtualFileUrl(projectModel.projectDirectory),
          entitySource = entitySource,
          excludedPatterns = emptyList()
        )
      )
      gradleModuleEntity = GradleModuleEntity(
        gradleProjectId = projectModel.projectEntityId(context),
        entitySource = entitySource
      )
    }
  }

  private fun createModuleOptionsEntity(
    context: ProjectResolverContext,
    sourceRootData: GradleContentRootData,
  ): ExternalSystemModuleOptionsEntityBuilder {
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
    storage addEntity ExternalProjectEntity(context.externalProjectPath, entitySource) {
      gradleBuilds = context.allBuilds.map { buildModel ->
        createGradleBuildEntity(buildModel, context, entitySource)
      }
    }
  }

  private fun createGradleBuildEntity(
    buildModel: GradleLightBuild,
    context: ProjectResolverContext,
    entitySource: GradleProjectModelEntitySource,
  ): GradleBuildEntityBuilder = GradleBuildEntity(
    externalProjectId = context.externalProjectEntityId,
    name = buildModel.name,
    url = buildModel.buildUrl(context),
    entitySource = entitySource
  ) {
    projects = buildModel.projects.map { projectModel ->
      createGradleProjectEntity(projectModel, buildModel, context, entitySource)
    }
  }

  private fun createGradleProjectEntity(
    projectModel: GradleLightProject,
    buildModel: GradleLightBuild,
    context: ProjectResolverContext,
    entitySource: GradleProjectModelEntitySource,
  ): GradleProjectEntityBuilder = GradleProjectEntity(
    buildId = buildModel.buildEntityId(context),
    name = projectModel.name,
    path = projectModel.path,
    identityPath = projectModel.identityPath,
    url = projectModel.projectUrl(context),
    linkedProjectId = GradleProjectResolverUtil.getModuleId(context, projectModel),
    entitySource = entitySource
  )

  private data class GradleProjectModelEntitySource(
    override val projectPath: String,
    override val phase: GradleSyncPhase,
  ) : GradleEntitySource
}