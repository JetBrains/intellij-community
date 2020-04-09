package com.intellij.workspace.jps

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspace.api.LibraryTableId
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.append
import com.intellij.workspace.api.toVirtualFileUrl
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.JpsProjectStoragePlace
import org.jdom.Element
import java.io.File

object JpsProjectEntitiesLoader {
  /**
   * [serializeArtifacts] specifies whether artifacts should be serialized or not. We need this until a legacy bridge implementation for
   * ArtifactManager is provided.
   */
  fun createProjectSerializers(storagePlace: JpsProjectStoragePlace,
                               reader: JpsFileContentReader,
                               serializeArtifacts: Boolean): JpsProjectSerializers {
    return createProjectEntitiesSerializers(storagePlace, serializeArtifacts, reader)
  }

  fun loadProject(storagePlace: JpsProjectStoragePlace, builder: TypedEntityStorageBuilder): JpsProjectSerializers {
    val reader = CachingJpsFileContentReader(storagePlace.baseDirectoryUrlString)
    val data = createProjectEntitiesSerializers(storagePlace, true, reader)
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
    val reader = CachingJpsFileContentReader(storagePlace.baseDirectoryUrlString)
    val serializer = ModuleSerializersFactory.createModuleEntitiesSerializer(moduleFile.toVirtualFileUrl(), source)
    serializer.loadEntities(builder, reader)
  }

  private fun createProjectEntitiesSerializers(storagePlace: JpsProjectStoragePlace,
                                               serializeArtifacts: Boolean,
                                               reader: JpsFileContentReader): JpsProjectSerializers {
    return when (storagePlace) {
      is JpsProjectStoragePlace.FileBased -> createIprProjectSerializers(storagePlace, serializeArtifacts, reader)
      is JpsProjectStoragePlace.DirectoryBased -> createDirectoryProjectSerializers(storagePlace, serializeArtifacts, reader)
    }
  }

  private fun createDirectoryProjectSerializers(storagePlace: JpsProjectStoragePlace.DirectoryBased,
                                                serializeArtifacts: Boolean,
                                                reader: JpsFileContentReader): JpsProjectSerializers {
    val projectDirUrl = storagePlace.projectDir.url
    val directorySerializersFactories = ArrayList<JpsDirectoryEntitiesSerializerFactory<*>>()
    directorySerializersFactories += JpsLibrariesDirectorySerializerFactory("$projectDirUrl/.idea/libraries")
    if (serializeArtifacts) {
      directorySerializersFactories += JpsArtifactsDirectorySerializerFactory("$projectDirUrl/.idea/artifacts")
    }
    return JpsProjectSerializers.createSerializers(
      entityTypeSerializers = emptyList(),
      directorySerializersFactories = directorySerializersFactories,
      fileSerializerFactories = listOf(ModuleSerializersFactory("$projectDirUrl/.idea/modules.xml")),
      storagePlace = storagePlace,
      reader = reader
    )
  }

  private fun createIprProjectSerializers(storagePlace: JpsProjectStoragePlace.FileBased,
                                          serializeArtifacts: Boolean,
                                          reader: JpsFileContentReader): JpsProjectSerializers {
    val projectFileSource = JpsFileEntitySource.ExactFile(storagePlace.iprFile, storagePlace)
    val projectFileUrl = projectFileSource.file
    val entityTypeSerializers = ArrayList<JpsFileEntityTypeSerializer<*>>()
    entityTypeSerializers += JpsLibrariesFileSerializer(projectFileUrl, projectFileSource, LibraryTableId.ProjectLibraryTableId)
    if (serializeArtifacts) {
      entityTypeSerializers += JpsArtifactsFileSerializer(projectFileUrl, projectFileSource)
    }
    return JpsProjectSerializers.createSerializers(
      entityTypeSerializers = entityTypeSerializers,
      directorySerializersFactories = emptyList(),
      fileSerializerFactories = listOf(ModuleSerializersFactory(projectFileUrl.url)),
      storagePlace = storagePlace,
      reader = reader
    )
  }

  fun createJpsEntitySourceForLibrary(storagePlace: JpsProjectStoragePlace) = when (storagePlace) {
    is JpsProjectStoragePlace.DirectoryBased -> JpsFileEntitySource.FileInDirectory(storagePlace.projectDir.append(".idea/libraries"), storagePlace)
    is JpsProjectStoragePlace.FileBased -> JpsFileEntitySource.ExactFile(storagePlace.iprFile, storagePlace)
  }
}

/**
 * @see com.intellij.core.CoreProjectLoader.loadStorageFile
 */
internal fun loadStorageFile(xmlFile: File, pathMacroManager: PathMacroManager) =
  FileStorageCoreUtil.load(JDOMUtil.load(xmlFile.toPath()), pathMacroManager) as Map<String, Element>
