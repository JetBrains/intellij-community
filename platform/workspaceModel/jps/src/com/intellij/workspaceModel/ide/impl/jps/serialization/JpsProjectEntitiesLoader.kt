// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.impl.url.toVirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

object JpsProjectEntitiesLoader {
  fun createProjectSerializers(configLocation: JpsProjectConfigLocation,
                               reader: JpsFileContentReader,
                               externalStoragePath: Path,
                               serializeArtifacts: Boolean,
                               virtualFileManager: VirtualFileUrlManager,
                               externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null,
                               fileInDirectorySourceNames: FileInDirectorySourceNames = FileInDirectorySourceNames.empty()): JpsProjectSerializers {
    return createProjectEntitiesSerializers(configLocation, reader, externalStoragePath, serializeArtifacts, virtualFileManager,
                                            externalStorageConfigurationManager,
                                            fileInDirectorySourceNames)
  }

  @TestOnly
  fun loadProject(configLocation: JpsProjectConfigLocation, builder: WorkspaceEntityStorageBuilder,
                  externalStoragePath: Path, errorReporter: ErrorReporter, virtualFileManager: VirtualFileUrlManager,
                  fileInDirectorySourceNames: FileInDirectorySourceNames = FileInDirectorySourceNames.empty(),
                  externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null): JpsProjectSerializers {
    val reader = CachingJpsFileContentReader(configLocation.baseDirectoryUrlString)
    val data = createProjectEntitiesSerializers(configLocation, reader, externalStoragePath, true, virtualFileManager,
                                                externalStorageConfigurationManager = externalStorageConfigurationManager,
                                                fileInDirectorySourceNames = fileInDirectorySourceNames)
    data.loadAll(reader, builder, errorReporter, null)
    return data
  }

  fun loadModule(moduleFile: Path, configLocation: JpsProjectConfigLocation, builder: WorkspaceEntityStorageBuilder,
                 errorReporter: ErrorReporter, virtualFileManager: VirtualFileUrlManager) {
    val source = JpsFileEntitySource.FileInDirectory(moduleFile.parent.toVirtualFileUrl(virtualFileManager), configLocation)
    loadModule(moduleFile, source, configLocation, builder, errorReporter, virtualFileManager)
  }

  internal fun loadModule(moduleFile: Path,
                          source: JpsFileEntitySource.FileInDirectory,
                          configLocation: JpsProjectConfigLocation,
                          builder: WorkspaceEntityStorageBuilder,
                          errorReporter: ErrorReporter,
                          virtualFileManager: VirtualFileUrlManager) {
    val reader = CachingJpsFileContentReader(configLocation.baseDirectoryUrlString)
    val serializer = ModuleListSerializerImpl.createModuleEntitiesSerializer(moduleFile.toVirtualFileUrl(virtualFileManager), null, source,
                                                                             virtualFileManager)
    serializer.loadEntities(builder, reader, errorReporter, virtualFileManager)
  }

  private fun createProjectEntitiesSerializers(configLocation: JpsProjectConfigLocation,
                                               reader: JpsFileContentReader,
                                               externalStoragePath: Path,
                                               serializeArtifacts: Boolean,
                                               virtualFileManager: VirtualFileUrlManager,
                                               externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null,
                                               fileInDirectorySourceNames: FileInDirectorySourceNames = FileInDirectorySourceNames.empty()): JpsProjectSerializers {
    val externalStorageRoot = externalStoragePath.toVirtualFileUrl(virtualFileManager)
    val externalStorageMapping = JpsExternalStorageMappingImpl(externalStorageRoot, configLocation)
    return when (configLocation) {
      is JpsProjectConfigLocation.FileBased -> createIprProjectSerializers(configLocation, reader, externalStorageMapping,
                                                                           serializeArtifacts, virtualFileManager,
                                                                           fileInDirectorySourceNames)
      is JpsProjectConfigLocation.DirectoryBased -> createDirectoryProjectSerializers(configLocation, reader, externalStorageMapping,
                                                                                      serializeArtifacts,
                                                                                      virtualFileManager,
                                                                                      externalStorageConfigurationManager,
                                                                                      fileInDirectorySourceNames)
    }
  }

