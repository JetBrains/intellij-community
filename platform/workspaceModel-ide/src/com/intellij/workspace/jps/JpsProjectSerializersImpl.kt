// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.jps

import com.intellij.application.options.ReplacePathToMacroMap
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.module.impl.ModulePath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Function
import com.intellij.util.PathUtil
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.MultiMap
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.JpsImportedEntitySource
import com.intellij.workspace.ide.JpsProjectConfigLocation
import gnu.trove.TIntObjectHashMap
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class JpsProjectSerializersImpl(directorySerializersFactories: List<JpsDirectoryEntitiesSerializerFactory<*>>,
                                fileSerializerFactories: List<JpsFileSerializerFactory<*>>,
                                reader: JpsFileContentReader,
                                private val entityTypeSerializers: List<JpsFileEntityTypeSerializer<*>>,
                                private val configLocation: JpsProjectConfigLocation,
                                private val externalStorageMapping: JpsExternalStorageMapping,
                                private val virtualFileManager: VirtualFileUrlManager) : JpsProjectSerializers {
  internal val serializerToFileFactory = BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsFileSerializerFactory<*>>()
  internal val serializerToDirectoryFactory = BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsDirectoryEntitiesSerializerFactory<*>>()
  private val internalSourceToExternal = HashMap<JpsFileEntitySource, JpsFileEntitySource>()
  internal val fileSerializersByUrl = MultiMap.create<String, JpsFileEntitiesSerializer<*>>()
  internal val fileIdToFileName = TIntObjectHashMap<String>()

  init {
    for (factory in directorySerializersFactories) {
      createDirectorySerializers(factory).associateWithTo(serializerToDirectoryFactory) { factory }
    }
    for (factory in fileSerializerFactories) {
      val fileList = factory.loadFileList(reader, virtualFileManager)
      fileList
        .map { factory.createSerializer(createFileInDirectorySource(it.parent!!, it.file!!.name), it) }
        .associateWithTo(serializerToFileFactory) { factory }
    }

    val allFileSerializers = entityTypeSerializers + serializerToDirectoryFactory.keys + serializerToFileFactory.keys
    allFileSerializers.forEach {
      fileSerializersByUrl.putValue(it.fileUrl.url, it)
    }
  }

  internal val directorySerializerFactoriesByUrl = directorySerializersFactories.associateBy { it.directoryUrl }
  internal val fileSerializerFactoriesByUrl = fileSerializerFactories.associateBy { it.fileUrl }

  private fun createFileInDirectorySource(directoryUrl: VirtualFileUrl, fileName: String): JpsFileEntitySource.FileInDirectory {
    val source = JpsFileEntitySource.FileInDirectory(directoryUrl, configLocation)
    fileIdToFileName.put(source.fileNameId, fileName)
    return source
  }

  private fun createDirectorySerializers(factory: JpsDirectoryEntitiesSerializerFactory<*>): List<JpsFileEntitiesSerializer<*>> {
    val files = JpsPathUtil.urlToFile(factory.directoryUrl).listFiles { file: File -> file.extension == "xml" && file.isFile }
                ?: return emptyList()
    return files.map {
      factory.createSerializer("${factory.directoryUrl}/${it.name}",
                               createFileInDirectorySource(virtualFileManager.fromUrl(factory.directoryUrl), it.name),
                               virtualFileManager)
    }
  }

  override fun reloadFromChangedFiles(change: JpsConfigurationFilesChange,
                                      reader: JpsFileContentReader): Pair<Set<EntitySource>, TypedEntityStorageBuilder> {
    val obsoleteSerializers = ArrayList<JpsFileEntitiesSerializer<*>>()
    val newFileSerializers = ArrayList<JpsFileEntitiesSerializer<*>>()
    for (addedFileUrl in change.addedFileUrls) {
      val factory = directorySerializerFactoriesByUrl[PathUtil.getParentPath(addedFileUrl)]
      val newFileSerializer = factory?.createSerializer(addedFileUrl, createFileInDirectorySource(
        virtualFileManager.fromUrl(factory.directoryUrl), PathUtil.getFileName(addedFileUrl)), virtualFileManager)
      if (newFileSerializer != null) {
        newFileSerializers.add(newFileSerializer)
        serializerToDirectoryFactory[newFileSerializer] = factory
      }
    }

    for (changedUrl in change.changedFileUrls) {
      val serializerFactory = fileSerializerFactoriesByUrl[changedUrl]
      if (serializerFactory != null) {
        val newFileUrls = serializerFactory.loadFileList(reader, virtualFileManager)
        val oldSerializers: List<JpsFileEntitiesSerializer<*>> = serializerToFileFactory.getKeysByValue(serializerFactory) ?: emptyList()
        val oldFileUrls = oldSerializers.mapTo(HashSet()) { it.fileUrl }
        val newFileUrlsSet = newFileUrls.toSet()
        val obsoleteSerializersForFactory = oldSerializers.filter { it.fileUrl !in newFileUrlsSet }
        obsoleteSerializersForFactory.forEach { serializerToFileFactory.remove(it, serializerFactory) }
        val newFileSerializersForFactory = newFileUrls.filter { it !in oldFileUrls }.map {
          serializerFactory.createSerializer(createFileInDirectorySource(it.parent!!, it.file!!.name), it)
        }
        newFileSerializersForFactory.associateWithTo(serializerToFileFactory) { serializerFactory }
        obsoleteSerializers.addAll(obsoleteSerializersForFactory)
        newFileSerializers.addAll(newFileSerializersForFactory)
      }
    }

    for (newSerializer in newFileSerializers) {
      fileSerializersByUrl.putValue(newSerializer.fileUrl.url, newSerializer)
    }
    for (obsoleteSerializer in obsoleteSerializers) {
      fileSerializersByUrl.remove(obsoleteSerializer.fileUrl.url, obsoleteSerializer)
    }

    val affectedFileLoaders = (change.changedFileUrls + change.addedFileUrls).toCollection(HashSet()).flatMap { fileSerializersByUrl[it] }
    val changedSources = affectedFileLoaders.mapTo(HashSet()) { it.internalEntitySource }
    for (fileUrl in change.removedFileUrls) {
      val obsolete = fileSerializersByUrl.remove(fileUrl)
      if (obsolete != null) {
        obsoleteSerializers.addAll(obsolete)
        obsolete.forEach {
          serializerToDirectoryFactory.remove(it)
        }
      }
    }
    obsoleteSerializers.mapTo(changedSources) { it.internalEntitySource }
    obsoleteSerializers.asSequence().map { it.internalEntitySource }.filterIsInstance(JpsFileEntitySource.FileInDirectory::class.java).forEach {
      fileIdToFileName.remove(it.fileNameId)
    }

    val builder = TypedEntityStorageBuilder.create()
    affectedFileLoaders.forEach {
      it.loadEntities(builder, reader, virtualFileManager)
    }
    return Pair(changedSources, builder)
  }

  override fun loadAll(reader: JpsFileContentReader, builder: TypedEntityStorageBuilder) {
    fileSerializersByUrl.values().forEach {
      it.loadEntities(builder, reader, virtualFileManager)
    }
  }

  @TestOnly
  override fun saveAllEntities(storage: TypedEntityStorage, writer: JpsFileContentWriter) {
    fileSerializerFactoriesByUrl.values.forEach {
      saveEntitiesList(it, storage, writer)
    }

    val allSources = storage.entitiesBySource { true }.keys
    saveEntities(storage, allSources, writer)
  }

  private fun getActualFileUrl(source: JpsFileEntitySource) = when (source) {
    is JpsFileEntitySource.ExactFile -> source.file.url
    is JpsFileEntitySource.FileInDirectory -> {
      val fileName = fileIdToFileName[source.fileNameId]
      if (fileName != null) source.directory.url + "/" + fileName else null
    }
  }

  private fun getActualFileSource(source: EntitySource) = when (source) {
    is JpsImportedEntitySource -> {
      if (source.storedExternally) {
        //todo remove obsolete entries
        internalSourceToExternal.getOrPut(source.internalFile, { externalStorageMapping.getExternalSource(source.internalFile) })
      }
      else {
        source.internalFile
      }
    }
    is JpsFileEntitySource -> source
    else -> null
  }

  private fun getInternalFileSource(source: EntitySource) = when (source) {
    is JpsImportedEntitySource -> source.internalFile
    is JpsFileEntitySource -> source
    else -> null
  }

  internal fun getActualFileUrl(source: EntitySource) = getActualFileSource(source)?.let { getActualFileUrl(it) }

  override fun getAllModulePaths(): List<ModulePath> {
    return fileSerializersByUrl.values().filterIsInstance<ModuleImlFileEntitiesSerializer>().map { it.modulePath }
  }

  override fun saveEntities(storage: TypedEntityStorage, affectedSources: Set<EntitySource>, writer: JpsFileContentWriter) {
    val affectedFileFactories = HashSet<JpsFileSerializerFactory<*>>()

    fun processObsoleteSource(fileUrl: String, deleteObsoleteFilesFromFileFactories: Boolean) {
      val obsoleteSerializers = fileSerializersByUrl.remove(fileUrl)
      obsoleteSerializers?.forEach {
        val fileFactory = serializerToFileFactory.remove(it)
        if (fileFactory != null) {
          if (deleteObsoleteFilesFromFileFactories) {
            fileFactory.deleteObsoleteFile(fileUrl, writer)
          }
          affectedFileFactories.add(fileFactory)
        }
      }
      obsoleteSerializers?.forEach {
        val directoryFactory = serializerToDirectoryFactory.remove(it)
        if (directoryFactory != null) {
          writer.saveComponent(fileUrl, directoryFactory.componentName, null)
        }
      }
    }

    val entitiesToSave = storage.entitiesBySource { it in affectedSources }
    val obsoleteSources = affectedSources - entitiesToSave.keys
    for (source in obsoleteSources) {
      val fileUrl = getActualFileUrl(source)
      if (fileUrl != null) {
        processObsoleteSource(fileUrl, false)
        if (source is JpsFileEntitySource.FileInDirectory) {
          fileIdToFileName.remove(source.fileNameId)
        }
      }
    }

    val serializersToRun = ArrayList<Pair<JpsFileEntitiesSerializer<*>, Map<Class<out TypedEntity>, List<TypedEntity>>>>()

    fun processNewlyAddedDirectoryEntities(entitiesMap: Map<Class<out TypedEntity>, List<TypedEntity>>) {
      directorySerializerFactoriesByUrl.values.forEach { factory ->
        val added = entitiesMap[factory.entityClass]
        if (added != null) {
          val newSerializers = createSerializersForDirectoryEntities(factory, added)
          newSerializers.forEach {
            serializerToDirectoryFactory[it.first] = factory
            fileSerializersByUrl.putValue(it.first.fileUrl.url, it.first)
          }
          serializersToRun.addAll(newSerializers)
        }
      }
    }

    entitiesToSave.forEach { (source, entities) ->
      val actualFileSource = getActualFileSource(source)
      if (actualFileSource is JpsFileEntitySource.FileInDirectory) {
        val fileNameByEntity = calculateFileNameForEntity(actualFileSource, source, entities)
        val oldFileName = fileIdToFileName[actualFileSource.fileNameId]
        if (oldFileName != fileNameByEntity) {
          fileIdToFileName.put(actualFileSource.fileNameId, fileNameByEntity)
          if (oldFileName != null) {
            processObsoleteSource("${actualFileSource.directory.url}/$oldFileName", true)
          }
          processNewlyAddedDirectoryEntities(entities)
        }
      }
      val url = actualFileSource?.let { getActualFileUrl(it) }
      val internalSource = getInternalFileSource(source)
      if (url != null && internalSource != null && url !in fileSerializersByUrl.keySet()) {
        fileSerializerFactoriesByUrl.values.forEach { factory ->
          if ((factory.entityClass in entities || factory.additionalEntityClass in entities) && factory.entitySourceFilter(source)) {
            val newSerializer = factory.createSerializer(internalSource, virtualFileManager.fromUrl(url))
            fileSerializersByUrl.putValue(url, newSerializer)
            serializerToFileFactory[newSerializer] = factory
            affectedFileFactories.add(factory)
          }
        }
      }
    }

    entitiesToSave.forEach { (source, entities) ->
      val serializers = fileSerializersByUrl[getActualFileUrl(source)]
      serializers.filter { it !is JpsFileEntityTypeSerializer }.mapTo(serializersToRun) {
        Pair(it, entities)
      }
    }

    fileSerializerFactoriesByUrl.values.forEach {
      if (it in affectedFileFactories) {
        saveEntitiesList(it, storage, writer)
      }
    }

    for (serializer in entityTypeSerializers) {
      if (entitiesToSave.any { serializer.mainEntityClass in it.value }) {
        val entitiesMap = mutableMapOf(serializer.mainEntityClass to getFilteredEntitiesForSerializer(serializer, storage))
        serializer.additionalEntityTypes.associateWithTo(entitiesMap) {
          storage.entities(it).toList()
        }
        serializersToRun.add(Pair(serializer, entitiesMap))
      }
    }

    serializersToRun.forEach {
      saveEntitiesBySerializer(it.first, it.second, writer)
    }
  }

  private fun calculateFileNameForEntity(source: JpsFileEntitySource.FileInDirectory,
                                         originalSource: EntitySource,
                                         entities: Map<Class<out TypedEntity>, List<TypedEntity>>): String? {
    val directoryFactory = directorySerializerFactoriesByUrl[source.directory.url]
    if (directoryFactory != null) {
      return getDefaultFileNameForEntity(directoryFactory, entities)
    }
    val fileFactory = fileSerializerFactoriesByUrl.values.find {
      (it.entityClass in entities || it.additionalEntityClass in entities) && it.entitySourceFilter(originalSource)
    }
    if (fileFactory != null) {
      return getFileNameForEntity(fileFactory, entities)
    }
    return null
  }

  private fun <E : TypedEntity> getDefaultFileNameForEntity(directoryFactory: JpsDirectoryEntitiesSerializerFactory<E>,
                                                            entities: Map<Class<out TypedEntity>, List<TypedEntity>>): String? {
    @Suppress("UNCHECKED_CAST") val entity = entities[directoryFactory.entityClass]?.singleOrNull() as? E ?: return null
    return FileUtil.sanitizeFileName(directoryFactory.getDefaultFileName(entity)) + ".xml"
  }

  private fun <E : TypedEntity> getFileNameForEntity(fileFactory: JpsFileSerializerFactory<E>,
                                                     entities: Map<Class<out TypedEntity>, List<TypedEntity>>): String? {
    @Suppress("UNCHECKED_CAST") val entity = entities[fileFactory.entityClass]?.singleOrNull() as? E
    if (entity != null) {
      return fileFactory.getFileName(entity)
    }
    val additionalEntity = entities[fileFactory.additionalEntityClass]?.firstOrNull() ?: return null
    val mainEntity = fileFactory.getMainEntity(additionalEntity) as? E ?: return null
    return fileFactory.getFileName(mainEntity)
  }

  private fun <E : TypedEntity> getFilteredEntitiesForSerializer(serializer: JpsFileEntityTypeSerializer<E>,
                                                                 storage: TypedEntityStorage): List<E> {
    return storage.entities(serializer.mainEntityClass).filter(serializer.entityFilter).toList()
  }

  private fun <E : TypedEntity> saveEntitiesBySerializer(serializer: JpsFileEntitiesSerializer<E>,
                                                         entities: Map<Class<out TypedEntity>, List<TypedEntity>>,
                                                         writer: JpsFileContentWriter) {
    @Suppress("UNCHECKED_CAST")
    serializer.saveEntities(entities[serializer.mainEntityClass] as? Collection<E> ?: emptyList(), entities, writer)
  }

  private fun <E : TypedEntity> createSerializersForDirectoryEntities(factory: JpsDirectoryEntitiesSerializerFactory<E>,
                                                                      entities: List<TypedEntity>)
    : List<Pair<JpsFileEntitiesSerializer<*>, Map<Class<out TypedEntity>, List<TypedEntity>>>> {
    val nameGenerator = UniqueNameGenerator(serializerToDirectoryFactory.getKeysByValue(factory) ?: emptyList(), Function {
      PathUtil.getFileName(it.fileUrl.url)
    })
    return entities
      .filter { @Suppress("UNCHECKED_CAST") factory.entityFilter(it as E) }
      .map {
        @Suppress("UNCHECKED_CAST")
        val fileName = nameGenerator.generateUniqueName(FileUtil.sanitizeFileName(factory.getDefaultFileName(it as E)), "", ".xml")
        val entityMap = mapOf<Class<out TypedEntity>, List<TypedEntity>>(factory.entityClass to listOf(it))
        val currentSource = it.entitySource as? JpsFileEntitySource.FileInDirectory
        val source =
          if (currentSource != null && fileIdToFileName[currentSource.fileNameId] == fileName) currentSource
          else createFileInDirectorySource(virtualFileManager.fromUrl(factory.directoryUrl), fileName)
        Pair(factory.createSerializer("${factory.directoryUrl}/$fileName", source, virtualFileManager), entityMap)
      }
  }

  private fun <E : TypedEntity> saveEntitiesList(it: JpsFileSerializerFactory<E>,
                                                 storage: TypedEntityStorage,
                                                 writer: JpsFileContentWriter) {
    it.saveEntitiesList(storage.entities(it.entityClass), writer)
  }
}

