// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.PathMacroMap
import com.intellij.platform.workspaceModel.jps.JpsFileEntitySource
import com.intellij.platform.workspaceModel.jps.JpsProjectConfigLocation
import com.intellij.platform.workspaceModel.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspaceModel.jps.serialization.SerializationContext
import com.intellij.platform.workspaceModel.jps.serialization.impl.ModulePath
import com.intellij.workspaceModel.ide.UnloadedModulesNameHolder
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly

/**
 * Provides read access to components in project or module configuration file. The implementation may cache the file contents to avoid
 * reading the same file multiple times.
 */
interface JpsFileContentReader {
  fun loadComponent(fileUrl: String, componentName: String, customModuleFilePath: String? = null): Element?
  fun getExpandMacroMap(fileUrl: String): ExpandMacroToPathMap
}

interface JpsFileContentWriter {
  fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?)
  fun getReplacePathMacroMap(fileUrl: String): PathMacroMap
}

/**
 * Represents a serializer for a configuration XML file in JPS format.
 */
interface JpsFileEntitiesSerializer<E : WorkspaceEntity> {
  val internalEntitySource: JpsFileEntitySource
  val fileUrl: VirtualFileUrl
  val mainEntityClass: Class<E>

  /**
   * This method reads configuration files and creates entities that are not added to any builder.
   *
   * These entities can be just added to builder, but it's suggested to do it using [checkAndAddToBuilder] because this method
   *   implements additional actions on adding (e.g. reports error when trying to add a library that already exists).
   */
  fun loadEntities(reader: JpsFileContentReader,
                   errorReporter: ErrorReporter,
                   virtualFileManager: VirtualFileUrlManager): LoadingResult<Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity>>>
  fun checkAndAddToBuilder(builder: MutableEntityStorage, orphanage: MutableEntityStorage, newEntities: Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity>>)

  fun saveEntities(mainEntities: Collection<E>,
                   entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                   storage: EntityStorage,
                   writer: JpsFileContentWriter)

  val additionalEntityTypes: List<Class<out WorkspaceEntity>>
    get() = emptyList()
}

/**
 * Represents a serializer which is responsible for serializing all entities of the given type (e.g. libraries in *.ipr file).
 */
interface JpsFileEntityTypeSerializer<E : WorkspaceEntity> : JpsFileEntitiesSerializer<E> {
  val isExternalStorage: Boolean
  val entityFilter: (E) -> Boolean
    get() = { true }
  fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter)
}

/**
 * Represents a directory containing configuration files (e.g. .idea/libraries).
 */
interface JpsDirectoryEntitiesSerializerFactory<E : WorkspaceEntity> {
  val directoryUrl: String
  val entityClass: Class<E>
  val entityFilter: (E) -> Boolean
    get() = { true }
  val componentName: String

  /** Returns a serializer for a file located in [directoryUrl] directory*/
  fun createSerializer(fileUrl: String, entitySource: JpsProjectFileEntitySource.FileInDirectory, virtualFileManager: VirtualFileUrlManager): JpsFileEntitiesSerializer<E>

  fun getDefaultFileName(entity: E): String

  fun changeEntitySourcesToDirectoryBasedFormat(builder: MutableEntityStorage, configLocation: JpsProjectConfigLocation)
}

/**
 * Represents a configuration file which contains references to individual modules files (an ipr file, or .idea/modules.xml, or modules.xml
 * under external_build_system).
 */
interface JpsModuleListSerializer {
  val fileUrl: String
  val isExternalStorage: Boolean
  val entitySourceFilter: (EntitySource) -> Boolean
    get() = { true }

  fun loadFileList(reader: JpsFileContentReader, virtualFileManager: VirtualFileUrlManager): List<Pair<VirtualFileUrl, String?>>
  fun createSerializer(internalSource: JpsFileEntitySource, fileUrl: VirtualFileUrl, moduleGroup: String?): JpsFileEntitiesSerializer<ModuleEntity>
  fun saveEntitiesList(entities: Sequence<ModuleEntity>, writer: JpsFileContentWriter)
  fun getFileName(entity: ModuleEntity): String
  fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter)
}

/**
 * Represents set of serializers for some project.
 */
interface JpsProjectSerializers {
  companion object {
    fun createSerializers(entityTypeSerializers: List<JpsFileEntityTypeSerializer<*>>,
                          directorySerializersFactories: List<JpsDirectoryEntitiesSerializerFactory<*>>,
                          moduleListSerializers: List<JpsModuleListSerializer>,
                          configLocation: JpsProjectConfigLocation,
                          context: SerializationContext,
                          externalStorageMapping: JpsExternalStorageMapping,
                          enableExternalStorage: Boolean): JpsProjectSerializers {
      return JpsProjectSerializersImpl(directorySerializersFactories, moduleListSerializers, context, entityTypeSerializers, configLocation,
                                       externalStorageMapping, enableExternalStorage)
    }
  }

  suspend fun loadAll(reader: JpsFileContentReader,
                      builder: MutableEntityStorage,
                      orphanageBuilder: MutableEntityStorage,
                      unloadedEntityBuilder: MutableEntityStorage,
                      unloadedModuleNames: UnloadedModulesNameHolder,
                      errorReporter: ErrorReporter): List<EntitySource>

  fun reloadFromChangedFiles(change: JpsConfigurationFilesChange,
                             reader: JpsFileContentReader,
                             unloadedModuleNames: UnloadedModulesNameHolder,
                             errorReporter: ErrorReporter): ReloadingResult

  @TestOnly
  fun saveAllEntities(storage: EntityStorage, writer: JpsFileContentWriter)

  fun saveEntities(storage: EntityStorage, unloadedEntityStorage: EntityStorage, affectedSources: Set<EntitySource>,
                   writer: JpsFileContentWriter)
  
  fun getAllModulePaths(): List<ModulePath>

  fun changeEntitySourcesToDirectoryBasedFormat(builder: MutableEntityStorage)
}

data class ReloadingResult(
  val builder: MutableEntityStorage,
  val orphanageBuilder: MutableEntityStorage,
  val unloadedEntityBuilder: MutableEntityStorage,
  val affectedSources: Set<EntitySource>
)

interface ErrorReporter {
  fun reportError(message: @Nls String, file: VirtualFileUrl)
}

data class JpsConfigurationFilesChange(val addedFileUrls: Collection<String>,
                                       val removedFileUrls: Collection<String>,
                                       val changedFileUrls: Collection<String>) {
  override fun toString(): String {
    val description = StringBuilder()
    description.append("JpsConfigurationFilesChange:\n")
    description.append(" added (").append(addedFileUrls.size).append(")\n")
    addedFileUrls.forEach { description.append("  ").append(it).append("\n") }
    description.append(" removed (").append(removedFileUrls.size).append(")\n")
    removedFileUrls.forEach { description.append("  ").append(it).append("\n") }
    description.append(" changed (").append(changedFileUrls.size).append(")\n")
    changedFileUrls.forEach { description.append("  ").append(it).append("\n") }
    return description.toString()
  }
}