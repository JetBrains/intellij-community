package com.intellij.workspace.jps

import com.intellij.application.options.ReplacePathToMacroMap
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Function
import com.intellij.util.PathUtil
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.MultiMap
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.IdeUiEntitySource
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.JpsProjectStoragePlace
import gnu.trove.TIntObjectHashMap
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
  val entitySource: JpsFileEntitySource
  val fileUrl: VirtualFileUrl
  val mainEntityClass: Class<E>
  fun loadEntities(builder: TypedEntityStorageBuilder, reader: JpsFileContentReader)
  fun saveEntities(mainEntities: Collection<E>, entities: Map<Class<out TypedEntity>, List<TypedEntity>>, writer: JpsFileContentWriter): List<TypedEntity>

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
 *  Represents set of serializers corresponding to a project.
 */
class JpsEntitiesSerializerFactories(val entityTypeSerializers: List<JpsFileEntityTypeSerializer<*>>,
                                     val directorySerializersFactories: List<JpsDirectoryEntitiesSerializerFactory<*>>,
                                     val fileSerializerFactories: List<JpsFileSerializerFactory<*>>,
                                     val storagePlace: JpsProjectStoragePlace) {
  fun createSerializers(reader: JpsFileContentReader): JpsEntitiesSerializationData {
    return JpsEntitiesSerializationData(directorySerializersFactories, fileSerializerFactories, reader, entityTypeSerializers, storagePlace)
  }
}

data class JpsConfigurationFilesChange(val addedFileUrls: Collection<String>, val removedFileUrls: Collection<String>, val changedFileUrls: Collection<String>)

