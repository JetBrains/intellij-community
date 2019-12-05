package com.intellij.workspace.jps

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspace.api.LibraryTableId
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.toVirtualFileUrl
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.JpsProjectStoragePlace
import org.jdom.Element
import java.io.File

object JpsProjectEntitiesLoader {
  fun createProjectSerializers(storagePlace: JpsProjectStoragePlace, reader: JpsFileContentReader): JpsEntitiesSerializationData {
    return createProjectEntitiesSerializers(storagePlace).createSerializers(reader)
  }

  fun loadProject(storagePlace: JpsProjectStoragePlace, builder: TypedEntityStorageBuilder): JpsEntitiesSerializationData {
    val mainFactories = createProjectEntitiesSerializers(storagePlace)
    val reader = CachingJpsFileContentReader(storagePlace.baseDirectoryUrl)
    val data = mainFactories.createSerializers(reader)
    data.loadAll(reader, builder)
    return data
  }

  fun loadModule(moduleFile: File, storagePlace: JpsProjectStoragePlace, builder: TypedEntityStorageBuilder) {
    val source = JpsFileEntitySource.FileInDirectory(moduleFile.parentFile.toVirtualFileUrl(), storagePlace)
    loadModule(moduleFile, source, storagePlace, builder)
  }

  internal fun loadModule(moduleFile: File,
                          source: JpsFileEntitySource.FileInDirectory,
                          storagePlace: JpsProjectStoragePlace,
                          builder: TypedEntityStorageBuilder) {
    val mainFactories = createProjectEntitiesSerializers(storagePlace)
    val reader = CachingJpsFileContentReader(storagePlace.baseDirectoryUrl)
    val moduleSerializerFactory = mainFactories.fileSerializerFactories.filterIsInstance<ModuleSerializersFactory>().single()

    val serializer = moduleSerializerFactory.createSerializer(source, moduleFile.toVirtualFileUrl())
    serializer.loadEntities(builder, reader)
  }

  private fun createProjectEntitiesSerializers(storagePlace: JpsProjectStoragePlace): JpsEntitiesSerializerFactories {
    return when (storagePlace) {
      is JpsProjectStoragePlace.FileBased -> createIprProjectSerializers(storagePlace)
      is JpsProjectStoragePlace.DirectoryBased -> createDirectoryProjectSerializers(storagePlace)
    }
  }

  private fun createDirectoryProjectSerializers(storagePlace: JpsProjectStoragePlace.DirectoryBased): JpsEntitiesSerializerFactories {
    val projectDirUrl = storagePlace.projectDir.url
    return JpsEntitiesSerializerFactories(entityTypeSerializers = emptyList(),
                                          directorySerializersFactories = listOf(JpsLibrariesDirectorySerializerFactory("$projectDirUrl/.idea/libraries"),
                                                                                 JpsArtifactsDirectorySerializerFactory(
                                                                                   "$projectDirUrl/.idea/artifacts")),
                                          fileSerializerFactories = listOf(ModuleSerializersFactory("$projectDirUrl/.idea/modules.xml")),
                                          storagePlace = storagePlace)
  }

  private fun createIprProjectSerializers(storagePlace: JpsProjectStoragePlace.FileBased): JpsEntitiesSerializerFactories {
    val projectFileSource = JpsFileEntitySource.ExactFile(storagePlace.iprFile, storagePlace)
    val projectFileUrl = projectFileSource.file
    return JpsEntitiesSerializerFactories(listOf(JpsLibrariesFileSerializer(projectFileUrl, projectFileSource, LibraryTableId.ProjectLibraryTableId),
                                                 JpsArtifactsFileSerializer(projectFileUrl, projectFileSource)),
                                          directorySerializersFactories = emptyList(),
                                          fileSerializerFactories = listOf(ModuleSerializersFactory(projectFileUrl.url)),
                                          storagePlace = storagePlace)
  }
}

/**
 * @see com.intellij.core.CoreProjectLoader.loadStorageFile
 */
internal fun loadStorageFile(xmlFile: File, pathMacroManager: PathMacroManager) =
  FileStorageCoreUtil.load(JDOMUtil.load(xmlFile.toPath()), pathMacroManager) as Map<String, Element>
