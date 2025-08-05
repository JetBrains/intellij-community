// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.declarativeSync

import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.java.JavaDeclarativeModel
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.model.ProjectBuildModelImpl
import com.android.tools.idea.gradle.feature.flags.DeclarativeStudioSupport
import com.intellij.gradle.declarativeSync.GradleLibrariesResolver.LibDepData
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.project.nameGenerator.ModuleNameGenerator
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.impl.toPath
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.virtualFileUrl
import org.jetbrains.plugins.gradle.service.syncContributor.bridge.GradleBridgeEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.hasNonPreviewEntities
import org.jetbrains.plugins.gradle.service.syncContributor.removeProjectRoot
import java.io.File

private val LOG = logger<GradleDeclarativeSyncContributor>()

/**
 * Statically analyzes declarative Gradle build files and builds a model of the project structure if possible
 */
@ApiStatus.Internal
@Order(GradleSyncContributor.Order.DECLARATIVE_CONTRIBUTOR)
class GradleDeclarativeSyncContributor : GradleSyncContributor {

  override val phase: GradleSyncPhase = GradleSyncPhase.DECLARATIVE_PHASE

  override suspend fun configureProjectModel(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
  ) {
    if (!context.isPhasedSyncEnabled) return

    if (!DeclarativeStudioSupport.isEnabled()) {
      LOG.debug("Skipped Declarative Gradle static import: Disabled")
      return
    }
    if (!File(context.projectPath, "build.gradle.dcl").isFile && !File(context.projectPath, "settings.gradle.dcl").isFile) {
      LOG.debug("Skipped Declarative Gradle static import: No Gradle DCL files")
      return
    }
    if (hasNonPreviewEntities(context, storage)) {
      LOG.debug("Skipped Declarative Gradle static import: Secondary sync")
      return
    }
    LOG.debug("Removing preview project root")
    removeProjectRoot(context, storage)
    LOG.debug("Starting Declarative Gradle static import")
    configureProject(context, storage)
    LOG.debug("Finished Declarative Gradle static import")
  }

  private fun configureProject(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
  ) {
    val project = context.project

    val projectRootUrl = context.virtualFileUrl(context.projectPath)
    val entitySource = GradleDeclarativeEntitySource(context.projectPath)

    // try finding the Gradle build file in the project root directory
    val declarativeGradleBuildFile = File(context.projectPath, "build.gradle.dcl")

    val virtualBuildFile: VirtualFile? =
      if (declarativeGradleBuildFile.isFile)
        VfsUtil.findFileByIoFile(declarativeGradleBuildFile, false)
      else null

    val androidContext = BuildModelContext.create(project, createBuildModelContext(context.projectPath))

    val projectBuildModel = ProjectBuildModelImpl(project, virtualBuildFile, androidContext)
    val projectName = projectRootUrl.fileName // the settings model does not contain the project name

    val settingsModel = projectBuildModel.projectSettingsModel

    val rootDependencyDependencies = GradleLibrariesResolver()
      .resolveAndAddLibraries(project, storage, context, entitySource, projectBuildModel)

    if (settingsModel == null) { // no settings file so assume simple project
      LOG.debug("No settings file found in project root")
      val projectBuildModel = projectBuildModel.projectBuildModel
      if (projectBuildModel == null) {
        LOG.debug("No build file found in project root")
        addEmptyModule(storage, entitySource, projectName, projectRootUrl)
        return // zero info so return early
      }
      LOG.debug("Found build file in project root, adding root project module and its content root")
      configureModule(storage, entitySource, projectRootUrl, projectRootUrl, projectName, projectBuildModel.javaApplication(), rootDependencyDependencies)
    }
    else {
      LOG.debug("Found settings file in project root, adding modules and their content roots")
      // configure all modules found in settings file include statements including the root
      for (modulePath in settingsModel.modulePaths()) {
        LOG.debug("Found module path: $modulePath")
        val buildModel = settingsModel.moduleModel(modulePath)
        val moduleRootUrl = context.virtualFileUrl(settingsModel.moduleDirectory(modulePath) ?: continue)
        val moduleName: String = resolveModuleName(modulePath, projectRootUrl)

        // if the build file is missing in root, configure the project root module
        if (buildModel == null || !buildModel.virtualFile.isFile) {
          LOG.debug("No build file found for module path: '$modulePath', skipping module")
          if (!modulePath.equals(":")) continue
          addEmptyModule(storage, entitySource, moduleName, moduleRootUrl)
          continue
        }
        LOG.debug("Found build file for module path: '$modulePath', configuring module")
        configureModule(storage, entitySource, projectRootUrl, moduleRootUrl, moduleName, buildModel.javaApplication(), rootDependencyDependencies)
      }
    }
  }

