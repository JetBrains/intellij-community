// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.serialization

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.UnloadedModulesNameHolder
import com.intellij.platform.workspace.jps.bridge.JpsModuleExtensionBridge
import com.intellij.platform.workspace.jps.bridge.impl.JpsModelBridge
import com.intellij.platform.workspace.jps.bridge.impl.JpsProjectAdditionalData
import com.intellij.platform.workspace.jps.bridge.impl.JpsProjectBridge
import com.intellij.platform.workspace.jps.bridge.impl.library.sdk.JpsSdkLibraryBridge
import com.intellij.platform.workspace.jps.bridge.impl.module.JpsModuleBridge
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.jps.entities.customImlData
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.serialization.impl.*
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.url.UrlRelativizer
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.SystemProperties
import kotlinx.coroutines.runBlocking
import org.jdom.Element
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.serialization.*
import org.jetbrains.jps.model.serialization.JpsProjectConfigurationLoading.*
import org.jetbrains.jps.model.serialization.impl.JpsModuleSerializationDataExtensionImpl
import org.jetbrains.jps.model.serialization.impl.JpsSerializationViaWorkspaceModel
import org.jetbrains.jps.service.JpsServiceManager
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal class JpsSerializationViaWorkspaceModelImpl : JpsSerializationViaWorkspaceModel {
  override fun loadModel(projectPath: Path, workspaceStorageCachePath: Path?, externalConfigurationDirectory: Path?, optionsPath: Path?, 
                         globalWorkspaceStoragePath: Path?, loadUnloadedModules: Boolean): JpsModel {
    val virtualFileUrlManager = VirtualFileUrlManagerImpl()
    val errorReporter = createErrorReporter()
    val pathVariables = optionsPath?.let { JpsGlobalSettingsLoading.computeAllPathVariables(it) } ?: emptyMap()
    val globalMacroExpander = JpsMacroExpander(pathVariables)
    val globalStorageFromCache = globalWorkspaceStoragePath?.let {
      val urlRelativizer = if (useRelativePathsInCache) ApplicationLevelUrlRelativizer(insideIdeProcess = false) else null
      loadFromCacheFile(globalWorkspaceStoragePath, virtualFileUrlManager, urlRelativizer)
    }
    val globalStorage = globalStorageFromCache ?: loadGlobalStorage(optionsPath, virtualFileUrlManager, errorReporter, globalMacroExpander)

    val model = loadProject(projectPath, workspaceStorageCachePath, externalConfigurationDirectory, virtualFileUrlManager, pathVariables, errorReporter, globalStorage,
                            loadUnloadedModules)

    if (optionsPath != null) {
      val globalLoader = JpsGlobalLoader(globalMacroExpander, model.global, arrayOf(JpsGlobalLoader.FILE_TYPES_SERIALIZER))
      globalLoader.load(optionsPath)
    }

    return model
  }

  private fun loadProject(
    projectPath: Path,
    workspaceStorageCachePath: Path?,
    externalConfigurationDirectory: Path?,
    virtualFileUrlManager: VirtualFileUrlManager,
    pathVariables: Map<String, String>,
    errorReporter: ErrorReporter,
    globalStorage: EntityStorage,
    loadUnloadedModules: Boolean,
  ): JpsModelBridge {
    val configLocation = toConfigLocation(projectPath, virtualFileUrlManager)
    val contentReader = ProjectDirectJpsFileContentReader(configLocation.baseDirectoryUrl.toPath(), externalConfigurationDirectory, pathVariables)
    val storageFromCache = workspaceStorageCachePath?.let { 
      val urlRelativizer = 
        if (useRelativePathsInCache) JpsProjectUrlRelativizer(configLocation.baseDirectoryUrl.toPath().invariantSeparatorsPathString, insideIdeProcess = false) 
        else null
      loadFromCacheFile(workspaceStorageCachePath, virtualFileUrlManager, urlRelativizer)
    }
    val projectStorage = storageFromCache ?: loadProjectStorage(virtualFileUrlManager, errorReporter, configLocation, contentReader, loadUnloadedModules)
    val additionalData = loadAdditionalData(configLocation, contentReader.projectComponentLoader)

    val model = JpsModelBridge(projectStorage, globalStorage, additionalData)
    loadOtherProjectComponents(model.project, contentReader.projectComponentLoader, configLocation, externalConfigurationDirectory)
    loadOtherModuleComponents(model.project)
    return model
  }

  private val useRelativePathsInCache: Boolean
    get() = SystemProperties.getBooleanProperty("jps.workspace.storage.relative.paths.in.cache", false)
  
  private fun loadFromCacheFile(cachePath: Path, virtualFileUrlManager: VirtualFileUrlManager, urlRelativizer: UrlRelativizer?): EntityStorage? {
    val ijBuildVersion = "243.SNAPSHOT" //todo
    val serializer = EntityStorageSerializerImpl(JpsProcessEntityTypeResolver(), virtualFileUrlManager, urlRelativizer, ijBuildVersion)
    val storage = serializer.deserializeCache(cachePath).onFailure { logger.warn("Failed to load cache from $cachePath", it) }.getOrNull()
    if (storage != null) {
      logger.info("Loaded from cache: $cachePath")
    }
    return storage
  }

  private fun loadAdditionalData(
    configLocation: JpsProjectConfigLocation,
    componentLoader: JpsComponentLoader,
  ): JpsProjectAdditionalData {
    val projectName = when (configLocation) {
      is JpsProjectConfigLocation.DirectoryBased -> getDirectoryBaseProjectName(configLocation.projectDir.toPath(),
                                                                                configLocation.ideaFolder.toPath())
      is JpsProjectConfigLocation.FileBased -> FileUtil.getNameWithoutExtension(configLocation.iprFile.fileName)
    }
    val projectRootComponentFileElement = when (configLocation) {
      is JpsProjectConfigLocation.DirectoryBased -> componentLoader.loadRootElement(configLocation.ideaFolder.toPath().resolve("misc.xml"))
      is JpsProjectConfigLocation.FileBased -> componentLoader.loadRootElement(configLocation.iprFile.toPath())
    }
    val projectSdkId = readProjectSdkTypeAndName(projectRootComponentFileElement)?.let {
      SdkId(name = it.second, type = it.first)
    }
    val additionalData = JpsProjectAdditionalData(projectName, projectSdkId)
    return additionalData
  }

  private fun createErrorReporter() = object : ErrorReporter {
    override fun reportError(message: String, file: VirtualFileUrl) {
      throw CannotLoadJpsModelException(file.toPath().toFile(), message, null)
    }
  }

  private fun loadProjectStorage(
    virtualFileUrlManager: VirtualFileUrlManager, errorReporter: ErrorReporter,
    configLocation: JpsProjectConfigLocation, fileContentReader: ProjectDirectJpsFileContentReader, loadUnloadedModules: Boolean,
  ): EntityStorage {
    /* JpsProjectEntitiesLoader requires non-null value of externalStoragePath even if the external storage is not used, so use some
       artificial path if it isn't specified; externalStoragePath will be eliminated when IJPL-10518 is fixed
    */
    val externalStoragePath = fileContentReader.externalConfigurationDirectory 
                              ?: configLocation.baseDirectoryUrl.toPath().resolve("fake_external_build_system")
    
    val context = SerializationContextImpl(virtualFileUrlManager, fileContentReader)
    val serializers = JpsProjectEntitiesLoader.createProjectSerializers(configLocation, externalStoragePath, context)
    val mainStorage = MutableEntityStorage.create()
    val orphanageStorage = MutableEntityStorage.create()
    val unloadedStorage = MutableEntityStorage.create()
    val unloadedModuleNames = if (loadUnloadedModules) UnloadedModulesNameHolder.DUMMY else JpsUnloadedModulesNameHolder(readNamesOfUnloadedModules(configLocation.workspaceFile, fileContentReader.projectComponentLoader))
    @Suppress("SSBasedInspection")
    runBlocking {
      serializers.loadAll(context.fileContentReader, mainStorage, orphanageStorage, unloadedStorage, unloadedModuleNames, errorReporter)
    }

    return mainStorage
  }

  private fun loadGlobalStorage(
    optionsPath: Path?,
    virtualFileUrlManager: VirtualFileUrlManagerImpl,
    errorReporter: ErrorReporter,
    macroExpander: JpsMacroExpander,
  ): EntityStorage {
    val globalStorage = MutableEntityStorage.create()
    if (optionsPath == null) return globalStorage
    
    val reader = GlobalDirectJpsFileContentReader(macroExpander)
    val rootsTypes = JpsSdkLibraryBridge.serializers.map { it.typeId }
    val serializers = JpsGlobalEntitiesSerializers.createApplicationSerializers(virtualFileUrlManager, rootsTypes, optionsPath)
    for (serializer in serializers) {
      val loaded = serializer.loadEntities(reader, errorReporter, virtualFileUrlManager)
      serializer.checkAndAddToBuilder(globalStorage, globalStorage, loaded.data)
      loaded.exception?.let { throw it }
    }
    return globalStorage
  }

  override fun loadProject(projectPath: Path, externalConfigurationDirectory: Path?, pathVariables: Map<String, String>, loadUnloadedModules: Boolean): JpsProject {
    val model = loadProject(projectPath, null, externalConfigurationDirectory, VirtualFileUrlManagerImpl(), pathVariables, createErrorReporter(), 
                            MutableEntityStorage.create(), loadUnloadedModules)
    return model.project
  }

  private fun loadOtherProjectComponents(project: JpsProject, componentLoader: JpsComponentLoader, configLocation: JpsProjectConfigLocation, externalStoragePath: Path?) {
    setupSerializationExtension(project, configLocation.baseDirectoryUrl.toPath())
    when (configLocation) {
      is JpsProjectConfigLocation.DirectoryBased -> {
        val dotIdea = configLocation.ideaFolder.toPath()
        val defaultConfigFile = dotIdea.resolve("misc.xml")
        for (extension in JpsModelSerializerExtension.getExtensions()) {
          for (serializer in extension.projectExtensionSerializers) {
            componentLoader.loadComponents(dotIdea, defaultConfigFile, serializer, project)
          }
        }
        loadArtifactsFromDirectory(project, componentLoader, dotIdea, externalStoragePath)
        loadRunConfigurationsFromDirectory(project, componentLoader, dotIdea, configLocation.workspaceFile)
      }
      is JpsProjectConfigLocation.FileBased -> {
        val iprFile = configLocation.iprFile.toPath()
        val iprRoot = componentLoader.loadRootElement(iprFile)
        val iwsRoot = componentLoader.loadRootElement(configLocation.workspaceFile)
        loadProjectExtensionsFromIpr(project, iprRoot, iwsRoot)
        loadArtifactsFromIpr(project, iprRoot)
        loadRunConfigurationsFromIpr(project, iprRoot, iwsRoot)
      }
    }
  }

  private fun loadOtherModuleComponents(project: JpsProjectBridge) {
    project.modules.forEach { module ->
      loadOtherModuleComponents(module)
    }
  }

  private fun loadOtherModuleComponents(module: JpsModuleBridge) {
    val moduleEntity = module.entity
    val entitySource = moduleEntity.entitySource
    if (entitySource is JpsProjectFileEntitySource.FileInDirectory) {
      module.container.setChild(JpsModuleSerializationDataExtensionImpl.ROLE,
                                JpsModuleSerializationDataExtensionImpl(entitySource.directory.toPath()))
    }

    for (serializerExtension in JpsModelSerializerExtension.getExtensions()) {
      val rootElement = Element("module")
      //todo is it enough?
      moduleEntity.customImlData?.customModuleOptions?.forEach { (key, value) -> 
        rootElement.setAttribute(key, value)
      }
      moduleEntity.exModuleOptions?.let { externalSystemOptions ->
        externalSystemOptions.externalSystem?.let {
          rootElement.setAttribute("external.system.id", it)
          rootElement.setAttribute("ExternalSystem", it)
        }
      }
      serializerExtension.loadModuleOptions(module, rootElement)
    }
    JpsServiceManager.getInstance().getExtensions(JpsModuleExtensionBridge::class.java).forEach { extensionBridge ->
      extensionBridge.loadModuleExtensions(moduleEntity, module)
    }
  }

  private val JpsProjectConfigLocation.workspaceFile: Path
    get() = when (this) {
      is JpsProjectConfigLocation.DirectoryBased -> ideaFolder.toPath().resolve("workspace.xml")
      is JpsProjectConfigLocation.FileBased -> iprFileParent.toPath().resolve("${iprFile.fileName.substringBeforeLast('.')}.iws")
    }
}

private val logger = fileLogger()

private class JpsUnloadedModulesNameHolder(private val unloadedModuleNames: Set<String>) : UnloadedModulesNameHolder {
  override fun isUnloaded(name: String): Boolean = unloadedModuleNames.contains(name)

  override fun hasUnloaded(): Boolean = unloadedModuleNames.isNotEmpty()
}