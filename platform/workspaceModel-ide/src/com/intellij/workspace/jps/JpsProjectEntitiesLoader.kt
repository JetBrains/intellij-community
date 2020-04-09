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
   * [serializeFacets] specifies whether facets should be serialized or not. We need this until a legacy bridge implementation for
   * FacetManager is provided.
   */
  fun createProjectSerializers(storagePlace: JpsProjectStoragePlace,
                               reader: JpsFileContentReader,
                               serializeArtifacts: Boolean): JpsEntitiesSerializationData {
    return createProjectEntitiesSerializers(storagePlace, serializeArtifacts).createSerializers(reader)
  }

  fun loadProject(storagePlace: JpsProjectStoragePlace, builder: TypedEntityStorageBuilder): JpsEntitiesSerializationData {
    val mainFactories = createProjectEntitiesSerializers(storagePlace, true)
    val reader = CachingJpsFileContentReader(storagePlace.baseDirectoryUrlString)
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
    val mainFactories = createProjectEntitiesSerializers(storagePlace, false)
    val reader = CachingJpsFileContentReader(storagePlace.baseDirectoryUrlString)
    val moduleSerializerFactory = mainFactories.fileSerializerFactories.filterIsInstance<ModuleSerializersFactory>().single()

    val serializer = moduleSerializerFactory.createSerializer(source, moduleFile.toVirtualFileUrl())
    serializer.loadEntities(builder, reader)
  }

  private fun createProjectEntitiesSerializers(storagePlace: JpsProjectStoragePlace,
                                               serializeArtifacts: Boolean): JpsEntitiesSerializerFactories {
    return when (storagePlace) {
      is JpsProjectStoragePlace.FileBased -> createIprProjectSerializers(storagePlace, serializeArtifacts)
      is JpsProjectStoragePlace.DirectoryBased -> createDirectoryProjectSerializers(storagePlace, serializeArtifacts)
    }
  }

  private fun createDirectoryProjectSerializers(storagePlace: JpsProjectStoragePlace.DirectoryBased,
                                                serializeArtifacts: Boolean): JpsEntitiesSerializerFactories {
    val projectDirUrl = storagePlace.projectDir.url
    val directorySerializersFactories = ArrayList<JpsDirectoryEntitiesSerializerFactory<*>>()
    directorySerializersFactories += JpsLibrariesDirectorySerializerFactory("$projectDirUrl/.idea/libraries")
    if (serializeArtifacts) {
      directorySerializersFactories += JpsArtifactsDirectorySerializerFactory("$projectDirUrl/.idea/artifacts")
    }
    return JpsEntitiesSerializerFactories(entityTypeSerializers = emptyList(),
                                          directorySerializersFactories = directorySerializersFactories,
                                          fileSerializerFactories = listOf(ModuleSerializersFactory("$projectDirUrl/.idea/modules.xml")),
                                          storagePlace = storagePlace)
  }

  private fun createIprProjectSerializers(storagePlace: JpsProjectStoragePlace.FileBased,
                                          serializeArtifacts: Boolean): JpsEntitiesSerializerFactories {
    val projectFileSource = JpsFileEntitySource.ExactFile(storagePlace.iprFile, storagePlace)
    val projectFileUrl = projectFileSource.file
    val entityTypeSerializers = ArrayList<JpsFileEntityTypeSerializer<*>>()
    entityTypeSerializers += JpsLibrariesFileSerializer(projectFileUrl, projectFileSource, LibraryTableId.ProjectLibraryTableId)
    if (serializeArtifacts) {
      entityTypeSerializers += JpsArtifactsFileSerializer(projectFileUrl, projectFileSource)
    }
    return JpsEntitiesSerializerFactories(entityTypeSerializers,
                                          directorySerializersFactories = emptyList(),
                                          fileSerializerFactories = listOf(ModuleSerializersFactory(projectFileUrl.url)),
                                          storagePlace = storagePlace)
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
