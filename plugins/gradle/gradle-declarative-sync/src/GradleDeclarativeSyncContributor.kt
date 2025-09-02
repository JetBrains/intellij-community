// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.declarativeSync

import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.java.JavaDeclarativeModel
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.model.ProjectBuildModelImpl
import com.android.tools.idea.gradle.feature.flags.DeclarativeStudioSupport
import com.intellij.gradle.declarativeSync.GradleLibrariesResolver.LibDepData
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.project.nameGenerator.ModuleNameGenerator
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.impl.toPath
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleDeclarativeEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleLinkedProjectEntitySource
import java.io.File
import java.nio.file.Path

private val LOG = logger<GradleDeclarativeSyncContributor>()

/**
 * Statically analyzes declarative Gradle build files and builds a model of the project structure if possible
 */
@ApiStatus.Internal
@Order(GradleSyncContributor.Order.DECLARATIVE_CONTRIBUTOR)
class GradleDeclarativeSyncContributor : GradleSyncContributor {
  override suspend fun onResolveProjectInfoStarted(context: ProjectResolverContext, storage: MutableEntityStorage) {
    if (context.isPhasedSyncEnabled && DeclarativeStudioSupport.isEnabled() &&
        (File(context.projectPath, "build.gradle.dcl").isFile || File(context.projectPath, "settings.gradle.dcl").isFile)) {
      LOG.debug("Starting Declarative Gradle static import")
      configureProject(context, storage)
      LOG.debug("Finished Declarative Gradle static import")
    }
  }

  override suspend fun onModelFetchPhaseCompleted(context: ProjectResolverContext, storage: MutableEntityStorage, phase: GradleModelFetchPhase) {
    if (context.isPhasedSyncEnabled) {
      if (phase == GradleModelFetchPhase.PROJECT_LOADED_PHASE) {
        removeDeclarativeModel(context, storage)
      }
    }
  }

  private fun configureProject(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val project = context.project
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val projectRootPath = Path.of(context.projectPath)
    val projectRootUrl = projectRootPath.toVirtualFileUrl(virtualFileUrlManager)
    val entitySource = GradleDeclarativeEntitySource(projectRootUrl)

    // return if there is already a model other than the simple project root one
    val contentRootEntities = storage.entities<ContentRootEntity>()
    if(!contentRootEntities.toList().isEmpty()) {
      val linkedProjectEntitySource = GradleLinkedProjectEntitySource(projectRootUrl)
      if (contentRootEntities.any { it -> it.entitySource != linkedProjectEntitySource })
        return

      // remove the old one
      val linkedProjectEntities = storage.entitiesBySource { it == linkedProjectEntitySource }
      for (linkedProjectEntity in linkedProjectEntities.toList()) {
        storage.removeEntity(linkedProjectEntity)
      }
    }

    // try finding the Gradle build file in the project root directory
    val declarativeGradleBuildFile = File(context.projectPath, "build.gradle.dcl")

    val virtualBuildFile: VirtualFile? =
      if (declarativeGradleBuildFile.isFile)
        VfsUtil.findFileByIoFile(declarativeGradleBuildFile, false)
      else null

    val androidContext = BuildModelContext.create(project, createBuildModelContext(context.projectPath))

    val projectBuildModel = ProjectBuildModelImpl(project, virtualBuildFile, androidContext)
    val projectName = entitySource.projectRootUrl.fileName // the settings model does not contain the project name

    val settingsModel = projectBuildModel.projectSettingsModel

    val rootDependencyDependencies = GradleLibrariesResolver()
      .resolveAndAddLibraries(project, storage, context, entitySource, projectBuildModel)

    if(settingsModel == null) { // no settings file so assume simple project
      LOG.debug("No settings file found in project root")
      val projectBuildModel = projectBuildModel.projectBuildModel
      if(projectBuildModel == null) {
        LOG.debug("No build file found in project root")
        addEmptyModule(storage, entitySource, projectName, projectRootUrl)
        return // zero info so return early
      }
      LOG.debug("Found build file in project root, adding root project module and its content root")
      configureModule(storage, entitySource, projectRootUrl, projectName, projectBuildModel.javaApplication(), rootDependencyDependencies)
    } else {
      LOG.debug("Found settings file in project root, adding modules and their content roots")
      // configure all modules found in settings file include statements including the root
      for(modulePath in settingsModel.modulePaths()) {
        LOG.debug("Found module path: $modulePath")
        val buildModel = settingsModel.moduleModel(modulePath)
        val moduleRootUrl = Path.of(settingsModel.moduleDirectory(modulePath).toString()).toVirtualFileUrl(virtualFileUrlManager)
        val moduleName: String = resolveModuleName(modulePath, projectRootUrl)

        // if the build file is missing in root, configure the project root module
        if(buildModel == null || !buildModel.virtualFile.isFile) {
          LOG.debug("No build file found for module path: '$modulePath', skipping module")
          if(!modulePath.equals(":")) continue
          addEmptyModule(storage, entitySource, moduleName, moduleRootUrl)
          continue
        }
        LOG.debug("Found build file for module path: '$modulePath', configuring module")
        configureModule(storage, entitySource, moduleRootUrl, moduleName, buildModel.javaApplication(), rootDependencyDependencies)
      }
    }
  }

