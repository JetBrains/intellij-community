package com.intellij.workspace.jps

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.*
import org.jdom.Element
import java.io.File
import java.nio.file.Path

object JpsProjectEntitiesLoader {
  /**
   * [serializeArtifacts] specifies whether artifacts should be serialized or not. We need this until a legacy bridge implementation for
   * ArtifactManager is provided.
   */
  fun createProjectSerializers(storagePlace: JpsProjectStoragePlace,
                               reader: JpsFileContentReader,
                               externalStoragePath: Path,
                               serializeArtifacts: Boolean): JpsProjectSerializers {
    return createProjectEntitiesSerializers(storagePlace, reader, externalStoragePath, serializeArtifacts)
  }

  fun loadProject(storagePlace: JpsProjectStoragePlace, builder: TypedEntityStorageBuilder,
                  externalStoragePath: Path): JpsProjectSerializers {
    val reader = CachingJpsFileContentReader(storagePlace.baseDirectoryUrlString)
    val data = createProjectEntitiesSerializers(storagePlace, reader, externalStoragePath, true)
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
                                               reader: JpsFileContentReader,
                                               externalStoragePath: Path,
                                               serializeArtifacts: Boolean): JpsProjectSerializers {
    val externalStorageRoot = externalStoragePath.toVirtualFileUrl()
    val externalStorageMapping = JpsExternalStorageMappingImpl(externalStorageRoot, storagePlace)
    return when (storagePlace) {
      is JpsProjectStoragePlace.FileBased -> createIprProjectSerializers(storagePlace, reader, externalStorageMapping, serializeArtifacts)
      is JpsProjectStoragePlace.DirectoryBased -> createDirectoryProjectSerializers(storagePlace, reader, externalStorageMapping, serializeArtifacts)
    }
  }

  private fun createDirectoryProjectSerializers(storagePlace: JpsProjectStoragePlace.DirectoryBased,
                                                reader: JpsFileContentReader,
                                                externalStorageMapping: JpsExternalStorageMapping,
                                                serializeArtifacts: Boolean): JpsProjectSerializers {
    val projectDirUrl = storagePlace.projectDir.url
    val directorySerializersFactories = ArrayList<JpsDirectoryEntitiesSerializerFactory<*>>()
    val librariesDirectoryUrl = "$projectDirUrl/.idea/libraries"
    directorySerializersFactories += JpsLibrariesDirectorySerializerFactory(librariesDirectoryUrl)
    if (serializeArtifacts) {
      directorySerializersFactories += JpsArtifactsDirectorySerializerFactory("$projectDirUrl/.idea/artifacts")
    }
    val externalStorageRoot = externalStorageMapping.externalStorageRoot
    val internalLibrariesDirUrl = VirtualFileUrlManager.fromUrl(librariesDirectoryUrl)
    val librariesExternalStorageFile = JpsFileEntitySource.ExactFile(externalStorageRoot.append("project/libraries.xml"), storagePlace)
    return JpsProjectSerializers.createSerializers(
      entityTypeSerializers = listOf(JpsLibrariesExternalFileSerializer(librariesExternalStorageFile, internalLibrariesDirUrl)),
      directorySerializersFactories = directorySerializersFactories,
      fileSerializerFactories = listOf(
        ModuleSerializersFactory("$projectDirUrl/.idea/modules.xml"),
        ExternalModuleSerializersFactory(externalStorageRoot)
      ),
      storagePlace = storagePlace,
      reader = reader,
      externalStorageMapping = externalStorageMapping
    )
  }

  private fun createIprProjectSerializers(storagePlace: JpsProjectStoragePlace.FileBased,
                                          reader: JpsFileContentReader,
                                          externalStorageMapping: JpsExternalStorageMappingImpl,
                                          serializeArtifacts: Boolean): JpsProjectSerializers {
    val projectFileSource = JpsFileEntitySource.ExactFile(storagePlace.iprFile, storagePlace)
    val projectFileUrl = projectFileSource.file
    val entityTypeSerializers = ArrayList<JpsFileEntityTypeSerializer<*>>()
    entityTypeSerializers += JpsLibrariesFileSerializer(projectFileSource, LibraryTableId.ProjectLibraryTableId)
    if (serializeArtifacts) {
      entityTypeSerializers += JpsArtifactsFileSerializer(projectFileUrl, projectFileSource)
    }
    return JpsProjectSerializers.createSerializers(
      entityTypeSerializers = entityTypeSerializers,
      directorySerializersFactories = emptyList(),
      fileSerializerFactories = listOf(ModuleSerializersFactory(projectFileUrl.url)),
      storagePlace = storagePlace,
      reader = reader,
      externalStorageMapping = externalStorageMapping
    )
  }

  fun createEntitySourceForModule(project: Project, baseModuleDir: VirtualFileUrl, externalSource: ProjectModelExternalSource?): EntitySource {
    val place = project.storagePlace ?: return NonPersistentEntitySource
    val internalFile = JpsFileEntitySource.FileInDirectory(baseModuleDir, place)
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }

  fun createEntitySourceForProjectLibrary(project: Project, externalSource: ProjectModelExternalSource?): EntitySource {
    val place = project.storagePlace ?: return NonPersistentEntitySource
    val internalFile = createJpsEntitySourceForProjectLibrary(place)
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }

  fun createJpsEntitySourceForProjectLibrary(storagePlace: JpsProjectStoragePlace) = when (storagePlace) {
    is JpsProjectStoragePlace.DirectoryBased -> JpsFileEntitySource.FileInDirectory(storagePlace.projectDir.append(".idea/libraries"), storagePlace)
    is JpsProjectStoragePlace.FileBased -> JpsFileEntitySource.ExactFile(storagePlace.iprFile, storagePlace)
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
