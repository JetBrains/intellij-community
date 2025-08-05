// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaResourceRoots
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType
import com.intellij.openapi.externalSystem.service.project.nameGenerator.NumericNameGenerator
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.util.io.PathPrefixTree
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.util.containers.prefixTree.map.toPrefixTreeMap
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel
import org.jetbrains.plugins.gradle.service.project.GradleContentRootIndex
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.virtualFileUrl
import org.jetbrains.plugins.gradle.service.syncContributor.bridge.GradleBridgeEntitySource
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import kotlin.io.path.exists

@ApiStatus.Internal
@Order(GradleSyncContributor.Order.SOURCE_ROOT_CONTRIBUTOR)
class GradleSourceRootSyncContributor : GradleSyncContributor {

  override val phase: GradleSyncPhase = GradleSyncPhase.SOURCE_SET_MODEL_PHASE

  override suspend fun configureProjectModel(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
  ) {
    if (!context.isPhasedSyncEnabled) return

    configureProjectSourceRoots(context, storage)
  }

  private suspend fun configureProjectSourceRoots(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
  ) {
    val projectModuleEntities = storage.entities<ModuleEntity>()
      .filter { it.contentRoots.size == 1 }
      .associateBy { it.contentRoots.single().url }

    val contentRoots = storage.entities<ContentRootEntity>()
      .mapTo(LinkedHashSet()) { it.url }

    val contentRootIndex = GradleContentRootIndex()

    for (buildModel in context.allBuilds) {
      for (projectModel in buildModel.projects) {
        val sourceSetModel = context.getProjectModel(projectModel, GradleSourceSetModel::class.java) ?: continue
        for (sourceSet in sourceSetModel.sourceSets.values) {
          contentRootIndex.addSourceRoots(sourceSet)
        }
      }
    }

    val entitySource = GradleSourceRootEntitySource(context.projectPath)

    for (buildModel in context.allBuilds) {
      for (projectModel in buildModel.projects) {

        val externalProject = context.getProjectModel(projectModel, ExternalProject::class.java) ?: continue
        val sourceSetModel = context.getProjectModel(projectModel, GradleSourceSetModel::class.java) ?: continue
        val projectRootUrl = context.virtualFileUrl(projectModel.projectDirectory)
        val projectModuleEntity = projectModuleEntities[projectRootUrl] ?: continue

        for (sourceSet in sourceSetModel.sourceSets.values) {

          checkCanceled()

          val contentRootPaths = contentRootIndex.resolveContentRoots(externalProject, sourceSet)
          val sourceRootData = GradleSourceRootData(externalProject, sourceSet, contentRootPaths, projectModuleEntity, entitySource)

          if (isUnloadedModule(context, sourceRootData)) {
            continue
          }

          val moduleEntity = createModuleEntity(context, storage, sourceRootData)
          val moduleContentRoots = moduleEntity.contentRoots.map { it.url }

          if (moduleContentRoots.none { it in contentRoots }) {
            contentRoots.addAll(moduleContentRoots)

            storage addEntity moduleEntity
          }
        }
      }
    }
  }

  private suspend fun isUnloadedModule(
    context: ProjectResolverContext,
    sourceRootData: GradleSourceRootData,
  ): Boolean {
    val unloadedModulesListStorage = context.project.serviceAsync<UnloadedModulesListStorage>()
    val unloadedModuleNameHolder = unloadedModulesListStorage.unloadedModuleNameHolder
    for (moduleName in generateModuleNames(sourceRootData)) {
      if (unloadedModuleNameHolder.isUnloaded(moduleName)) {
        return true
      }
    }
    return false
  }

  private fun createModuleEntity(
    context: ProjectResolverContext,
    storage: EntityStorage,
    sourceRootData: GradleSourceRootData,
  ): ModuleEntity.Builder {
    val (excluded, sources) = sourceRootData.externalSourceSet.sources.asSequence()
      .flatMap { (type, set) -> set.srcDirs.asSequence().map { it.toPath() to type } }
      .partition { it.second.isExcluded }
    val excludedIndex = excluded.toPrefixTreeMap(PathPrefixTree)
    val sourceIndex = sources.filter { it.first.exists() }.toPrefixTreeMap(PathPrefixTree)

    return ModuleEntity(
      name = resolveUniqueModuleName(storage, sourceRootData),
      entitySource = sourceRootData.entitySource,
      dependencies = listOf(
        InheritedSdkDependency,
        ModuleSourceDependency
      )
    ) {
      exModuleOptions = createModuleOptionsEntity(context, sourceRootData)
      contentRoots = sourceRootData.contentRoots.map { contentRootPath ->
        ContentRootEntity(
          url = context.virtualFileUrl(contentRootPath),
          entitySource = sourceRootData.entitySource,
          excludedPatterns = emptyList()
        ) {
          excludedUrls = excludedIndex.getDescendantKeys(contentRootPath)
            .map { createExcludedUrlEntity(context, sourceRootData, it) }
          sourceRoots = sourceIndex.getDescendantEntries(contentRootPath)
            .map { createSourceRootEntity(context, sourceRootData, it.key, it.value) }
        }
      }
    }
  }

