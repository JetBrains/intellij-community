// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.impl.url.toVirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
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
                               externalStorageConfigurationManager: ExternalStorageConfigurationManager? = null,
                               fileInDirectorySourceNames: FileInDirectorySourceNames = FileInDirectorySourceNames.empty()): JpsProjectSerializers {
    return createProjectEntitiesSerializers(configLocation, reader, externalStoragePath, serializeArtifacts, virtualFileManager,
                                            externalStorageConfigurationManager, fileInDirectorySourceNames)
  }

  @TestOnly
  fun loadProject(configLocation: JpsProjectConfigLocation, builder: WorkspaceEntityStorageBuilder,
                  externalStoragePath: Path, errorReporter: ErrorReporter, virtualFileManager: VirtualFileUrlManager): JpsProjectSerializers {
    val reader = CachingJpsFileContentReader(configLocation.baseDirectoryUrlString)
    val data = createProjectEntitiesSerializers(configLocation, reader, externalStoragePath, true, virtualFileManager)
    data.loadAll(reader, builder, errorReporter)
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
      is JpsProjectConfigLocation.FileBased -> createIprProjectSerializers(configLocation, reader, externalStorageMapping, serializeArtifacts, virtualFileManager, fileInDirectorySourceNames)
      is JpsProjectConfigLocation.DirectoryBased -> createDirectoryProjectSerializers(configLocation, reader, externalStorageMapping,
                                                                                      serializeArtifacts, virtualFileManager,
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
    val librariesExternalStorageFile = JpsFileEntitySource.ExactFile(externalStorageRoot.append("project/libraries.xml"),
                                                                     configLocation)
    val externalModuleListSerializer = ExternalModuleListSerializer(externalStorageRoot, virtualFileManager)
    return JpsProjectSerializers.createSerializers(
      entityTypeSerializers = listOf(JpsLibrariesExternalFileSerializer(librariesExternalStorageFile, internalLibrariesDirUrl)),
      directorySerializersFactories = directorySerializersFactories,
      moduleListSerializers = listOf(
        ModuleListSerializerImpl("$projectDirUrl/.idea/modules.xml", virtualFileManager, externalModuleListSerializer,
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

  private fun isExternalStorageEnabled(reader: JpsFileContentReader, projectDirUrl: String): Boolean {
    val component = reader.loadComponent("$projectDirUrl/.idea/misc.xml", "ExternalStorageConfigurationManager")
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

  fun createEntitySourceForModule(project: Project, baseModuleDir: VirtualFileUrl, externalSource: ProjectModelExternalSource?): EntitySource {
    val location = getJpsProjectConfigLocation(project) ?: return NonPersistentEntitySource
    val internalFile = JpsFileEntitySource.FileInDirectory(baseModuleDir, location)
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }

  fun createEntitySourceForProjectLibrary(project: Project, externalSource: ProjectModelExternalSource?): EntitySource {
    val location = getJpsProjectConfigLocation(project) ?: return NonPersistentEntitySource
    val internalFile = createJpsEntitySourceForProjectLibrary(location)
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }

  fun createJpsEntitySourceForProjectLibrary(configLocation: JpsProjectConfigLocation) = when (configLocation) {
    is JpsProjectConfigLocation.DirectoryBased ->
      JpsFileEntitySource.FileInDirectory(configLocation.projectDir.append(".idea/libraries"), configLocation)
    is JpsProjectConfigLocation.FileBased -> JpsFileEntitySource.ExactFile(configLocation.iprFile, configLocation)
  }
}

internal fun loadStorageFile(xmlFile: Path, pathMacroManager: PathMacroManager): Map<String, Element> {
  val rootElement = JDOMUtil.load(xmlFile)
  if (Strings.endsWith(xmlFile.toString(), ".iml")) {
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
  return FileStorageCoreUtil.load(rootElement, pathMacroManager, true)
}

fun levelToLibraryTableId(level: String) = when (level) {
  JpsLibraryTableSerializer.MODULE_LEVEL -> error("this method isn't supposed to be used for module-level libraries")
  JpsLibraryTableSerializer.PROJECT_LEVEL -> LibraryTableId.ProjectLibraryTableId
  else -> LibraryTableId.GlobalLibraryTableId(level)
}