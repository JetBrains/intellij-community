// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.storage.*
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.nio.file.Path

object JpsProjectEntitiesLoader {
  /**
   * [serializeArtifacts] specifies whether artifacts should be serialized or not. We need this until a legacy bridge implementation for
   * ArtifactManager is provided.
   */
  fun createProjectSerializers(configLocation: JpsProjectConfigLocation,
                               reader: JpsFileContentReader,
                               externalStoragePath: Path,
                               serializeArtifacts: Boolean,
                               virtualFileManager: VirtualFileUrlManager,
                               externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null): JpsProjectSerializers {
    return createProjectEntitiesSerializers(configLocation, reader, externalStoragePath, serializeArtifacts, virtualFileManager,
                                            externalStorageConfigurationManager)
  }

  @TestOnly
  fun loadProject(configLocation: JpsProjectConfigLocation, builder: WorkspaceEntityStorageBuilder,
                  externalStoragePath: Path, virtualFileManager: VirtualFileUrlManager): JpsProjectSerializers {
    val reader = CachingJpsFileContentReader(configLocation.baseDirectoryUrlString)
    val data = createProjectEntitiesSerializers(configLocation, reader, externalStoragePath, true, virtualFileManager)
    data.loadAll(reader, builder)
    return data
  }

  fun loadModule(moduleFile: File, configLocation: JpsProjectConfigLocation, builder: WorkspaceEntityStorageBuilder, virtualFileManager: VirtualFileUrlManager) {
    val source = JpsFileEntitySource.FileInDirectory(moduleFile.parentFile.toVirtualFileUrl(virtualFileManager), configLocation)
    loadModule(moduleFile, source, configLocation, builder, virtualFileManager)
  }

  internal fun loadModule(moduleFile: File,
                          source: JpsFileEntitySource.FileInDirectory,
                          configLocation: JpsProjectConfigLocation,
                          builder: WorkspaceEntityStorageBuilder,
                          virtualFileManager: VirtualFileUrlManager) {
    val reader = CachingJpsFileContentReader(configLocation.baseDirectoryUrlString)
    val serializer = ModuleListSerializerImpl.createModuleEntitiesSerializer(moduleFile.toVirtualFileUrl(virtualFileManager), source)
    serializer.loadEntities(builder, reader, virtualFileManager)
  }

  private fun createProjectEntitiesSerializers(configLocation: JpsProjectConfigLocation,
                                               reader: JpsFileContentReader,
                                               externalStoragePath: Path,
                                               serializeArtifacts: Boolean,
                                               virtualFileManager: VirtualFileUrlManager,
                                               externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null): JpsProjectSerializers {
    val externalStorageRoot = externalStoragePath.toVirtualFileUrl(virtualFileManager)
    val externalStorageMapping = JpsExternalStorageMappingImpl(externalStorageRoot, configLocation)
    return when (configLocation) {
      is JpsProjectConfigLocation.FileBased -> createIprProjectSerializers(configLocation, reader, externalStorageMapping, serializeArtifacts, virtualFileManager)
      is JpsProjectConfigLocation.DirectoryBased -> createDirectoryProjectSerializers(configLocation, reader, externalStorageMapping,
                                                                                      serializeArtifacts, virtualFileManager,
                                                                                      externalStorageConfigurationManager)
    }
  }