class JpsEntitiesSerializationData(directorySerializersFactories: List<JpsDirectoryEntitiesSerializerFactory<*>>,
                                   fileSerializerFactories: List<JpsFileSerializerFactory<*>>,
                                   reader: JpsFileContentReader,
                                   private val entityTypeSerializers: List<JpsFileEntityTypeSerializer<*>>,
                                   private val storagePlace: JpsProjectStoragePlace) {
  internal val serializerToFileFactory = BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsFileSerializerFactory<*>>()
  internal val serializerToDirectoryFactory = BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsDirectoryEntitiesSerializerFactory<*>>()
  internal val fileSerializersByUrl = MultiMap.create<String, JpsFileEntitiesSerializer<*>>()
  internal val fileIdToFileName = TIntObjectHashMap<String>()

  init {
    for (factory in directorySerializersFactories) {
      createDirectorySerializers(factory).associateWithTo(serializerToDirectoryFactory) { factory }
    }
    for (factory in fileSerializerFactories) {
      val fileList = factory.loadFileList(reader)
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

  internal fun createFileInDirectorySource(directoryUrl: VirtualFileUrl, fileName: String): JpsFileEntitySource.FileInDirectory {
    val source = JpsFileEntitySource.FileInDirectory(directoryUrl, storagePlace)
    fileIdToFileName.put(source.fileNameId, fileName)
    return source
  }

  private fun createDirectorySerializers(factory: JpsDirectoryEntitiesSerializerFactory<*>): List<JpsFileEntitiesSerializer<*>> {
    val files = JpsPathUtil.urlToFile(factory.directoryUrl).listFiles { file: File -> file.extension == "xml" && file.isFile }
                ?: return emptyList()
    return files.map {
      factory.createSerializer("${factory.directoryUrl}/${it.name}",
                               createFileInDirectorySource(VirtualFileUrlManager.fromUrl(factory.directoryUrl), it.name))
    }
  }

  fun reloadFromChangedFiles(change: JpsConfigurationFilesChange,
                             reader: JpsFileContentReader): Pair<Set<EntitySource>, TypedEntityStorageBuilder> {
    val obsoleteSerializers = ArrayList<JpsFileEntitiesSerializer<*>>()
    val newFileSerializers = ArrayList<JpsFileEntitiesSerializer<*>>()
    for (addedFileUrl in change.addedFileUrls) {
      val factory = directorySerializerFactoriesByUrl[PathUtil.getParentPath(addedFileUrl)]
      val newFileSerializer = factory?.createSerializer(addedFileUrl, createFileInDirectorySource(VirtualFileUrlManager.fromUrl(factory.directoryUrl), PathUtil.getFileName(addedFileUrl)))
      if (newFileSerializer != null) {
        newFileSerializers.add(newFileSerializer)
        serializerToDirectoryFactory[newFileSerializer] = factory
      }
    }

    for (changedUrl in change.changedFileUrls) {
      val serializerFactory = fileSerializerFactoriesByUrl[changedUrl]
      if (serializerFactory != null) {
        val newFileUrls = serializerFactory.loadFileList(reader)
        val oldSerializers: List<JpsFileEntitiesSerializer<*>> = serializerToFileFactory.getKeysByValue(serializerFactory) ?: emptyList()
        val oldFileUrls = oldSerializers.mapTo(HashSet()) { it.fileUrl }
        val newFileUrlsSet = newFileUrls.toSet()
        val obsoleteSerializersForFactory = oldSerializers.filter { it.fileUrl !in newFileUrlsSet }
        obsoleteSerializersForFactory.forEach { serializerToFileFactory.remove(it, serializerFactory) }
        val newFileSerializersForFactory = newFileUrls.filter { it !in oldFileUrls}.map {
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
    val changedSources = affectedFileLoaders.mapTo(HashSet()) { it.entitySource }
    for (fileUrl in change.removedFileUrls) {
      val obsolete = fileSerializersByUrl.remove(fileUrl)
      if (obsolete != null) {
        obsoleteSerializers.addAll(obsolete)
        obsolete.forEach {
          serializerToDirectoryFactory.remove(it)
        }
      }
    }
    obsoleteSerializers.mapTo(changedSources) { it.entitySource }
    obsoleteSerializers.asSequence().map { it.entitySource }.filterIsInstance(JpsFileEntitySource.FileInDirectory::class.java).forEach {
      fileIdToFileName.remove(it.fileNameId)
    }

    val builder = TypedEntityStorageBuilder.create()
    affectedFileLoaders.forEach {
      it.loadEntities(builder, reader)
    }
    return Pair(changedSources, builder)
  }

  fun loadAll(reader: JpsFileContentReader, builder: TypedEntityStorageBuilder) {
    fileSerializersByUrl.values().forEach {
      it.loadEntities(builder, reader)
    }
  }

  @TestOnly
  fun saveAllEntities(storage: TypedEntityStorage, writer: JpsFileContentWriter) {
    fileSerializerFactoriesByUrl.values.forEach {
      saveEntitiesList(it, storage, writer)
    }

    val allSources = fileSerializersByUrl.values().mapTo(HashSet<EntitySource>()) { it.entitySource }
    allSources += IdeUiEntitySource
    saveEntities(storage, allSources, writer)
  }

  internal fun getActualFileUrl(source: JpsFileEntitySource) = when (source) {
    is JpsFileEntitySource.ExactFile -> source.file.url
    is JpsFileEntitySource.FileInDirectory -> {
      val fileName = fileIdToFileName[source.fileNameId]
      if (fileName != null) source.directory.url + "/" + fileName else null
    }
  }

  fun saveEntities(storage: TypedEntityStorage, affectedSources: Set<EntitySource>, writer: JpsFileContentWriter): List<Pair<TypedEntity, JpsFileEntitySource>> {
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
      if (source is JpsFileEntitySource) {
        val fileUrl = getActualFileUrl(source)
        if (fileUrl != null) {
          processObsoleteSource(fileUrl, false)
          if (source is JpsFileEntitySource.FileInDirectory) {
            fileIdToFileName.remove(source.fileNameId)
          }
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
      if (source is JpsFileEntitySource) {
        if (source is JpsFileEntitySource.FileInDirectory) {
          val fileNameByEntity = calculateFileNameForEntity(source, entities)
          val oldFileName = fileIdToFileName[source.fileNameId]
          if (oldFileName != fileNameByEntity) {
            fileIdToFileName.put(source.fileNameId, fileNameByEntity)
            if (oldFileName != null) {
              processObsoleteSource("${source.directory.url}/$oldFileName", true)
            }
            processNewlyAddedDirectoryEntities(entities)
          }
        }
        val url = getActualFileUrl(source)
        if (url != null && url !in fileSerializersByUrl.keySet()) {
          fileSerializerFactoriesByUrl.values.forEach { factory ->
            if (factory.entityClass in entities) {
              val newSerializer = factory.createSerializer(source, VirtualFileUrlManager.fromUrl(url))
              fileSerializersByUrl.putValue(url, newSerializer)
              serializerToFileFactory[newSerializer] = factory
              affectedFileFactories.add(factory)
            }
          }
        }
      }
    }

    entitiesToSave.forEach { (source, entities) ->
      if (source is JpsFileEntitySource) {
        val serializers = fileSerializersByUrl[getActualFileUrl(source)]
        serializers.filter { it !is JpsFileEntityTypeSerializer }.mapTo(serializersToRun) {
          Pair(it, entities)
        }
      }
    }

    val newEntities = entitiesToSave[IdeUiEntitySource] ?: emptyMap()
    fileSerializerFactoriesByUrl.values.forEach {
      if (it in affectedFileFactories || it.entityClass in newEntities) {
        saveEntitiesList(it, storage, writer)
      }
    }

    processNewlyAddedDirectoryEntities(newEntities)

    for (serializer in entityTypeSerializers) {
      if (serializer.mainEntityClass in newEntities ||
        entitiesToSave.any { serializer.mainEntityClass in it.value }) {
        val entitiesMap = mutableMapOf(serializer.mainEntityClass to getFilteredEntitiesForSerializer(serializer, storage))
        serializer.additionalEntityTypes.associateWithTo(entitiesMap) {
          storage.entities(it).toList()
        }
        serializersToRun.add(Pair(serializer, entitiesMap))
      }
    }

    return serializersToRun.flatMap {
      saveEntitiesBySerializer(it.first, it.second, writer)
    }
  }

  private fun calculateFileNameForEntity(source: JpsFileEntitySource.FileInDirectory,
                                         entities: Map<Class<out TypedEntity>, List<TypedEntity>>): String? {
    val directoryFactory = directorySerializerFactoriesByUrl[source.directory.url]
    if (directoryFactory != null) {
      return getDefaultFileNameForEntity(directoryFactory, entities)
    }
    val fileFactory = fileSerializerFactoriesByUrl.values.find { it.entityClass in entities }
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
    @Suppress("UNCHECKED_CAST") val entity = entities[fileFactory.entityClass]?.singleOrNull() as? E ?: return null
    return fileFactory.getFileName(entity)
  }

  private fun <E : TypedEntity> getFilteredEntitiesForSerializer(serializer: JpsFileEntityTypeSerializer<E>, storage: TypedEntityStorage): List<E> {
    return storage.entities(serializer.mainEntityClass).filter(serializer.entityFilter).toList()
  }

  private fun <E : TypedEntity> saveEntitiesBySerializer(serializer: JpsFileEntitiesSerializer<E>,
                                                         entities: Map<Class<out TypedEntity>, List<TypedEntity>>,
                                                         writer: JpsFileContentWriter): List<Pair<TypedEntity, JpsFileEntitySource>> {
    @Suppress("UNCHECKED_CAST")
    val savedEntities = serializer.saveEntities(entities[serializer.mainEntityClass] as Collection<E>, entities, writer)
    return savedEntities.filter { it.entitySource != serializer.entitySource }.map { Pair(it, serializer.entitySource) }
  }

  private fun <E : TypedEntity> createSerializersForDirectoryEntities(factory: JpsDirectoryEntitiesSerializerFactory<E>, entities: List<TypedEntity>)
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
          else createFileInDirectorySource(VirtualFileUrlManager.fromUrl(factory.directoryUrl), fileName)
        Pair(factory.createSerializer("${factory.directoryUrl}/$fileName", source), entityMap)
      }
  }

  private fun <E : TypedEntity> saveEntitiesList(it: JpsFileSerializerFactory<E>, storage: TypedEntityStorage, writer: JpsFileContentWriter) {
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

  internal class LegacyBridgeModulePathMacroManager(pathMacros: PathMacros, private val moduleFilePath: String) : PathMacroManager(pathMacros) {
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
