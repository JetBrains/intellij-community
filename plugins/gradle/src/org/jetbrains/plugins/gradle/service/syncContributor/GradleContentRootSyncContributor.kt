// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.service.project.nameGenerator.ModuleNameGenerator
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getModuleId
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
    phase: GradleModelFetchPhase
  ) {
    if (context.isPhasedSyncEnabled) {
      if (phase == GradleModelFetchPhase.PROJECT_LOADED_PHASE) {
        configureProjectContentRoots(context, storage)
      }
      if (phase == GradleModelFetchPhase.PROJECT_MODEL_PHASE) {
        configureExModuleOptions(context, storage)
      }
    }
  }

  private suspend fun configureProjectContentRoots(
    context: ProjectResolverContext,
    storage: MutableEntityStorage
  ) {
    val project = context.project
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val contentRootsToAdd = LinkedHashMap<GradleProjectEntitySource, GradleContentRootData>()

    val contentRootEntities = storage.entities<ContentRootEntity>()

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

        val contentRootData = GradleContentRootData(buildModel, projectModel, projectEntitySource)

        if (contentRootEntities.any { isConflictedContentRootEntity(it, contentRootData) }) {
          continue
        }
        if (isUnloadedModule(context, project, contentRootData)) {
          continue
        }

        contentRootsToAdd[projectEntitySource] = contentRootData
      }
    }

    for (contentRootData in contentRootsToAdd.values) {

      checkCanceled()

      configureContentRoot(context, storage, contentRootData)
    }
  }

  private fun isConflictedContentRootEntity(
    contentRootEntity: ContentRootEntity,
    contentRootData: GradleContentRootData,
  ): Boolean {
    val entitySource = contentRootData.entitySource
    return contentRootEntity.entitySource == entitySource ||
           contentRootEntity.url == entitySource.projectRootUrl
  }

  private fun isUnloadedModule(
    context: ProjectResolverContext,
    project: Project,
    contentRootData: GradleContentRootData,
  ): Boolean {
    val unloadedModulesListStorage = UnloadedModulesListStorage.getInstance(project)
    val unloadedModuleNameHolder = unloadedModulesListStorage.unloadedModuleNameHolder
    for (moduleName in generateModuleNames(context, contentRootData)) {
      if (unloadedModuleNameHolder.isUnloaded(moduleName)) {
        return true
      }
    }
    return false
  }

  private fun configureContentRoot(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
    contentRootData: GradleContentRootData,
  ) {
    val moduleEntity = addModuleEntity(context, storage, contentRootData)
    addContentRootEntity(storage, contentRootData, moduleEntity)
  }

  private fun addModuleEntity(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
    contentRootData: GradleContentRootData,
  ): ModuleEntity.Builder {
    val entitySource = contentRootData.entitySource
    val moduleName = resolveUniqueModuleName(context, storage, contentRootData)
    val moduleEntity = ModuleEntity(
      name = moduleName,
      entitySource = entitySource,
      dependencies = listOf(
        InheritedSdkDependency,
        ModuleSourceDependency
      )
    )
    storage addEntity moduleEntity
    return moduleEntity
  }

  private fun addContentRootEntity(
    storage: MutableEntityStorage,
    contentRootData: GradleContentRootData,
    moduleEntity: ModuleEntity.Builder,
  ) {
    val entitySource = contentRootData.entitySource
    storage addEntity ContentRootEntity(
      url = entitySource.projectRootUrl,
      entitySource = entitySource,
      excludedPatterns = emptyList()
    ) {
      module = moduleEntity
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
    val entitySource: GradleProjectEntitySource,
  )

  private suspend fun configureExModuleOptions(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
  ) {
    val project = context.project
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val exModuleOptionsToAdd = LinkedHashMap<GradleProjectEntitySource, GradleExModuleOptionsData>()

    val moduleEntities = storage.entities<ModuleEntity>()

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

        val moduleEntity = moduleEntities.find { it.entitySource == projectEntitySource } ?: continue
        val externalProject = context.getProjectModel(projectModel, ExternalProject::class.java) ?: continue

        val exModuleOptionsData = GradleExModuleOptionsData(externalProject, projectEntitySource, moduleEntity)

        exModuleOptionsToAdd[projectEntitySource] = exModuleOptionsData
      }
    }

    for (exModuleOptionsData in exModuleOptionsToAdd.values) {

      checkCanceled()

      configureExModuleOptionsEntity(context, storage, exModuleOptionsData)
    }
  }

  private fun configureExModuleOptionsEntity(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
    exModuleOptionsData: GradleExModuleOptionsData,
  ) {
    val externalProject = exModuleOptionsData.externalProject
    val entitySource = exModuleOptionsData.entitySource
    val moduleEntity = exModuleOptionsData.moduleEntity
    storage.modifyModuleEntity(moduleEntity) {
      exModuleOptions = ExternalSystemModuleOptionsEntity(
        entitySource = entitySource
      ) {
        externalSystem = GradleConstants.SYSTEM_ID.id
        linkedProjectId = getModuleId(context, externalProject)
        linkedProjectPath = externalProject.projectDir.path
        rootProjectPath = context.projectPath

        externalSystemModuleGroup = externalProject.group
        externalSystemModuleVersion = externalProject.version
      }
    }
  }

  private class GradleExModuleOptionsData(
    val externalProject: ExternalProject,
    val entitySource: GradleProjectEntitySource,
    val moduleEntity: ModuleEntity,
  )
}