  private fun addEmptyModule(storage: MutableEntityStorage, entitySource: GradleDeclarativeEntitySource,
                             moduleName: String, moduleRootUrl: VirtualFileUrl) {
    val rootModuleEntity = addModuleEntity(storage, entitySource, moduleName, listOf(
      InheritedSdkDependency,
      ModuleSourceDependency
    ))
    addContentRootEntity(storage, entitySource, rootModuleEntity, moduleRootUrl)
  }

  private fun configureModule(
    storage: MutableEntityStorage,
    entitySource: GradleDeclarativeEntitySource,
    moduleRootUrl: VirtualFileUrl,
    moduleName: String,
    javaModel: JavaDeclarativeModel,
    rootDependencyMapping: Map<LibDepData, List<LibDepData>>
  ) {
    val java_sdk = "JavaSDK"
    val src_main_path = "src/main"
    val src_test_path = "src/test"

    addEmptyModule(storage, entitySource, moduleName, moduleRootUrl)

    // Main submodule configuration
    val mainSdkDependencyItem: ModuleDependencyItem = if (javaModel.javaVersion().getValueType() == GradlePropertyModel.ValueType.NONE)
      InheritedSdkDependency
    else
      SdkDependency(SdkId(javaModel.javaVersion().toString(), java_sdk))


    val mainDependencies = buildDependenciesList(
      mainSdkDependencyItem,
      javaModel.dependencies(), rootDependencyMapping,
      entitySource.projectRootUrl
    )

    val mainModuleEntity = addModuleEntity(storage, entitySource, "$moduleName.main", mainDependencies)
    val mainContentRootEntity = addContentRootEntity(
      storage, entitySource, mainModuleEntity, moduleRootUrl.append(src_main_path)
    )
    addSourceRoots(
      storage, entitySource, mainContentRootEntity,
      listOf(Pair("java-source", "java"), Pair("java-resource", "resources")),
      moduleRootUrl.append(src_main_path)
    )

    // Test submodule configuration
    val testSdkDependencyItem: ModuleDependencyItem = if (javaModel.testing().javaVersion().getValueType() == GradlePropertyModel.ValueType.NONE) {
      mainSdkDependencyItem
    } else {
      SdkDependency(SdkId(javaModel.testing().javaVersion().toString(), java_sdk))
    }

    val mainModuleDependency = ModuleDependency(ModuleId(mainModuleEntity.name), false, DependencyScope.COMPILE, false)
    val testDependencies = buildDependenciesList(
      testSdkDependencyItem,
      javaModel.testing().dependencies(), rootDependencyMapping,
      entitySource.projectRootUrl, additionalDependencies = listOf(mainModuleDependency).plus(mainDependencies)
    )

    val testModuleEntity = addModuleEntity(storage, entitySource, "$moduleName.test", testDependencies)
    val testContentRootEntity = addContentRootEntity(
      storage, entitySource, testModuleEntity, moduleRootUrl.append(src_test_path)
    )
    addSourceRoots(
      storage, entitySource, testContentRootEntity,
      listOf(Pair("java-test", "java"), Pair("java-test-resource", "resources")),
      moduleRootUrl.append(src_test_path)
    )
  }

  private fun addSourceRoots(
    storage: MutableEntityStorage,
    entitySource: GradleDeclarativeEntitySource,
    contentRootEntity: ContentRootEntity.Builder,
    sourceRoots: List<Pair<String, String>>,
    basePath: VirtualFileUrl
  ) {
    sourceRoots.forEach { (type, path) ->
      addSourceRootEntity(storage, entitySource, type, basePath.append(path), contentRootEntity)
    }
  }