  private fun createDirectoryProjectSerializers(configLocation: JpsProjectConfigLocation.DirectoryBased,
                                                reader: JpsFileContentReader,
                                                externalStorageMapping: JpsExternalStorageMapping,
                                                serializeArtifacts: Boolean,
                                                virtualFileManager: VirtualFileUrlManager,
                                                externalStorageConfigurationManager: ExternalStorageConfigurationManager?,
                                                fileInDirectorySourceNames: FileInDirectorySourceNames): JpsProjectSerializers {
    val ideaFolderUrl = configLocation.ideaFolder.url
    val directorySerializersFactories = ArrayList<JpsDirectoryEntitiesSerializerFactory<*>>()
    val librariesDirectoryUrl = "$ideaFolderUrl/libraries"
    val artifactsDirectoryUrl = "$ideaFolderUrl/artifacts"
    directorySerializersFactories += JpsLibrariesDirectorySerializerFactory(librariesDirectoryUrl)
    if (serializeArtifacts) {
      directorySerializersFactories += JpsArtifactsDirectorySerializerFactory(artifactsDirectoryUrl)
    }
    val externalStorageRoot = externalStorageMapping.externalStorageRoot
    val externalStorageEnabled = isExternalStorageEnabled(reader, ideaFolderUrl)
    val librariesExternalStorageFile = JpsFileEntitySource.ExactFile(externalStorageRoot.append("project/libraries.xml"), configLocation)
    val externalModuleListSerializer = ExternalModuleListSerializer(externalStorageRoot, virtualFileManager)

    val entityTypeSerializers: MutableList<JpsFileEntityTypeSerializer<*>> = mutableListOf(
      JpsLibrariesExternalFileSerializer(librariesExternalStorageFile, virtualFileManager.fromUrl(librariesDirectoryUrl),
                                         fileInDirectorySourceNames))

    if (serializeArtifacts) {
      val artifactsExternalStorageFile = JpsFileEntitySource.ExactFile(externalStorageRoot.append("project/artifacts.xml"), configLocation)
      val artifactsExternalFileSerializer = JpsArtifactsExternalFileSerializer(artifactsExternalStorageFile,
                                                                               virtualFileManager.fromUrl(artifactsDirectoryUrl),
                                                                               fileInDirectorySourceNames,
                                                                               virtualFileManager)
      entityTypeSerializers += artifactsExternalFileSerializer
    }

    return JpsProjectSerializers.createSerializers(
      entityTypeSerializers = entityTypeSerializers,
      directorySerializersFactories = directorySerializersFactories,
      moduleListSerializers = listOf(
        ModuleListSerializerImpl("$ideaFolderUrl/modules.xml", virtualFileManager, externalModuleListSerializer,
                                 externalStorageConfigurationManager),
        externalModuleListSerializer
      ),
      configLocation = configLocation,
      reader = reader,
      externalStorageMapping = externalStorageMapping,
      enableExternalStorage = externalStorageEnabled,
      virtualFileManager = virtualFileManager,
      fileInDirectorySourceNames = fileInDirectorySourceNames
    )
  }

  private fun isExternalStorageEnabled(reader: JpsFileContentReader, ideaFolderPath: String): Boolean {
    val component = reader.loadComponent("$ideaFolderPath/misc.xml", "ExternalStorageConfigurationManager")
    return component?.getAttributeValue("enabled") == "true"
  }

  private fun createIprProjectSerializers(configLocation: JpsProjectConfigLocation.FileBased,
                                          reader: JpsFileContentReader,
                                          externalStorageMapping: JpsExternalStorageMappingImpl,
                                          serializeArtifacts: Boolean,
                                          virtualFileManager: VirtualFileUrlManager,
                                          fileInDirectorySourceNames: FileInDirectorySourceNames): JpsProjectSerializers {
    val projectFileSource = JpsFileEntitySource.ExactFile(configLocation.iprFile, configLocation)
    val projectFileUrl = projectFileSource.file
    val entityTypeSerializers = ArrayList<JpsFileEntityTypeSerializer<*>>()
    entityTypeSerializers += JpsLibrariesFileSerializer(projectFileSource, LibraryTableId.ProjectLibraryTableId)
    if (serializeArtifacts) {
      entityTypeSerializers += JpsArtifactsFileSerializer(projectFileUrl, projectFileSource, virtualFileManager)
    }
    return JpsProjectSerializers.createSerializers(
      entityTypeSerializers = entityTypeSerializers,
      directorySerializersFactories = emptyList(),
      moduleListSerializers = listOf(ModuleListSerializerImpl(projectFileUrl.url, virtualFileManager)),
      configLocation = configLocation,
      reader = reader,
      virtualFileManager = virtualFileManager,
      externalStorageMapping = externalStorageMapping,
      enableExternalStorage = false,
      fileInDirectorySourceNames = fileInDirectorySourceNames
    )
  }
}

internal fun loadStorageFile(xmlFile: Path, pathMacroManager: PathMacroManager): Map<String, Element> {
  val rootElement = JDOMUtil.load(xmlFile)
  if (Strings.endsWith(xmlFile.toString(), ".iml")) {
    val optionElement = Element("component").setAttribute("name", DEPRECATED_MODULE_MANAGER_COMPONENT_NAME)
    val iterator = rootElement.attributes.iterator()
    for (attribute in iterator) {
      if (attribute.name != "version") {
        iterator.remove()
        optionElement.addContent(Element("option").setAttribute("key", attribute.name).setAttribute("value", attribute.value))
      }
    }
    rootElement.addContent(optionElement)
  }
  return FileStorageCoreUtil.load(rootElement, pathMacroManager)
}