internal class CachingJpsFileContentReader(projectBaseDirUrl: String) : JpsFileContentReader {
  private val projectPathMacroManager = LegacyBridgeProjectPathMacroManager(JpsPathUtil.urlToPath(projectBaseDirUrl))
  private val fileContentCache = ConcurrentHashMap<String, Map<String, Element>>()

  override fun loadComponent(fileUrl: String, componentName: String): Element? {
    val content = fileContentCache.computeIfAbsent(fileUrl) {
      loadComponents(it)
    }
    return content[componentName]
  }

  private fun loadComponents(fileUrl: String): Map<String, Element> {
    val macroManager = if (FileUtil.extensionEquals(fileUrl, "iml")) {
      LegacyBridgeModulePathMacroManager(PathMacros.getInstance(), JpsPathUtil.urlToPath(fileUrl))
    }
    else {
      projectPathMacroManager
    }
    val file = JpsPathUtil.urlToFile(fileUrl)
    if (!file.isFile) return emptyMap()
    return loadStorageFile(file, macroManager)
  }

  internal class LegacyBridgeModulePathMacroManager(pathMacros: PathMacros, private val moduleFilePath: String) : PathMacroManager(
    pathMacros) {
    override fun getExpandMacroMap(): ExpandMacroToPathMap {
      val result = super.getExpandMacroMap()
      addFileHierarchyReplacements(
        result, PathMacroUtil.MODULE_DIR_MACRO_NAME,
        PathMacroUtil.getModuleDir(moduleFilePath))
      return result
    }

    public override fun computeReplacePathMap(): ReplacePathToMacroMap {
      val result = super.computeReplacePathMap()
      val modulePath = PathMacroUtil.getModuleDir(moduleFilePath)
      addFileHierarchyReplacements(
        result, PathMacroUtil.MODULE_DIR_MACRO_NAME, modulePath,
        PathMacroUtil.getUserHomePath())
      return result
    }
  }

  internal class LegacyBridgeProjectPathMacroManager(private val projectDirPath: String) : PathMacroManager(PathMacros.getInstance()) {
    override fun getExpandMacroMap(): ExpandMacroToPathMap {
      val result = super.getExpandMacroMap()
      addFileHierarchyReplacements(result, PathMacroUtil.PROJECT_DIR_MACRO_NAME, projectDirPath)
      return result
    }

    override fun computeReplacePathMap(): ReplacePathToMacroMap {
      val result = super.computeReplacePathMap()
      addFileHierarchyReplacements(result, PathMacroUtil.PROJECT_DIR_MACRO_NAME, projectDirPath, null)
      return result
    }
  }
}

// TODO Add more diagnostics: file path, line etc
internal fun Element.getAttributeValueStrict(name: String): String =
  getAttributeValue(name) ?: error("Expected attribute $name under ${this.name} element")
