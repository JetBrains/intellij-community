// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.serialization.SerializationContext
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

object JpsProjectEntitiesLoader {

  @TestOnly
  suspend fun loadProject(configLocation: JpsProjectConfigLocation,
                          builder: MutableEntityStorage,
                          orphanage: MutableEntityStorage,
                          externalStoragePath: Path,
                          errorReporter: ErrorReporter,
                          unloadedModulesNameHolder: com.intellij.platform.workspace.jps.UnloadedModulesNameHolder = com.intellij.platform.workspace.jps.UnloadedModulesNameHolder.DUMMY,
                          unloadedEntitiesBuilder: MutableEntityStorage = MutableEntityStorage.create(),
                          context: SerializationContext): JpsProjectSerializers {
    val data = createProjectSerializers(configLocation, externalStoragePath, context)
    data.loadAll(context.fileContentReader, builder, orphanage, unloadedEntitiesBuilder, unloadedModulesNameHolder, errorReporter)
    return data
  }
  
  @JvmStatic
  val isModulePropertiesBridgeEnabled: Boolean
    get() = Registry.`is`("workspace.model.test.properties.bridge")
  
  fun loadModule(moduleFile: Path, configLocation: JpsProjectConfigLocation, builder: MutableEntityStorage,
                 errorReporter: ErrorReporter, context: SerializationContext) {
    val source = JpsProjectFileEntitySource.FileInDirectory(moduleFile.parent.toVirtualFileUrl(context.virtualFileUrlManager), configLocation)
    loadModule(moduleFile, source, builder, builder, errorReporter, context)
  }

  internal fun loadModule(moduleFile: Path,
                          source: JpsProjectFileEntitySource.FileInDirectory,
                          builder: MutableEntityStorage,
                          orphanage: MutableEntityStorage,
                          errorReporter: ErrorReporter,
                          context: SerializationContext) {
    val reader = context.fileContentReader
    val serializer = ModuleListSerializerImpl.createModuleEntitiesSerializer(moduleFile.toVirtualFileUrl(context.virtualFileUrlManager),
                                                                             null, source, context)
    val newEntities = serializer.loadEntities(reader, errorReporter, context.virtualFileUrlManager)
    serializer.checkAndAddToBuilder(builder, orphanage, newEntities.data)
    newEntities.exception?.let { throw it }
  }

  fun createProjectSerializers(configLocation: JpsProjectConfigLocation,
                                       externalStoragePath: Path,
                                       context: SerializationContext): JpsProjectSerializers {
    val externalStorageRoot = externalStoragePath.toVirtualFileUrl(context.virtualFileUrlManager)
    val externalStorageMapping = JpsExternalStorageMappingImpl(externalStorageRoot, configLocation)
    return when (configLocation) {
      is JpsProjectConfigLocation.FileBased -> createIprProjectSerializers(configLocation, externalStorageMapping, context)
      is JpsProjectConfigLocation.DirectoryBased -> createDirectoryProjectSerializers(configLocation, externalStorageMapping, context)
      else -> error("Unexpected state")
    }
  }

  private fun createDirectoryProjectSerializers(configLocation: JpsProjectConfigLocation.DirectoryBased,
                                                externalStorageMapping: JpsExternalStorageMapping,
                                                context: SerializationContext): JpsProjectSerializers {
    val ideaFolderUrl = configLocation.ideaFolder.url
    val directorySerializersFactories = ArrayList<JpsDirectoryEntitiesSerializerFactory<*>>()
    val librariesDirectoryUrl = "$ideaFolderUrl/libraries"
    val artifactsDirectoryUrl = "$ideaFolderUrl/artifacts"
    directorySerializersFactories += JpsLibrariesDirectorySerializerFactory(librariesDirectoryUrl)
    directorySerializersFactories += JpsArtifactsDirectorySerializerFactory(artifactsDirectoryUrl)
    val externalStorageRoot = externalStorageMapping.externalStorageRoot
    val externalStorageEnabled = isExternalStorageEnabled(context.fileContentReader, ideaFolderUrl, externalStorageRoot)
    val librariesExternalStorageFile = JpsProjectFileEntitySource.ExactFile(externalStorageRoot.append("project/libraries.xml"),
                                                                            configLocation)
    val virtualFileManager = context.virtualFileUrlManager
    val externalModuleListSerializer = ExternalModuleListSerializer(externalStorageRoot, context)

    val entityTypeSerializers: MutableList<JpsFileEntityTypeSerializer<*>> = mutableListOf(
      JpsLibrariesExternalFileSerializer(librariesExternalStorageFile, virtualFileManager.getOrCreateFromUri(librariesDirectoryUrl),
                                         context.fileInDirectorySourceNames))

    val artifactsExternalStorageFile = JpsProjectFileEntitySource.ExactFile(externalStorageRoot.append("project/artifacts.xml"),
                                                                            configLocation)
    val artifactsExternalFileSerializer = JpsArtifactsExternalFileSerializer(artifactsExternalStorageFile,
                                                                             virtualFileManager.getOrCreateFromUri(artifactsDirectoryUrl),
                                                                             context.fileInDirectorySourceNames,
                                                                             virtualFileManager)
    entityTypeSerializers += artifactsExternalFileSerializer

    return JpsProjectSerializers.createSerializers(
      entityTypeSerializers = entityTypeSerializers,
      directorySerializersFactories = directorySerializersFactories,
      moduleListSerializers = listOf(
        ModuleListSerializerImpl("$ideaFolderUrl/modules.xml", context, externalModuleListSerializer),
        externalModuleListSerializer
      ),
      configLocation = configLocation,
      context = context,
      externalStorageMapping = externalStorageMapping,
      enableExternalStorage = externalStorageEnabled
    )
  }

  private fun isExternalStorageEnabled(reader: JpsFileContentReader, ideaFolderPath: String, externalStorageRoot: VirtualFileUrl): Boolean {
    val component = reader.loadComponent("$ideaFolderPath/misc.xml", "ExternalStorageConfigurationManager") ?:
                    reader.loadComponent(externalStorageRoot.append(".idea/misc.xml").url, "ExternalStorageConfigurationManager")
    return component?.getAttributeValue("enabled") == "true"
  }

  private fun createIprProjectSerializers(configLocation: JpsProjectConfigLocation.FileBased,
                                          externalStorageMapping: JpsExternalStorageMappingImpl,
                                          context: SerializationContext): JpsProjectSerializers {
    val projectFileSource = JpsProjectFileEntitySource.ExactFile(configLocation.iprFile, configLocation)
    val projectFileUrl = projectFileSource.file
    val entityTypeSerializers = ArrayList<JpsFileEntityTypeSerializer<*>>()
    entityTypeSerializers += JpsLibrariesFileSerializer(projectFileSource, LibraryTableId.ProjectLibraryTableId)
    entityTypeSerializers += JpsArtifactsFileSerializer(projectFileUrl, projectFileSource, context.virtualFileUrlManager)
    return JpsProjectSerializers.createSerializers(
      entityTypeSerializers = entityTypeSerializers,
      directorySerializersFactories = emptyList(),
      moduleListSerializers = listOf(ModuleListSerializerImpl(projectFileUrl.url, context)),
      configLocation = configLocation,
      context = context,
      externalStorageMapping = externalStorageMapping,
      enableExternalStorage = false
    )
  }
}

