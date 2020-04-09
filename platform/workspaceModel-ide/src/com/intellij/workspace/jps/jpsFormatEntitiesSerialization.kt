package com.intellij.workspace.jps

import com.intellij.openapi.module.impl.ModulePath
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.JpsProjectStoragePlace
import org.jdom.Element
import org.jetbrains.annotations.TestOnly

/**
 * Provides read access to components in project or module configuration file. The implementation may cache the file contents to avoid
 * reading the same file multiple times.
 */
interface JpsFileContentReader {
  fun loadComponent(fileUrl: String, componentName: String): Element?
}

interface JpsFileContentWriter {
  fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?)
}

/**
 * Represents a serializer for a configuration XML file in JPS format.
 */
interface JpsFileEntitiesSerializer<E : TypedEntity> {
  val entitySource: EntitySource
  val fileUrl: VirtualFileUrl
  val mainEntityClass: Class<E>
  fun loadEntities(builder: TypedEntityStorageBuilder, reader: JpsFileContentReader)
  fun saveEntities(mainEntities: Collection<E>, entities: Map<Class<out TypedEntity>, List<TypedEntity>>, writer: JpsFileContentWriter)

  val additionalEntityTypes: List<Class<out TypedEntity>>
    get() = emptyList()
}

/**
 * Represents a serializer which is responsible for serializing all entities of the given type (e.g. libraries in *.ipr file).
 */
interface JpsFileEntityTypeSerializer<E : TypedEntity> : JpsFileEntitiesSerializer<E> {
  val entityFilter: (E) -> Boolean
    get() = { true }
}

/**
 * Represents a directory containing configuration files (e.g. .idea/libraries).
 */
interface JpsDirectoryEntitiesSerializerFactory<E : TypedEntity> {
  val directoryUrl: String
  val entityClass: Class<E>
  val entityFilter: (E) -> Boolean
    get() = { true }
  val componentName: String

  /** Returns a serializer for a file located in [directoryUrl] directory*/
  fun createSerializer(fileUrl: String, entitySource: JpsFileEntitySource.FileInDirectory): JpsFileEntitiesSerializer<E>

  fun getDefaultFileName(entity: E): String
}

/**
 * Represents a configuration file which contains references to other configuration files (e.g. .idea/modules.xml which contains references
 * to *.iml files).
 */
interface JpsFileSerializerFactory<E : TypedEntity> {
  val fileUrl: String
  val entityClass: Class<E>
  
  /** Returns serializers for individual configuration files referenced from [fileUrl] */
  fun loadFileList(reader: JpsFileContentReader): List<VirtualFileUrl>

  fun createSerializer(source: JpsFileEntitySource, fileUrl: VirtualFileUrl): JpsFileEntitiesSerializer<E>
  fun saveEntitiesList(entities: Sequence<E>, writer: JpsFileContentWriter)
  fun getFileName(entity: E): String

  fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter)
}

/**
 * Represents set of serializers for some project.
 */
interface JpsProjectSerializers {
  companion object {
    fun createSerializers(entityTypeSerializers: List<JpsFileEntityTypeSerializer<*>>,
                          directorySerializersFactories: List<JpsDirectoryEntitiesSerializerFactory<*>>,
                          fileSerializerFactories: List<JpsFileSerializerFactory<*>>,
                          storagePlace: JpsProjectStoragePlace, reader: JpsFileContentReader): JpsProjectSerializers {
      return JpsProjectSerializersImpl(directorySerializersFactories, fileSerializerFactories, reader, entityTypeSerializers, storagePlace)
    }
  }

  fun loadAll(reader: JpsFileContentReader, builder: TypedEntityStorageBuilder)

  fun reloadFromChangedFiles(change: JpsConfigurationFilesChange,
                             reader: JpsFileContentReader): Pair<Set<EntitySource>, TypedEntityStorageBuilder>

  @TestOnly
  fun saveAllEntities(storage: TypedEntityStorage, writer: JpsFileContentWriter)

  fun saveEntities(storage: TypedEntityStorage, affectedSources: Set<EntitySource>, writer: JpsFileContentWriter)
  
  fun getAllModulePaths(): List<ModulePath>
}

data class JpsConfigurationFilesChange(val addedFileUrls: Collection<String>, val removedFileUrls: Collection<String>, val changedFileUrls: Collection<String>)