// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.ModulePathMacroManager
import com.intellij.openapi.components.impl.ProjectPathMacroManager
import com.intellij.openapi.module.impl.ModulePath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.Function
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.workspaceModel.ide.JpsFileDependentEntitySource
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import kotlin.streams.toList

class JpsProjectSerializersImpl(directorySerializersFactories: List<JpsDirectoryEntitiesSerializerFactory<*>>,
                                moduleListSerializers: List<JpsModuleListSerializer>,
                                reader: JpsFileContentReader,
                                private val entityTypeSerializers: List<JpsFileEntityTypeSerializer<*>>,
                                private val configLocation: JpsProjectConfigLocation,
                                private val externalStorageMapping: JpsExternalStorageMapping,
                                enableExternalStorage: Boolean,
                                private val virtualFileManager: VirtualFileUrlManager) : JpsProjectSerializers {
  internal val moduleSerializers = BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsModuleListSerializer>()
  internal val serializerToDirectoryFactory = BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsDirectoryEntitiesSerializerFactory<*>>()
  private val internalSourceToExternal = HashMap<JpsFileEntitySource, JpsFileEntitySource>()
  internal val fileSerializersByUrl = BidirectionalMultiMap<String, JpsFileEntitiesSerializer<*>>()
  internal val fileIdToFileName = Int2ObjectOpenHashMap<String>()

  init {
    for (factory in directorySerializersFactories) {
      createDirectorySerializers(factory).associateWithTo(serializerToDirectoryFactory) { factory }
    }
    val enabledModuleListSerializers = moduleListSerializers.filter { enableExternalStorage || !it.isExternalStorage }
    val moduleFiles = enabledModuleListSerializers.flatMap { it.loadFileList(reader, virtualFileManager) }
    for ((moduleFile, moduleGroup) in moduleFiles) {
      val internalSource = createFileInDirectorySource(moduleFile.parent!!, moduleFile.file!!.name)
      for (moduleListSerializer in enabledModuleListSerializers) {
        val moduleSerializer = moduleListSerializer.createSerializer(internalSource, moduleFile, moduleGroup)
        moduleSerializers[moduleSerializer] = moduleListSerializer
      }
    }

    val allFileSerializers = entityTypeSerializers.filter { enableExternalStorage || !it.isExternalStorage } +
                             serializerToDirectoryFactory.keys + moduleSerializers.keys
    allFileSerializers.forEach {
      fileSerializersByUrl.put(it.fileUrl.url, it)
    }
  }

  internal val directorySerializerFactoriesByUrl = directorySerializersFactories.associateBy { it.directoryUrl }
  internal val moduleListSerializersByUrl = moduleListSerializers.associateBy { it.fileUrl }

  private fun createFileInDirectorySource(directoryUrl: VirtualFileUrl, fileName: String): JpsFileEntitySource.FileInDirectory {
    val source = JpsFileEntitySource.FileInDirectory(directoryUrl, configLocation)
    // Don't convert to links[key] = ... because it *may* became autoboxing
    @Suppress("ReplacePutWithAssignment")
    fileIdToFileName.put(source.fileNameId, fileName)
    return source
  }

  private fun createDirectorySerializers(factory: JpsDirectoryEntitiesSerializerFactory<*>): List<JpsFileEntitiesSerializer<*>> {
    val osPath = JpsPathUtil.urlToOsPath(factory.directoryUrl)
    val libPath = Paths.get(osPath)
    val files = when {
      Files.exists(libPath) -> Files.list(libPath).use { stream ->
        stream.filter { path: Path -> PathUtil.getFileExtension(path.toString()) == "xml" && Files.isRegularFile(path) }
          .toList()
      }
      else -> emptyList()
    }
    return files.map {
      val fileName = it.fileName
      factory.createSerializer("${factory.directoryUrl}/$fileName",
                               createFileInDirectorySource(virtualFileManager.fromUrl(factory.directoryUrl), fileName.toString()),
                               virtualFileManager)
    }
  }

  fun findModuleSerializer(modulePath: ModulePath): JpsFileEntitiesSerializer<*>? {
    return fileSerializersByUrl.getValues(VfsUtilCore.pathToUrl(modulePath.path)).first()
  }

  override fun reloadFromChangedFiles(change: JpsConfigurationFilesChange,
                                      reader: JpsFileContentReader,
                                      errorReporter: ErrorReporter): Pair<Set<EntitySource>, WorkspaceEntityStorageBuilder> {
    val obsoleteSerializers = ArrayList<JpsFileEntitiesSerializer<*>>()
    val newFileSerializers = ArrayList<JpsFileEntitiesSerializer<*>>()

    val addedFileUrls = change.addedFileUrls.flatMap {
      val file = JpsPathUtil.urlToFile(it)
      if (file.isDirectory) file.listFiles()?.map { JpsPathUtil.pathToUrl(it.canonicalPath) } ?: emptyList() else listOf(it)
    }.toSet()

    for (addedFileUrl in addedFileUrls) {
      val factory = directorySerializerFactoriesByUrl[PathUtil.getParentPath(addedFileUrl)]
      val newFileSerializer = factory?.createSerializer(addedFileUrl, createFileInDirectorySource(
        virtualFileManager.fromUrl(factory.directoryUrl), PathUtil.getFileName(addedFileUrl)), virtualFileManager)
      if (newFileSerializer != null) {
        newFileSerializers.add(newFileSerializer)
        serializerToDirectoryFactory[newFileSerializer] = factory
      }
    }

    for (changedUrl in change.changedFileUrls) {
      val serializerFactory = moduleListSerializersByUrl[changedUrl]
      if (serializerFactory != null) {
        val newFileUrls = serializerFactory.loadFileList(reader, virtualFileManager)
        val oldSerializers: List<JpsFileEntitiesSerializer<*>> = moduleSerializers.getKeysByValue(serializerFactory) ?: emptyList()
        val oldFileUrls = oldSerializers.mapTo(HashSet()) { it.fileUrl }
        val newFileUrlsSet = newFileUrls.mapTo(HashSet()) { it.first }
        val obsoleteSerializersForFactory = oldSerializers.filter { it.fileUrl !in newFileUrlsSet }
        obsoleteSerializersForFactory.forEach { moduleSerializers.remove(it, serializerFactory) }
        val newFileSerializersForFactory = newFileUrls.filter { it.first !in oldFileUrls }.map {
          serializerFactory.createSerializer(createFileInDirectorySource(it.first.parent!!, it.first.file!!.name), it.first, it.second)
        }
        newFileSerializersForFactory.associateWithTo(moduleSerializers) { serializerFactory }
        obsoleteSerializers.addAll(obsoleteSerializersForFactory)
        newFileSerializers.addAll(newFileSerializersForFactory)
      }
    }

    for (newSerializer in newFileSerializers) {
      fileSerializersByUrl.put(newSerializer.fileUrl.url, newSerializer)
    }
    for (obsoleteSerializer in obsoleteSerializers) {
      fileSerializersByUrl.remove(obsoleteSerializer.fileUrl.url, obsoleteSerializer)
    }

    val affectedFileLoaders = (change.changedFileUrls + addedFileUrls).toCollection(HashSet()).flatMap { fileSerializersByUrl.getValues(it) }
    val changedSources = affectedFileLoaders.mapTo(HashSet()) { it.internalEntitySource }
    for (fileUrl in change.removedFileUrls) {

      val directorySerializer = directorySerializerFactoriesByUrl[fileUrl]
      if (directorySerializer != null) {
        val serializers = serializerToDirectoryFactory.getKeysByValue(directorySerializer)?.toList() ?: emptyList()
        for (serializer in serializers) {
          fileSerializersByUrl.removeValue(serializer)

          obsoleteSerializers.add(serializer)
          serializerToDirectoryFactory.remove(serializer, directorySerializer)
        }
      } else {
        val obsolete = fileSerializersByUrl.getValues(fileUrl)
        fileSerializersByUrl.removeKey(fileUrl)

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

    val builder = WorkspaceEntityStorageBuilder.create()
    affectedFileLoaders.forEach {
      it.loadEntities(builder, reader, errorReporter, virtualFileManager)
    }
    return Pair(changedSources, builder)
  }

  override fun loadAll(reader: JpsFileContentReader, builder: WorkspaceEntityStorageBuilder, errorReporter: ErrorReporter) {
    val service = AppExecutorUtil.createBoundedApplicationPoolExecutor("ModuleManager Loader", 1)
    try {
      val tasks = fileSerializersByUrl.values.map { serializer ->
        Callable {
          val myBuilder = WorkspaceEntityStorageBuilder.create()
          serializer.loadEntities(myBuilder, reader, errorReporter, virtualFileManager)
          myBuilder
        }
      }

      val res = ConcurrencyUtil.invokeAll(tasks, service)
      val squashedBuilder = squash(res)
      builder.addDiff(squashedBuilder)
    }
    finally {
      service.shutdown()
    }
  }

  // This fancy logic allows to reduce the time spared by addDiff. This can be removed if addDiff won't do toStorage at the start of the method
  private fun squash(builders: List<Future<WorkspaceEntityStorageBuilder>>): WorkspaceEntityStorageBuilder {
    val builder = byPairs(builders.map { it.get() })
    return builder.singleOrNull() ?: WorkspaceEntityStorageBuilder.create()
  }

  private tailrec fun byPairs(builders: List<WorkspaceEntityStorageBuilder>): List<WorkspaceEntityStorageBuilder> {
    if (builders.isEmpty() || builders.size == 1) return builders
    val resBuilder = ArrayList<WorkspaceEntityStorageBuilder>()
    var i = 0
    while (i < builders.size - 1) {
      val left = builders[i]
      left.addDiff(builders[i+1])
      resBuilder += left
      i += 2
    }
    if (i == builders.lastIndex) {
      resBuilder += builders.last()
    }

    return byPairs(resBuilder)
  }

  @TestOnly
  override fun saveAllEntities(storage: WorkspaceEntityStorage, writer: JpsFileContentWriter) {
    moduleListSerializersByUrl.values.forEach {
      saveModulesList(it, storage, writer)
    }

    val allSources = storage.entitiesBySource { true }.keys
    saveEntities(storage, allSources, writer)
  }

  private fun getActualFileUrl(source: JpsFileEntitySource) = when (source) {
    is JpsFileEntitySource.ExactFile -> source.file.url
    is JpsFileEntitySource.FileInDirectory -> {
      val fileName = fileIdToFileName.get(source.fileNameId)
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
    is JpsFileDependentEntitySource -> source.originalSource
    is JpsFileEntitySource -> source
    else -> null
  }

  private fun getInternalFileSource(source: EntitySource) = when (source) {
    is JpsFileDependentEntitySource -> source.originalSource
    is JpsFileEntitySource -> source
    else -> null
  }

  internal fun getActualFileUrl(source: EntitySource) = getActualFileSource(source)?.let { getActualFileUrl(it) }

  override fun getAllModulePaths(): List<ModulePath> {
    return fileSerializersByUrl.values.filterIsInstance<ModuleImlFileEntitiesSerializer>().map { it.modulePath }
  }

  override fun saveEntities(storage: WorkspaceEntityStorage, affectedSources: Set<EntitySource>, writer: JpsFileContentWriter) {
    val affectedFileFactories = HashSet<JpsModuleListSerializer>()

    fun processObsoleteSource(fileUrl: String, deleteObsoleteFilesFromFileFactories: Boolean) {
      val obsoleteSerializers = fileSerializersByUrl.getValues(fileUrl)
      fileSerializersByUrl.removeKey(fileUrl)
      obsoleteSerializers.forEach {
        val fileFactory = moduleSerializers.remove(it)
        if (fileFactory != null) {
          if (deleteObsoleteFilesFromFileFactories) {
            fileFactory.deleteObsoleteFile(fileUrl, writer)
          }
          affectedFileFactories.add(fileFactory)
        }
      }
      obsoleteSerializers.forEach {
        val directoryFactory = serializerToDirectoryFactory.remove(it)
        if (directoryFactory != null) {
          writer.saveComponent(fileUrl, directoryFactory.componentName, null)
        }
      }
    }

    val entitiesToSave = storage.entitiesBySource { it in affectedSources }
    val internalSourceConvertedToImported = affectedSources.filterIsInstance<JpsImportedEntitySource>().mapTo(HashSet()) {
      it.internalFile
    }
    val obsoleteSources = affectedSources - entitiesToSave.keys
    for (source in obsoleteSources) {
      val fileUrl = getActualFileUrl(source)
      if (fileUrl != null) {
        processObsoleteSource(fileUrl, source in internalSourceConvertedToImported)
        if (source is JpsFileEntitySource.FileInDirectory) {
          fileIdToFileName.remove(source.fileNameId)
        }
      }
    }

    val serializersToRun = ArrayList<Pair<JpsFileEntitiesSerializer<*>, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>>>()

    fun processNewlyAddedDirectoryEntities(entitiesMap: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>) {
      directorySerializerFactoriesByUrl.values.forEach { factory ->
        val added = entitiesMap[factory.entityClass]
        if (added != null) {
          val newSerializers = createSerializersForDirectoryEntities(factory, added)
          newSerializers.forEach {
            serializerToDirectoryFactory[it.first] = factory
            fileSerializersByUrl.put(it.first.fileUrl.url, it.first)
          }
          serializersToRun.addAll(newSerializers)
        }
      }
    }

    entitiesToSave.forEach { (source, entities) ->
      val actualFileSource = getActualFileSource(source)
      if (actualFileSource is JpsFileEntitySource.FileInDirectory) {
        val fileNameByEntity = calculateFileNameForEntity(actualFileSource, source, entities)
        val oldFileName = fileIdToFileName.get(actualFileSource.fileNameId)
        if (oldFileName != fileNameByEntity) {
          // Don't convert to links[key] = ... because it *may* became autoboxing
          @Suppress("ReplacePutWithAssignment")
          fileIdToFileName.put(actualFileSource.fileNameId, fileNameByEntity)
          if (oldFileName != null) {
            processObsoleteSource("${actualFileSource.directory.url}/$oldFileName", true)
          }
          processNewlyAddedDirectoryEntities(entities)
        }
      }
      val url = actualFileSource?.let { getActualFileUrl(it) }
      val internalSource = getInternalFileSource(source)
      if (url != null && internalSource != null && url !in fileSerializersByUrl.keys
          && (ModuleEntity::class.java in entities || FacetEntity::class.java in entities)) {
        moduleListSerializersByUrl.values.forEach { moduleListSerializer ->
          if (moduleListSerializer.entitySourceFilter(source)) {
            val newSerializer = moduleListSerializer.createSerializer(internalSource, virtualFileManager.fromUrl(url), null)
            fileSerializersByUrl.put(url, newSerializer)
            moduleSerializers[newSerializer] = moduleListSerializer
            affectedFileFactories.add(moduleListSerializer)
          }
        }
      }
    }

    entitiesToSave.forEach { (source, entities) ->
      val serializers = fileSerializersByUrl.getValues(getActualFileUrl(source))
      serializers.filter { it !is JpsFileEntityTypeSerializer }.mapTo(serializersToRun) {
        Pair(it, entities)
      }
    }

    moduleListSerializersByUrl.values.forEach {
      if (it in affectedFileFactories) {
        saveModulesList(it, storage, writer)
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
      saveEntitiesBySerializer(it.first, it.second, storage, writer)
    }
  }

  private fun calculateFileNameForEntity(source: JpsFileEntitySource.FileInDirectory,
                                         originalSource: EntitySource,
                                         entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>): String? {
    val directoryFactory = directorySerializerFactoriesByUrl[source.directory.url]
    if (directoryFactory != null) {
      return getDefaultFileNameForEntity(directoryFactory, entities)
    }
    if (ModuleEntity::class.java in entities || FacetEntity::class.java in entities) {
      val moduleListSerializer = moduleListSerializersByUrl.values.find {
         it.entitySourceFilter(originalSource)
      }
      if (moduleListSerializer != null) {
        return getFileNameForModuleEntity(moduleListSerializer, entities)
      }
    }
    return null
  }

  private fun <E : WorkspaceEntity> getDefaultFileNameForEntity(directoryFactory: JpsDirectoryEntitiesSerializerFactory<E>,
                                                                entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>): String? {
    @Suppress("UNCHECKED_CAST") val entity = entities[directoryFactory.entityClass]?.singleOrNull() as? E ?: return null
    return FileUtil.sanitizeFileName(directoryFactory.getDefaultFileName(entity)) + ".xml"
  }

  private fun getFileNameForModuleEntity(moduleListSerializer: JpsModuleListSerializer,
                                         entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>): String? {
    val entity = entities[ModuleEntity::class.java]?.singleOrNull() as? ModuleEntity
    if (entity != null) {
      return moduleListSerializer.getFileName(entity)
    }
    val additionalEntity = entities[FacetEntity::class.java]?.firstOrNull() as? FacetEntity ?: return null
    return moduleListSerializer.getFileName(additionalEntity.module)
  }

  private fun <E : WorkspaceEntity> getFilteredEntitiesForSerializer(serializer: JpsFileEntityTypeSerializer<E>,
                                                                     storage: WorkspaceEntityStorage): List<E> {
    return storage.entities(serializer.mainEntityClass).filter(serializer.entityFilter).toList()
  }

  private fun <E : WorkspaceEntity> saveEntitiesBySerializer(serializer: JpsFileEntitiesSerializer<E>,
                                                             entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                                                             storage: WorkspaceEntityStorage,
                                                             writer: JpsFileContentWriter) {
    @Suppress("UNCHECKED_CAST")
    serializer.saveEntities(entities[serializer.mainEntityClass] as? Collection<E> ?: emptyList(), entities, storage, writer)
  }

  private fun <E : WorkspaceEntity> createSerializersForDirectoryEntities(factory: JpsDirectoryEntitiesSerializerFactory<E>,
                                                                          entities: List<WorkspaceEntity>)
    : List<Pair<JpsFileEntitiesSerializer<*>, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>>> {
    val nameGenerator = UniqueNameGenerator(serializerToDirectoryFactory.getKeysByValue(factory) ?: emptyList(), Function {
      PathUtil.getFileName(it.fileUrl.url)
    })
    return entities
      .filter { @Suppress("UNCHECKED_CAST") factory.entityFilter(it as E) }
      .map {
        @Suppress("UNCHECKED_CAST")
        val fileName = nameGenerator.generateUniqueName(FileUtil.sanitizeFileName(factory.getDefaultFileName(it as E)), "", ".xml")
        val entityMap = mapOf<Class<out WorkspaceEntity>, List<WorkspaceEntity>>(factory.entityClass to listOf(it))
        val currentSource = it.entitySource as? JpsFileEntitySource.FileInDirectory
        val source =
          if (currentSource != null && fileIdToFileName.get(currentSource.fileNameId) == fileName) currentSource
          else createFileInDirectorySource(virtualFileManager.fromUrl(factory.directoryUrl), fileName)
        Pair(factory.createSerializer("${factory.directoryUrl}/$fileName", source, virtualFileManager), entityMap)
      }
  }

  private fun saveModulesList(it: JpsModuleListSerializer, storage: WorkspaceEntityStorage, writer: JpsFileContentWriter) {
    it.saveEntitiesList(storage.entities(ModuleEntity::class.java), writer)
  }
}

class CachingJpsFileContentReader(projectBaseDirUrl: String) : JpsFileContentReader {
  private val projectPathMacroManager = ProjectPathMacroManager.createInstance({ JpsPathUtil.urlToPath(projectBaseDirUrl) }, null)
  private val fileContentCache = ConcurrentHashMap<String, Map<String, Element>>()

  override fun loadComponent(fileUrl: String, componentName: String, customModuleFilePath: String?): Element? {
    val content = fileContentCache.computeIfAbsent(fileUrl) {
      loadComponents(it, customModuleFilePath)
    }
    return content[componentName]
  }

  override fun getExpandMacroMap(fileUrl: String): ExpandMacroToPathMap {
    return getMacroManager(fileUrl, null).expandMacroMap
  }

  private fun loadComponents(fileUrl: String, customModuleFilePath: String?): Map<String, Element> {
    val macroManager = getMacroManager(fileUrl, customModuleFilePath)

    val file = Paths.get(JpsPathUtil.urlToPath(fileUrl))
    return if (Files.isRegularFile(file)) loadStorageFile(file, macroManager) else emptyMap()
  }

  private fun getMacroManager(fileUrl: String,
                              customModuleFilePath: String?): PathMacroManager {
    val path = JpsPathUtil.urlToPath(fileUrl)
    return if (FileUtil.extensionEquals(fileUrl, "iml") || isExternalModuleFile(path)) {
      ModulePathMacroManager.createInstance { customModuleFilePath ?: path }
    }
    else {
      projectPathMacroManager
    }
  }
}

// TODO Add more diagnostics: file path, line etc
internal fun Element.getAttributeValueStrict(name: String): String =
  getAttributeValue(name) ?: error("Expected attribute $name under ${this.name} element")

fun isExternalModuleFile(filePath: String): Boolean {
  val parentPath = PathUtil.getParentPath(filePath)
  return FileUtil.extensionEquals(filePath, "xml") && PathUtil.getFileName(parentPath) == "modules"
         && PathUtil.getFileName(PathUtil.getParentPath(parentPath)) != ".idea"
}