  private fun addEmptyModule(
    storage: MutableEntityStorage,
    entitySource: EntitySource,
    moduleName: String,
    moduleRootUrl: VirtualFileUrl,
  ) {
    val rootModuleEntity = addModuleEntity(storage, entitySource, moduleName, listOf(
      InheritedSdkDependency,
      ModuleSourceDependency
    ))
    addContentRootEntity(storage, entitySource, rootModuleEntity, moduleRootUrl)
  }

  private fun configureModule(
    storage: MutableEntityStorage,
    entitySource: EntitySource,
    projectRootUrl: VirtualFileUrl,
    moduleRootUrl: VirtualFileUrl,
    moduleName: String,
    javaModel: JavaDeclarativeModel,
    rootDependencyMapping: Map<LibDepData, List<LibDepData>>,
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
      projectRootUrl
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
    }
    else {
      SdkDependency(SdkId(javaModel.testing().javaVersion().toString(), java_sdk))
    }

    val mainModuleDependency = ModuleDependency(ModuleId(mainModuleEntity.name), false, DependencyScope.COMPILE, false)
    val testDependencies = buildDependenciesList(
      testSdkDependencyItem,
      javaModel.testing().dependencies(), rootDependencyMapping,
      projectRootUrl, additionalDependencies = listOf(mainModuleDependency).plus(mainDependencies)
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
    entitySource: EntitySource,
    contentRootEntity: ContentRootEntity.Builder,
    sourceRoots: List<Pair<String, String>>,
    basePath: VirtualFileUrl,
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
    additionalDependencies: List<ModuleDependencyItem> = emptyList(),
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

  private fun convertDependenciesModel(
    dependenciesModel: DependenciesModel,
    projectRootUrl: VirtualFileUrl,
  ): List<ModuleDependencyItem> {
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

  private fun convertLibDependency(
    dependency: LibDepData,
  ): ModuleDependencyItem {
    return LibraryDependency(
      LibraryId("Gradle: " + dependency.compactNotation(), LibraryTableId.ProjectLibraryTableId),
      false, DependencyScope.COMPILE)
  }

  private fun resolveModuleName(
    moduleName: String,
    projectRootUrl: VirtualFileUrl,
  ): String {
    if (moduleName == ":") return projectRootUrl.fileName
    return ModuleNameGenerator.generate(null, moduleName.removePrefix(":").replace(':', '.'), projectRootUrl.toPath(), ".").first()
  }

  private fun addModuleEntity(
    storage: MutableEntityStorage,
    entitySource: EntitySource,
    moduleName: String,
    dependencies: List<ModuleDependencyItem>,
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
    entitySource: EntitySource,
    moduleEntity: ModuleEntity.Builder,
    url: VirtualFileUrl,
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
    entitySource: EntitySource,
    type: String,
    url: VirtualFileUrl,
    contentRootEntity: ContentRootEntity.Builder,
  ) {
    storage addEntity SourceRootEntity(
      url = url,
      rootTypeId = SourceRootTypeId(type),
      entitySource = entitySource
    ) {
      contentRoot = contentRootEntity
    }
  }

  private fun createBuildModelContext(
    projectPath: String,
  ): BuildModelContext.ResolvedConfigurationFileLocationProvider {
    return object : BuildModelContext.ResolvedConfigurationFileLocationProvider {
      // Resolved location is unknown (no sync).
      override fun getGradleBuildFile(module: Module) = null
      override fun getGradleProjectRootPath(module: Module) = projectPath
      override fun getGradleProjectRootPath(project: Project) = projectPath
    }
  }

  private data class GradleDeclarativeEntitySource(
    override val projectPath: String,
  ) : GradleBridgeEntitySource

  class Bridge : GradleSyncContributor {

    override val phase: GradleSyncPhase = GradleSyncPhase.PROJECT_MODEL_PHASE

    override suspend fun configureProjectModel(
      context: ProjectResolverContext,
      storage: MutableEntityStorage,
    ) {
      if (!context.isPhasedSyncEnabled) return

      removeDeclarativeModel(context, storage)
    }

    private fun removeDeclarativeModel(
      context: ProjectResolverContext,
      storage: MutableEntityStorage,
    ) {
      val entitySource = GradleDeclarativeEntitySource(context.projectPath)
      val projectEntities = storage.entitiesBySource { it == entitySource }
      for (projectEntity in projectEntities.toList()) {
        storage.removeEntity(projectEntity)
      }
    }
  }
}
