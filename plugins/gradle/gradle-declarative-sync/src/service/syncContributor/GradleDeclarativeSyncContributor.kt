// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.model.ProjectBuildModelImpl
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncProjectConfigurator.project
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleDeclarativeEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleLinkedProjectEntitySource
import java.io.File
import java.nio.file.Path

@ApiStatus.Internal
@Order(GradleSyncContributor.Order.DECLARATIVE_CONTRIBUTOR)
class GradleDeclarativeSyncContributor : GradleSyncContributor {
  override suspend fun onResolveProjectInfoStarted(context: ProjectResolverContext, storage: MutableEntityStorage) {
    if (context.isPhasedSyncEnabled &&
        (File(context.projectPath, "build.gradle.dcl").isFile || File(context.projectPath, "settings.gradle.dcl").isFile)) {
      configureProject(context, storage)
    }
  }

  override suspend fun onModelFetchPhaseCompleted(context: ProjectResolverContext, storage: MutableEntityStorage, phase: GradleModelFetchPhase) {
    if (context.isPhasedSyncEnabled) {
      if (phase == GradleModelFetchPhase.PROJECT_LOADED_PHASE) {
        removeDeclarativeModel(context, storage)
      }
    }
  }

  private suspend fun configureProject(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val project = context.project()
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

    val virtualBuildFile: VirtualFile? = if (declarativeGradleBuildFile.isFile) {
      VfsUtil.findFileByIoFile(declarativeGradleBuildFile, false)
    } else {
      null
    }

    val androidContext = BuildModelContext.create(project, createBuildModelContext(context.projectPath))

    val projectBuildModel = ProjectBuildModelImpl(project, virtualBuildFile, androidContext)
    val projectName = entitySource.projectRootUrl.fileName // the settings model does not contain the project name

    val settingsModel = projectBuildModel.projectSettingsModel
    if(settingsModel == null) { // no settings file so assume simple project
      if(projectBuildModel.projectBuildModel == null) {
        val rootModuleEntity = addModuleEntity(storage, entitySource, projectName, listOf(
          InheritedSdkDependency,
          ModuleSourceDependency
        ))
        addContentRootEntity(storage, entitySource, rootModuleEntity, projectRootUrl)
        return
      }
      configureModule(storage, entitySource, projectRootUrl, projectName, projectBuildModel.projectBuildModel!!)
      return
    }

    // configure all modules found in settings file include statements including the root
    for(modulePath in settingsModel.modulePaths()) {
      val buildModel = settingsModel.moduleModel(modulePath)
      val moduleRootUrl = Path.of(settingsModel.moduleDirectory(modulePath).toString()).toVirtualFileUrl(virtualFileUrlManager)
      val moduleName: String = if(modulePath.equals(":")) projectName else projectName + "." + modulePath.replace(':', '.').removePrefix(".")

      // if the build file is missing in root, configure the project root module
      if(buildModel == null || !buildModel.virtualFile.isFile) {
        if(!modulePath.equals(":")) continue
        val rootModuleEntity = addModuleEntity(storage, entitySource, moduleName, listOf(
          InheritedSdkDependency,
          ModuleSourceDependency
        ))
        addContentRootEntity(storage, entitySource, rootModuleEntity, moduleRootUrl)
        continue
      }
      configureModule(storage, entitySource, moduleRootUrl, moduleName, buildModel)
    }
  }

  private fun configureModule(storage: MutableEntityStorage, entitySource: GradleDeclarativeEntitySource, moduleRootUrl: VirtualFileUrl, moduleName: String, buildModel: GradleBuildModel) {
    val javaModel = buildModel.javaApplication()

    val rootModuleEntity = addModuleEntity(storage, entitySource, moduleName, listOf(
      InheritedSdkDependency,
      ModuleSourceDependency
    ))
    addContentRootEntity(storage, entitySource, rootModuleEntity, moduleRootUrl)

    val dependenciesModel = javaModel.dependencies() //TODO convert android dependencies to workspace deps
    val mainSdkDependency: ModuleDependencyItem =
      if(javaModel.javaVersion().getValueType() == GradlePropertyModel.ValueType.NONE) InheritedSdkDependency
      else SdkDependency(SdkId(javaModel.javaVersion().toString(), "JavaSDK"))
    val mainDependencies = listOf(mainSdkDependency, ModuleSourceDependency)
      .plus(dependenciesModel.artifacts().map {
        LibraryDependency(LibraryId("Gradle: " + it.compactNotation(), LibraryTableId.ProjectLibraryTableId), false,
                          DependencyScope.COMPILE) // TODO should library deps be added?
      })
      .plus(dependenciesModel.modules().map {
        ModuleDependency(ModuleId(entitySource.projectRootUrl.fileName + "." + it.name() + ".main"), false, DependencyScope.COMPILE, false)
      })


    val mainModuleEntity = addModuleEntity(storage, entitySource, "$moduleName.main", mainDependencies)
    val mainContentRootEntity = addContentRootEntity(storage, entitySource, mainModuleEntity,
                                                     moduleRootUrl.append("src").append("main"))
    addSourceRootEntity(storage, entitySource, "java-source", moduleRootUrl.append("src").append("main").append("java"),
                        mainContentRootEntity)
    addSourceRootEntity(storage, entitySource, "java-resource",
                        moduleRootUrl.append("src").append("main").append("resources"), mainContentRootEntity)

    val mainModuleDep = ModuleDependency(ModuleId(mainModuleEntity.name), false, DependencyScope.COMPILE, false)
    val testSdkDependency: ModuleDependencyItem =
      if(javaModel.testing().javaVersion().getValueType() == GradlePropertyModel.ValueType.NONE) mainSdkDependency
      else SdkDependency(SdkId(javaModel.javaVersion().toString(), "JavaSDK"))
    val testDependenciesModel = javaModel.testing().dependencies()
    val testDependencies = listOf(testSdkDependency, ModuleSourceDependency, mainModuleDep)
      .plus(mainDependencies)
      .plus(testDependenciesModel.artifacts().map {
        LibraryDependency(LibraryId("Gradle: " + it.compactNotation(), LibraryTableId.ProjectLibraryTableId), false,
                          DependencyScope.COMPILE) // TODO should library deps be added?
      })
      .plus(testDependenciesModel.modules().map {
        ModuleDependency(ModuleId(entitySource.projectRootUrl.fileName + "." + it.name().replace(":", ".") + ".main"),
                         false, DependencyScope.COMPILE, false)
      })

    val testModuleEntity = addModuleEntity(storage, entitySource, "$moduleName.test", testDependencies)
    val testContentRootEntity = addContentRootEntity(storage, entitySource, testModuleEntity,
                                                     moduleRootUrl.append("src").append("test"))
    addSourceRootEntity(storage, entitySource, "java-test", moduleRootUrl.append("src").append("test").append("java"),
                        testContentRootEntity)
    addSourceRootEntity(storage, entitySource, "java-test-resource", moduleRootUrl.append("src").append("test").append("resources"),
                        testContentRootEntity)
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

  private suspend fun removeDeclarativeModel(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val project = context.project()
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