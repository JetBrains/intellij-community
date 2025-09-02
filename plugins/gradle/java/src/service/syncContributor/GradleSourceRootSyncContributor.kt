// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaResourceRoots
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType
import com.intellij.openapi.externalSystem.service.project.nameGenerator.NumericNameGenerator
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.CanonicalPathPrefixTree
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.prefixTree.map.PrefixTreeMap
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel
import org.jetbrains.plugins.gradle.service.project.GradleContentRootIndex
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getModuleId
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleBuildEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleLinkedProjectEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleProjectEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleSourceSetEntitySource
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import kotlin.io.path.exists

@ApiStatus.Internal
@Order(GradleSyncContributor.Order.SOURCE_ROOT_CONTRIBUTOR)
class GradleSourceRootSyncContributor : GradleSyncContributor {

  override suspend fun onModelFetchPhaseCompleted(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
    phase: GradleModelFetchPhase,
  ) {
    if (context.isPhasedSyncEnabled) {
      if (phase == GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE) {
        configureProjectSourceRoots(context, storage)
      }
    }
  }

  private suspend fun configureProjectSourceRoots(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val project = context.project
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val sourceRootsToAdd = LinkedHashMap<GradleSourceSetEntitySource, GradleSourceRootData>()

    val moduleEntities = storage.entities<ModuleEntity>()

    val contentRootIndex = GradleContentRootIndex()

    for (buildModel in context.allBuilds) {
      for (projectModel in buildModel.projects) {
        val sourceSetModel = context.getProjectModel(projectModel, GradleSourceSetModel::class.java) ?: continue
        for (sourceSet in sourceSetModel.sourceSets.values) {
          contentRootIndex.addSourceRoots(sourceSet)
        }
      }
    }

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
        val sourceSetModel = context.getProjectModel(projectModel, GradleSourceSetModel::class.java) ?: continue
        val projectModuleEntity = moduleEntities.singleOrNull { isProjectModuleEntity(it, projectEntitySource) } ?: continue

        for (sourceSet in sourceSetModel.sourceSets.values) {

          val sourceSetEntitySource = GradleSourceSetEntitySource(projectEntitySource, sourceSet.name)

          val contentRoots = resolveContentRoots(virtualFileUrlManager, externalProject, sourceSet, contentRootIndex)
          val sourceRootData = GradleSourceRootData(externalProject, sourceSet, projectModuleEntity, sourceSetEntitySource, contentRoots)

          if (moduleEntities.any { isConflictedModuleEntity(it, sourceRootData) }) {
            continue
          }
          if (isUnloadedModule(project, sourceRootData)) {
            continue
          }

          sourceRootsToAdd[sourceSetEntitySource] = sourceRootData
        }
      }
    }

    for (sourceRootData in sourceRootsToAdd.values) {

      checkCanceled()

      configureSourceRoot(context, storage, virtualFileUrlManager, sourceRootData)
    }
  }

  private fun isProjectModuleEntity(
    moduleEntity: ModuleEntity,
    entitySource: GradleProjectEntitySource,
  ): Boolean {
    return moduleEntity.entitySource == entitySource ||
           moduleEntity.contentRoots.singleOrNull()?.url == entitySource.projectRootUrl
  }

  private fun isConflictedModuleEntity(
    moduleEntity: ModuleEntity,
    sourceRootData: GradleSourceRootData,
  ): Boolean {
    return moduleEntity.entitySource == sourceRootData.entitySource ||
           moduleEntity.contentRoots.any { it.url in sourceRootData.contentRootUrls }
  }

  private fun isUnloadedModule(
    project: Project,
    sourceRootData: GradleSourceRootData,
  ): Boolean {
    val unloadedModulesListStorage = UnloadedModulesListStorage.getInstance(project)
    val unloadedModuleNameHolder = unloadedModulesListStorage.unloadedModuleNameHolder
    for (moduleName in generateModuleNames(sourceRootData)) {
      if (unloadedModuleNameHolder.isUnloaded(moduleName)) {
        return true
      }
    }
    return false
  }

  private fun configureSourceRoot(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
    virtualFileUrlManager: VirtualFileUrlManager,
    sourceRootData: GradleSourceRootData,
  ) {
    val moduleEntity = addModuleEntity(storage, sourceRootData)
    addExModuleOptionsEntity(context, storage, moduleEntity, sourceRootData)
    val contentRootEntities = addContentRootEntities(storage, sourceRootData, moduleEntity)
    addSourceRootEntities(storage, virtualFileUrlManager, sourceRootData, contentRootEntities)
  }

  private fun addModuleEntity(
    storage: MutableEntityStorage,
    sourceRootData: GradleSourceRootData,
  ): ModuleEntity.Builder {
    val entitySource = sourceRootData.entitySource

    val moduleName = resolveUniqueModuleName(storage, sourceRootData)

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

  private fun addExModuleOptionsEntity(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
    moduleEntity: ModuleEntity.Builder,
    sourceRootData: GradleSourceRootData,
  ) {
    val externalProject = sourceRootData.externalProject
    val sourceSet = sourceRootData.sourceSet
    val entitySource = sourceRootData.entitySource

    storage addEntity ExternalSystemModuleOptionsEntity(
      entitySource = entitySource
    ) {
      module = moduleEntity

      externalSystem = GradleConstants.SYSTEM_ID.id
      linkedProjectId = getModuleId(context, externalProject, sourceSet)
      linkedProjectPath = externalProject.projectDir.path
      rootProjectPath = context.projectPath

      externalSystemModuleGroup = externalProject.group
      externalSystemModuleVersion = externalProject.version
      externalSystemModuleType = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
    }
  }

  private fun addContentRootEntities(
    storage: MutableEntityStorage,
    sourceRootData: GradleSourceRootData,
    moduleEntity: ModuleEntity.Builder,
  ): PrefixTreeMap<String, ContentRootEntity.Builder> {
    val entitySource = sourceRootData.entitySource
    val contentRootEntities = CanonicalPathPrefixTree.createMap<ContentRootEntity.Builder>()

    for (contentRootUrl in sourceRootData.contentRootUrls) {
      val contentRootEntity = ContentRootEntity(
        url = contentRootUrl,
        entitySource = entitySource,
        excludedPatterns = emptyList()
      ) {
        module = moduleEntity
      }
      storage addEntity contentRootEntity
      contentRootEntities[contentRootUrl.url] = contentRootEntity
    }
    return contentRootEntities
  }

  private fun addSourceRootEntities(
    storage: MutableEntityStorage,
    virtualFileUrlManager: VirtualFileUrlManager,
    sourceRootData: GradleSourceRootData,
    contentRootEntities: PrefixTreeMap<String, ContentRootEntity.Builder>,
  ) {
    val entitySource = sourceRootData.entitySource
    for ((sourceRootType, sourceDirectorySet) in sourceRootData.sourceSet.sources) {
      for (sourceDirectory in sourceDirectorySet.srcDirs) {
        val sourceRootPath = sourceDirectory.toPath()
        if (sourceRootType.isExcluded) {
          addExcludedRootEntity(storage, virtualFileUrlManager, contentRootEntities, sourceRootPath, entitySource)
        }
        else {
          addSourceRootEntity(storage, virtualFileUrlManager, contentRootEntities, sourceRootPath, sourceRootType, entitySource)
        }
      }
    }
  }

  private fun addSourceRootEntity(
    storage: MutableEntityStorage,
    virtualFileUrlManager: VirtualFileUrlManager,
    contentRootEntities: PrefixTreeMap<String, ContentRootEntity.Builder>,
    sourceRootPath: Path,
    sourceRootType: IExternalSystemSourceType,
    entitySource: GradleSourceSetEntitySource,
  ) {
    val sourceRootUrl = sourceRootPath.toVirtualFileUrl(virtualFileUrlManager)
    val contentRootEntity = contentRootEntities.getAncestorValues(sourceRootUrl.url).last()

    if (!sourceRootPath.exists()) {
      // TODO: SourceFolderManager.addSourceRootEntity
      return
    }

    storage addEntity SourceRootEntity(
      url = sourceRootUrl,
      rootTypeId = sourceRootType.toSourceRootTypeId(),
      entitySource = entitySource
    ) {
      contentRoot = contentRootEntity

      if (sourceRootType.isResource) {
        javaResourceRoots += JavaResourceRootPropertiesEntity(
          generated = sourceRootType.isGenerated,
          relativeOutputPath = "",
          entitySource = entitySource
        )
      }
      else {
        javaSourceRoots += JavaSourceRootPropertiesEntity(
          generated = sourceRootType.isGenerated,
          packagePrefix = "",
          entitySource = entitySource
        )
      }
    }
  }

  private fun addExcludedRootEntity(
    storage: MutableEntityStorage,
    virtualFileUrlManager: VirtualFileUrlManager,
    contentRootEntities: PrefixTreeMap<String, ContentRootEntity.Builder>,
    sourceRootPath: Path,
    entitySource: GradleSourceSetEntitySource,
  ) {
    val sourceRootUrl = sourceRootPath.toVirtualFileUrl(virtualFileUrlManager)
    val contentRootEntity = contentRootEntities.getAncestorValues(sourceRootUrl.url).last()

    storage addEntity ExcludeUrlEntity(
      url = sourceRootUrl,
      entitySource = entitySource
    ) {
      contentRoot = contentRootEntity
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

  private fun resolveContentRoots(
    virtualFileUrlManager: VirtualFileUrlManager,
    externalProject: ExternalProject,
    sourceSet: ExternalSourceSet,
    contentRootIndex: GradleContentRootIndex,
  ): Set<VirtualFileUrl> {
    val contentRootUrls = LinkedHashSet<VirtualFileUrl>()
    val contentRootPaths = contentRootIndex.resolveContentRoots(externalProject, sourceSet)
    for (contentRootPath in contentRootPaths) {
      val contentRootUrl = contentRootPath.toVirtualFileUrl(virtualFileUrlManager)
      contentRootUrls.add(contentRootUrl)
    }
    return contentRootUrls
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
    val moduleName = projectModuleName + "." + sourceRootData.entitySource.sourceSetName
    return listOf(moduleName) + NumericNameGenerator.generate(moduleName)
  }

  private class GradleSourceRootData(
    val externalProject: ExternalProject,
    val sourceSet: ExternalSourceSet,
    val projectModuleEntity: ModuleEntity,
    val entitySource: GradleSourceSetEntitySource,
    val contentRootUrls: Set<VirtualFileUrl>,
  )
}