  private fun buildDependenciesList(
    sdkDependency: ModuleDependencyItem,
    dependenciesModel: DependenciesModel,
    rootDependencyMapping: Map<LibDepData, List<LibDepData>>,
    projectRootUrl: VirtualFileUrl,
    additionalDependencies: List<ModuleDependencyItem> = emptyList()
  ): List<ModuleDependencyItem> {
    return listOf(sdkDependency, ModuleSourceDependency)
      .plus(additionalDependencies)
      .plus(convertDependenciesModel(dependenciesModel, projectRootUrl))
      .plus(
        dependenciesModel.artifacts()
          .map { LibDepData(it) }
          .flatMap { rootDependencyMapping[it] ?: emptyList() }
          .map { convertLibDependency(it) }
      ).distinct()
  }

  private fun convertDependenciesModel(dependenciesModel: DependenciesModel, projectRootUrl: VirtualFileUrl): List<ModuleDependencyItem> {
    return (dependenciesModel.artifacts().map {
        LibraryDependency(
          LibraryId("Gradle: " + it.compactNotation(), LibraryTableId.ProjectLibraryTableId),
          false, DependencyScope.COMPILE)
      }).plus(dependenciesModel.modules().map {
        ModuleDependency(
          ModuleId(resolveModuleName(it.name(), projectRootUrl) + ".main"),
          false, DependencyScope.COMPILE, false)
      })
  }

  private fun convertLibDependency(dependency: LibDepData): ModuleDependencyItem {
    return LibraryDependency(
      LibraryId("Gradle: " + dependency.compactNotation(), LibraryTableId.ProjectLibraryTableId),
      false, DependencyScope.COMPILE)
  }

  private fun resolveModuleName(moduleName: String, projectRootUrl: VirtualFileUrl): String {
    if(moduleName == ":") return projectRootUrl.fileName
    return ModuleNameGenerator.generate(null, moduleName.removePrefix(":").replace(':', '.'), projectRootUrl.toPath(), ".").first()
  }

  private fun addModuleEntity(
    storage: MutableEntityStorage,
    entitySource: GradleDeclarativeEntitySource,
    moduleName: String,
    dependencies: List<ModuleDependencyItem>
  ): ModuleEntity.Builder {
    val moduleName = moduleName
    val moduleEntity = ModuleEntity(
      name = moduleName,
      entitySource = entitySource,
      dependencies = dependencies
    ) {
      type = ModuleTypeId("JAVA_MODULE")
    }
    storage addEntity moduleEntity
    return moduleEntity
  }

  private fun addContentRootEntity(
    storage: MutableEntityStorage,
    entitySource: GradleDeclarativeEntitySource,
    moduleEntity: ModuleEntity.Builder,
    url: VirtualFileUrl
  ): ContentRootEntity.Builder {
    val contentRootEntity = ContentRootEntity(
      url = url,
      entitySource = entitySource,
      excludedPatterns = emptyList()
    ) {
      module = moduleEntity
    }
    storage addEntity contentRootEntity
    return contentRootEntity
  }

  private fun addSourceRootEntity(
    storage: MutableEntityStorage,
    entitySource: GradleDeclarativeEntitySource,
    type: String,
    url: VirtualFileUrl,
    contentRootEntity: ContentRootEntity.Builder
  ) {
    storage addEntity SourceRootEntity(
      url = url,
      rootTypeId = SourceRootTypeId(type),
      entitySource = entitySource
    ) {
      contentRoot = contentRootEntity
    }
  }

  private fun createBuildModelContext(projectPath: String): BuildModelContext.ResolvedConfigurationFileLocationProvider {
    return object : BuildModelContext.ResolvedConfigurationFileLocationProvider {
      override fun getGradleBuildFile(module: Module): VirtualFile? {
        // Resolved location is unknown (no sync).
        return null
      }

      override fun getGradleProjectRootPath(module: Module): @SystemIndependent String? {
        return projectPath
      }

      override fun getGradleProjectRootPath(project: Project): @SystemIndependent String? {
        return projectPath
      }
    }
  }

  private fun removeDeclarativeModel(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val project = context.project
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val projectRootPath = Path.of(context.projectPath)
    val projectRootUrl = projectRootPath.toVirtualFileUrl(virtualFileUrlManager)
    val entitySource = GradleDeclarativeEntitySource(projectRootUrl)

    val projectEntities = storage.entitiesBySource { it == entitySource }
    for (projectEntity in projectEntities.toList()) {
      storage.removeEntity(projectEntity)
    }
  }
}