  private fun createExcludedUrlEntity(
    context: ProjectResolverContext,
    sourceRootData: GradleSourceRootData,
    excludedPath: Path,
  ): ExcludeUrlEntity.Builder {
    return ExcludeUrlEntity(
      url = context.virtualFileUrl(excludedPath),
      entitySource = sourceRootData.entitySource
    )
  }

  private fun createSourceRootEntity(
    context: ProjectResolverContext,
    sourceRootData: GradleSourceRootData,
    sourceRootPath: Path,
    sourceRootType: IExternalSystemSourceType,
  ): SourceRootEntity.Builder {
    return SourceRootEntity(
      url = context.virtualFileUrl(sourceRootPath),
      rootTypeId = sourceRootType.toSourceRootTypeId(),
      entitySource = sourceRootData.entitySource
    ) {
      if (sourceRootType.isResource) {
        javaResourceRoots += JavaResourceRootPropertiesEntity(
          generated = sourceRootType.isGenerated,
          relativeOutputPath = "",
          entitySource = sourceRootData.entitySource
        )
      }
      else {
        javaSourceRoots += JavaSourceRootPropertiesEntity(
          generated = sourceRootType.isGenerated,
          packagePrefix = "",
          entitySource = sourceRootData.entitySource
        )
      }
    }
  }

  private fun createModuleOptionsEntity(
    context: ProjectResolverContext,
    sourceRootData: GradleSourceRootData,
  ): ExternalSystemModuleOptionsEntity.Builder {
    val externalProject = sourceRootData.externalProject
    val externalSourceSet = sourceRootData.externalSourceSet
    return ExternalSystemModuleOptionsEntity(
      entitySource = sourceRootData.entitySource
    ) {
      externalSystem = GradleConstants.SYSTEM_ID.id
      linkedProjectId = GradleProjectResolverUtil.getModuleId(context, externalProject, externalSourceSet)
      linkedProjectPath = externalProject.projectDir.path
      rootProjectPath = context.projectPath

      externalSystemModuleGroup = externalProject.group
      externalSystemModuleVersion = externalProject.version
      externalSystemModuleType = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
    }
  }

  private fun IExternalSystemSourceType.toSourceRootTypeId(): SourceRootTypeId {
    return when (ExternalSystemSourceType.from(this)) {
      ExternalSystemSourceType.SOURCE -> JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
      ExternalSystemSourceType.SOURCE_GENERATED -> JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
      ExternalSystemSourceType.TEST -> JAVA_TEST_ROOT_ENTITY_TYPE_ID
      ExternalSystemSourceType.TEST_GENERATED -> JAVA_TEST_ROOT_ENTITY_TYPE_ID
      ExternalSystemSourceType.RESOURCE -> JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
      ExternalSystemSourceType.RESOURCE_GENERATED -> JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
      ExternalSystemSourceType.TEST_RESOURCE -> JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
      ExternalSystemSourceType.TEST_RESOURCE_GENERATED -> JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
      else -> throw NoWhenBranchMatchedException("Unexpected source type: ${this}")
    }
  }

  private fun resolveUniqueModuleName(
    storage: EntityStorage,
    sourceRootData: GradleSourceRootData,
  ): String {
    for (moduleNameCandidate in generateModuleNames(sourceRootData)) {
      val moduleId = ModuleId(moduleNameCandidate)
      if (storage.resolve(moduleId) == null) {
        return moduleNameCandidate
      }
    }
    throw IllegalStateException("Too many duplicated module names")
  }

  private fun generateModuleNames(
    sourceRootData: GradleSourceRootData,
  ): Iterable<String> {
    val projectModuleName = sourceRootData.projectModuleEntity.name
    val moduleName = projectModuleName + "." + sourceRootData.externalSourceSet.name
    return listOf(moduleName) + NumericNameGenerator.generate(moduleName)
  }

  private class GradleSourceRootData(
    val externalProject: ExternalProject,
    val externalSourceSet: ExternalSourceSet,
    val contentRoots: Set<Path>,
    val projectModuleEntity: ModuleEntity,
    val entitySource: EntitySource,
  )

  private data class GradleSourceRootEntitySource(
    override val projectPath: String,
  ) : GradleBridgeEntitySource
}