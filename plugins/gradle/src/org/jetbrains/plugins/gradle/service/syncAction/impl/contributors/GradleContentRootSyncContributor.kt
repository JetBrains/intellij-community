// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.contributors

import com.intellij.openapi.progress.checkCanceled
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource
import org.jetbrains.plugins.gradle.service.syncAction.virtualFileUrl
import org.jetbrains.plugins.gradle.util.GradleConstants

internal class GradleContentRootSyncContributor : GradleSyncContributor {

  override val name: String = "Gradle Content Root"

  override val phase: GradleSyncPhase = GradleSyncPhase.PROJECT_MODEL_PHASE

  override suspend fun createProjectModel(
    context: ProjectResolverContext,
    storage: ImmutableEntityStorage,
  ): ImmutableEntityStorage {
    val builder = storage.toBuilder()

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
}