  private fun createDirectoryProjectSerializers(configLocation: JpsProjectConfigLocation.DirectoryBased,
                                                reader: JpsFileContentReader,
                                                externalStorageMapping: JpsExternalStorageMapping,
                                                serializeArtifacts: Boolean,
                                                virtualFileManager: VirtualFileUrlManager,
                                                externalStorageConfigurationManager: ExternalStorageConfigurationManager?): JpsProjectSerializers {
    val projectDirUrl = configLocation.projectDir.url
    val directorySerializersFactories = ArrayList<JpsDirectoryEntitiesSerializerFactory<*>>()
    val librariesDirectoryUrl = "$projectDirUrl/.idea/libraries"
    directorySerializersFactories += JpsLibrariesDirectorySerializerFactory(librariesDirectoryUrl)
    if (serializeArtifacts) {
      directorySerializersFactories += JpsArtifactsDirectorySerializerFactory("$projectDirUrl/.idea/artifacts")
    }
    val externalStorageRoot = externalStorageMapping.externalStorageRoot
    val internalLibrariesDirUrl = virtualFileManager.fromUrl(librariesDirectoryUrl)
    val externalStorageEnabled = isExternalStorageEnabled(reader, projectDirUrl)
    val librariesExternalStorageFile = JpsFileEntitySource.ExactFile(externalStorageRoot.append("project/libraries.xml"), configLocation)
    val externalModuleListSerializer = ExternalModuleListSerializer(externalStorageRoot)
    return JpsProjectSerializers.createSerializers(
      entityTypeSerializers = listOf(JpsLibrariesExternalFileSerializer(librariesExternalStorageFile, internalLibrariesDirUrl)),
      directorySerializersFactories = directorySerializersFactories,
      moduleListSerializers = listOf(
        ModuleListSerializerImpl("$projectDirUrl/.idea/modules.xml", externalModuleListSerializer, externalStorageConfigurationManager),
        externalModuleListSerializer
      ),
      configLocation = configLocation,
      reader = reader,
      externalStorageMapping = externalStorageMapping,
      enableExternalStorage = externalStorageEnabled,
      virtualFileManager = virtualFileManager
    )
  }

  private fun isExternalStorageEnabled(reader: JpsFileContentReader, projectDirUrl: String): Boolean {
    val component = reader.loadComponent("$projectDirUrl/.idea/misc.xml", "ExternalStorageConfigurationManager")
    return component?.getAttributeValue("enabled") == "true"
  }

  private fun createIprProjectSerializers(configLocation: JpsProjectConfigLocation.FileBased,
                                          reader: JpsFileContentReader,
                                          externalStorageMapping: JpsExternalStorageMappingImpl,
                                          serializeArtifacts: Boolean,
                                          virtualFileManager: VirtualFileUrlManager): JpsProjectSerializers {
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
      moduleListSerializers = listOf(ModuleListSerializerImpl(projectFileUrl.url)),
      configLocation = configLocation,
      reader = reader,
      virtualFileManager = virtualFileManager,
      externalStorageMapping = externalStorageMapping,
      enableExternalStorage = false
    )
  }

  fun createEntitySourceForModule(project: Project, baseModuleDir: VirtualFileUrl, externalSource: ProjectModelExternalSource?): EntitySource {
    val location = project.configLocation ?: return NonPersistentEntitySource
    val internalFile = JpsFileEntitySource.FileInDirectory(baseModuleDir, location)
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }

  fun createEntitySourceForProjectLibrary(project: Project, externalSource: ProjectModelExternalSource?): EntitySource {
    val location = project.configLocation ?: return NonPersistentEntitySource
    val internalFile = createJpsEntitySourceForProjectLibrary(location)
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }

  fun createJpsEntitySourceForProjectLibrary(configLocation: JpsProjectConfigLocation) = when (configLocation) {
    is JpsProjectConfigLocation.DirectoryBased -> JpsFileEntitySource.FileInDirectory(configLocation.projectDir.append(".idea/libraries"), configLocation)
    is JpsProjectConfigLocation.FileBased -> JpsFileEntitySource.ExactFile(configLocation.iprFile, configLocation)
  }
}

internal fun loadStorageFile(xmlFile: File, pathMacroManager: PathMacroManager): Map<String, Element> {
  val rootElement = JDOMUtil.load(xmlFile.toPath())
  if (FileUtil.extensionEquals(xmlFile.path, "iml")) {
    val optionElement = Element("component").setAttribute("name", "DeprecatedModuleOptionManager")
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
