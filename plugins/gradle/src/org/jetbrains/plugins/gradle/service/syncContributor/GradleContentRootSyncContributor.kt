// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.service.project.nameGenerator.ModuleNameGenerator
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncProjectConfigurator.project

@ApiStatus.Internal
@Order(GradleSyncContributor.Order.CONTENT_ROOT_CONTRIBUTOR)
class GradleContentRootSyncContributor : GradleSyncContributor {

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
    }
  }

  private suspend fun configureProjectContentRoots(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val project = context.project()
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val contentRootEntities = storage.entities<ContentRootEntity>()

    for (buildModel in context.allBuilds) {
      val buildPath = buildModel.buildIdentifier.rootDir.toPath()
      val buildUrl = buildPath.toVirtualFileUrl(virtualFileUrlManager)

      val buildEntitySource = GradleBuildEntitySource(buildUrl)

      val contentRootEntitiesToAdd = LinkedHashMap<VirtualFileUrl, GradleLightProject>()

      for (projectModel in buildModel.projects) {
        val contentRootPath = projectModel.projectDirectory.toPath()
        val contentRootUrl = contentRootPath.toVirtualFileUrl(virtualFileUrlManager)
        val contentRootEntity = contentRootEntities.find { it.url == contentRootUrl }
        if (contentRootEntity == null) {
          contentRootEntitiesToAdd[contentRootUrl] = projectModel
        }
      }

      for (projectModel in contentRootEntitiesToAdd.values) {
        addContentRootEntity(context, project, storage, buildModel, projectModel, buildEntitySource)
      }
    }
  }

  private fun addContentRootEntity(
    context: ProjectResolverContext,
    project: Project,
    storage: MutableEntityStorage,
    buildModel: GradleLightBuild,
    projectModel: GradleLightProject,
    entitySource: GradleEntitySource,
  ) {
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val contentRootPath = projectModel.projectDirectory.toPath()
    val contentRootUrl = contentRootPath.toVirtualFileUrl(virtualFileUrlManager)
    val moduleName = resolveUniqueModuleName(context, storage, buildModel, projectModel)
    storage addEntity ContentRootEntity(
      url = contentRootUrl,
      entitySource = entitySource,
      excludedPatterns = emptyList()
    ) {
      module = ModuleEntity(
        name = moduleName,
        entitySource = entitySource,
        dependencies = emptyList()
      )
    }
  }

  private fun resolveUniqueModuleName(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
    buildModel: GradleLightBuild,
    projectModel: GradleLightProject,
  ): String {
    for (moduleNameCandidate in generateModuleNames(context, buildModel, projectModel)) {
      val moduleId = ModuleId(moduleNameCandidate)
      if (storage.resolve(moduleId) == null) {
        return moduleNameCandidate
      }
    }
    throw IllegalStateException("Too many duplicated module names")
  }

  private fun generateModuleNames(
    context: ProjectResolverContext,
    buildModel: GradleLightBuild,
    projectModel: GradleLightProject,
  ): Iterable<String> {
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
}