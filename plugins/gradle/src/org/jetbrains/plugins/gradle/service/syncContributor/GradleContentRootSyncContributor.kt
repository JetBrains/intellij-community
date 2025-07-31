// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.externalSystem.service.project.nameGenerator.ModuleNameGenerator
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.progress.checkCanceled
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleBuildEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleLinkedProjectEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleProjectEntitySource
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

@Order(GradleSyncContributor.Order.CONTENT_ROOT_CONTRIBUTOR)
internal class GradleContentRootSyncContributor : GradleSyncContributor {

  override val name: String = "Gradle Content Root"

  override suspend fun onModelFetchPhaseCompleted(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
    phase: GradleModelFetchPhase,
  ) {
    if (context.isPhasedSyncEnabled) {
      if (phase == GradleModelFetchPhase.PROJECT_MODEL_PHASE) {
        configureProjectContentRoots(context, storage)
      }
    }
  }

  private suspend fun configureProjectContentRoots(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
  ) {
    val project = context.project
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val contentRoots = storage.entities<ContentRootEntity>()
      .mapTo(LinkedHashSet()) { it.url }

    val linkedProjectRootPath = Path.of(context.projectPath)
    val linkedProjectRootUrl = linkedProjectRootPath.toVirtualFileUrl(virtualFileUrlManager)
    val linkedProjectEntitySource = GradleLinkedProjectEntitySource(linkedProjectRootUrl)

    for (buildModel in context.allBuilds) {

      val buildRootPath = buildModel.buildIdentifier.rootDir.toPath()
      val buildRootUrl = buildRootPath.toVirtualFileUrl(virtualFileUrlManager)
      val buildEntitySource = GradleBuildEntitySource(linkedProjectEntitySource, buildRootUrl)

      for (projectModel in buildModel.projects) {

        checkCanceled()

        val projectRootPath = projectModel.projectDirectory.toPath()
        val projectRootUrl = projectRootPath.toVirtualFileUrl(virtualFileUrlManager)
        val projectEntitySource = GradleProjectEntitySource(buildEntitySource, projectRootUrl)

        val externalProject = context.getProjectModel(projectModel, ExternalProject::class.java) ?: continue

        val contentRootData = GradleContentRootData(buildModel, projectModel, externalProject, projectEntitySource)

        if (isUnloadedModule(context, contentRootData)) {
          continue
        }

        val moduleEntity = createModuleEntity(context, storage, contentRootData)
        val moduleContentRoots = moduleEntity.contentRoots.map { it.url }

        if (moduleContentRoots.none { it in contentRoots }) {
          contentRoots.addAll(moduleContentRoots)

          storage addEntity moduleEntity
        }
      }
    }
  }

  private suspend fun isUnloadedModule(
    context: ProjectResolverContext,
    contentRootData: GradleContentRootData,
  ): Boolean {
    val unloadedModulesListStorage = context.project.serviceAsync<UnloadedModulesListStorage>()
    val unloadedModuleNameHolder = unloadedModulesListStorage.unloadedModuleNameHolder
    for (moduleName in generateModuleNames(context, contentRootData)) {
      if (unloadedModuleNameHolder.isUnloaded(moduleName)) {
        return true
      }
    }
    return false
  }

  private fun createModuleEntity(
    context: ProjectResolverContext,
    storage: EntityStorage,
    contentRootData: GradleContentRootData,
  ): ModuleEntity.Builder {
    val virtualFileUrlManager = context.project.workspaceModel.getVirtualFileUrlManager()

    val contentRootPath = contentRootData.projectModel.projectDirectory.toPath()
    return ModuleEntity(
      name = resolveUniqueModuleName(context, storage, contentRootData),
      entitySource = contentRootData.entitySource,
      dependencies = listOf(
        InheritedSdkDependency,
        ModuleSourceDependency
      )
    ) {
      exModuleOptions = createModuleOptionsEntity(context, contentRootData)
      contentRoots = listOf(
        ContentRootEntity(
          url = contentRootPath.toVirtualFileUrl(virtualFileUrlManager),
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

  private fun resolveUniqueModuleName(
    context: ProjectResolverContext,
    storage: EntityStorage,
    contentRootData: GradleContentRootData,
  ): String {
    for (moduleNameCandidate in generateModuleNames(context, contentRootData)) {
      val moduleId = ModuleId(moduleNameCandidate)
      if (storage.resolve(moduleId) == null) {
        return moduleNameCandidate
      }
    }
    throw IllegalStateException("Too many duplicated module names")
  }

  private fun generateModuleNames(
    context: ProjectResolverContext,
    contentRootData: GradleContentRootData,
  ): Iterable<String> {
    val buildModel = contentRootData.buildModel
    val projectModel = contentRootData.projectModel
    val moduleName = resolveModuleName(context, buildModel, projectModel)
    val modulePath = projectModel.projectDirectory.toPath()
    return ModuleNameGenerator.generate(null, moduleName, modulePath, ".")
  }

  private fun resolveModuleName(
    context: ProjectResolverContext,
    buildModel: GradleLightBuild,
    projectModel: GradleLightProject,
  ): String {
    val moduleName = resolveGradleProjectQualifiedName(buildModel, projectModel)
    val buildSrcGroup = context.getBuildSrcGroup(buildModel.name, buildModel.buildIdentifier)
    if (buildSrcGroup.isNullOrBlank()) {
      return moduleName
    }
    return "$buildSrcGroup.$moduleName"
  }

  private fun resolveGradleProjectQualifiedName(
    buildModel: GradleLightBuild,
    projectModel: GradleLightProject,
  ): String {
    if (projectModel.path == ":") {
      return buildModel.name
    }
    if (projectModel.path.startsWith(":")) {
      return buildModel.name + projectModel.path.replace(":", ".")
    }
    return projectModel.path.replace(":", ".")
  }

  private class GradleContentRootData(
    val buildModel: GradleLightBuild,
    val projectModel: GradleLightProject,
    val externalProject: ExternalProject,
    val entitySource: GradleProjectEntitySource,
  )
}