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
import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.ide.IdeUiEntitySource
import com.intellij.workspace.ide.JpsFileEntitySource
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
  val mainEntityClass: Class<E>
  fun loadEntities(builder: TypedEntityStorageBuilder, reader: JpsFileContentReader)
  fun saveEntities(mainEntities: Collection<E>, entities: Map<Class<out TypedEntity>, List<TypedEntity>>, writer: JpsFileContentWriter): List<TypedEntity>
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
  val componentName: String

  /** Returns a serializer for a file located in [directoryUrl] directory*/
  fun createSerializer(fileUrl: String): JpsFileEntitiesSerializer<E>

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
  fun createSerializers(reader: JpsFileContentReader): List<JpsFileEntitiesSerializer<E>>
  fun createSerializer(source: JpsFileEntitySource): JpsFileEntitiesSerializer<E>
  fun saveEntitiesList(entities: Sequence<E>, writer: JpsFileContentWriter)
}

/**
 *  Represents set of serializers corresponding to a project.
 */
class JpsEntitiesSerializerFactories(val entityTypeSerializers: List<JpsFileEntityTypeSerializer<*>>,
                                     val directorySerializersFactories: List<JpsDirectoryEntitiesSerializerFactory<*>>,
                                     val fileSerializerFactories: List<JpsFileSerializerFactory<*>>) {
  fun createSerializers(reader: JpsFileContentReader): JpsEntitiesSerializationData {
    val serializerToFileFactory = BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsFileSerializerFactory<*>>()
    for (factory in fileSerializerFactories) {
      factory.createSerializers(reader).associateWithTo(serializerToFileFactory) { factory }
    }
    val serializerToDirectoryFactory = BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsDirectoryEntitiesSerializerFactory<*>>()
    for (factory in directorySerializersFactories) {
      createDirectorySerializers(factory).associateWithTo(serializerToDirectoryFactory) { factory }
    }
    val allFileSerializers = entityTypeSerializers + serializerToDirectoryFactory.keys + serializerToFileFactory.keys
    return JpsEntitiesSerializationData(allFileSerializers, directorySerializersFactories, fileSerializerFactories,
                                        serializerToDirectoryFactory, serializerToFileFactory, entityTypeSerializers)
  }

  private fun createDirectorySerializers(factory: JpsDirectoryEntitiesSerializerFactory<*>): List<JpsFileEntitiesSerializer<*>> {
    val files = JpsPathUtil.urlToFile(factory.directoryUrl).listFiles { file: File -> file.extension == "xml" && file.isFile }
                ?: return emptyList()
    return files.map { factory.createSerializer("${factory.directoryUrl}/${it.name}") }
  }
}

data class JpsConfigurationFilesChange(val addedFileUrls: Collection<String>, val removedFileUrls: Collection<String>, val changedFileUrls: Collection<String>)

class JpsEntitiesSerializationData(fileSerializers: List<JpsFileEntitiesSerializer<*>>,
                                   directorySerializersFactories: List<JpsDirectoryEntitiesSerializerFactory<*>>,
                                   fileSerializerFactories: List<JpsFileSerializerFactory<*>>,
                                   internal val serializerToDirectoryFactory: BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsDirectoryEntitiesSerializerFactory<*>>,
                                   internal val serializerToFileFactory: BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsFileSerializerFactory<*>>,
                                   private val entityTypeSerializers: List<JpsFileEntityTypeSerializer<*>>) {
  internal val fileSerializersByUrl = MultiMap.create<String, JpsFileEntitiesSerializer<*>>().apply {
    fileSerializers.forEach {
      putValue(it.entitySource.file.url, it)
    }
  }
  internal val directorySerializerFactoriesByUrl = directorySerializersFactories.associateBy { it.directoryUrl }
  internal val fileSerializerFactoriesByUrl = fileSerializerFactories.associateBy { it.fileUrl }

  fun reloadFromChangedFiles(change: JpsConfigurationFilesChange,
                             reader: JpsFileContentReader): Pair<Set<EntitySource>, TypedEntityStorageBuilder> {
    val obsoleteSerializers = ArrayList<JpsFileEntitiesSerializer<*>>()
    val newFileSerializers = ArrayList<JpsFileEntitiesSerializer<*>>()
    for (addedFileUrl in change.addedFileUrls) {
      val factory = directorySerializerFactoriesByUrl[PathUtil.getParentPath(addedFileUrl)]
      val newFileSerializer = factory?.createSerializer(addedFileUrl)
      if (newFileSerializer != null) {
        newFileSerializers.add(newFileSerializer)
        serializerToDirectoryFactory[newFileSerializer] = factory
      }
    }

    for (changedUrl in change.changedFileUrls) {
      val serializerFactory = fileSerializerFactoriesByUrl[changedUrl]
      if (serializerFactory != null) {
        val newSerializers = serializerFactory.createSerializers(reader)
        val oldSerializers: List<JpsFileEntitiesSerializer<*>> = serializerToFileFactory.getKeysByValue(serializerFactory) ?: emptyList()
        serializerToFileFactory.removeValue(serializerFactory)
        newSerializers.associateWithTo(serializerToFileFactory) { serializerFactory }
        obsoleteSerializers.addAll(oldSerializers - newSerializers)
        newFileSerializers.addAll(newSerializers - oldSerializers)
      }
    }

    for (newSerializer in newFileSerializers) {
      fileSerializersByUrl.putValue(newSerializer.entitySource.file.url, newSerializer)
    }
    for (obsoleteSerializer in obsoleteSerializers) {
      fileSerializersByUrl.remove(obsoleteSerializer.entitySource.file.url, obsoleteSerializer)
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

    saveEntities(storage, fileSerializersByUrl.values().mapTo(HashSet()) { it.entitySource }, writer)
  }

  fun saveEntities(storage: TypedEntityStorage, affectedSources: Set<EntitySource>, writer: JpsFileContentWriter): List<Pair<TypedEntity, JpsFileEntitySource>> {
    val entitiesToSave = storage.entitiesBySource { it in affectedSources }
    val obsoleteSources = affectedSources - entitiesToSave.keys
    val affectedFileFactories = HashSet<JpsFileSerializerFactory<*>>()
    for (source in obsoleteSources) {
      if (source is JpsFileEntitySource) {
        val fileUrl = source.file.url
        val obsoleteSerializers = fileSerializersByUrl.remove(fileUrl)
        obsoleteSerializers?.mapNotNullTo(affectedFileFactories) { serializerToFileFactory.remove(it) }
        obsoleteSerializers?.forEach {
          val directoryFactory = serializerToDirectoryFactory.remove(it)
          if (directoryFactory != null) {
            writer.saveComponent(fileUrl, directoryFactory.componentName, null)
          }
        }
      }
    }

    entitiesToSave.forEach { (source, entities) ->
      if (source is JpsFileEntitySource && source.file.url !in fileSerializersByUrl.keySet()) {
        fileSerializerFactoriesByUrl.values.forEach { factory ->
          if (factory.entityClass in entities) {
            val newSerializer = factory.createSerializer(source)
            fileSerializersByUrl.putValue(source.file.url, newSerializer)
            serializerToFileFactory[newSerializer] = factory
            affectedFileFactories.add(factory)
          }
        }
      }
    }

    val serializersToRun = ArrayList<Pair<JpsFileEntitiesSerializer<*>, Map<Class<out TypedEntity>, List<TypedEntity>>>>()
    entitiesToSave.forEach { (source, entities) ->
      if (source is JpsFileEntitySource) {
        val serializers = fileSerializersByUrl[source.file.url]
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

    directorySerializerFactoriesByUrl.values.forEach { factory ->
      val added = newEntities[factory.entityClass]
      if (added != null) {
        val newSerializers = createSerializersForDirectoryEntities(factory, added)
        newSerializers.forEach {
          serializerToDirectoryFactory[it.first] = factory
          fileSerializersByUrl.putValue(it.first.entitySource.file.url, it.first)
        }
        serializersToRun.addAll(newSerializers)
      }
    }

    for (serializer in entityTypeSerializers) {
      if (serializer.mainEntityClass in newEntities ||
        entitiesToSave.any { serializer.mainEntityClass in it.value }) {
        serializersToRun.add(Pair(serializer, mapOf(serializer.mainEntityClass to getFilteredEntitiesForSerializer(serializer, storage))))
      }
    }

    return serializersToRun.flatMap {
      saveEntitiesBySerializer(it.first, it.second, writer)
    }
  }

  private fun <E : TypedEntity> getFilteredEntitiesForSerializer(serializer: JpsFileEntityTypeSerializer<E>, storage: TypedEntityStorage): List<E> {
    return storage.entities(serializer.mainEntityClass.kotlin).filter(serializer.entityFilter).toList()
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
      PathUtil.getFileName(it.entitySource.file.url)
    })
    return entities.map {
      @Suppress("UNCHECKED_CAST")
      val fileName = nameGenerator.generateUniqueName(factory.getDefaultFileName(it as E), "", ".xml")
      val entityMap = mapOf<Class<out TypedEntity>, List<TypedEntity>>(factory.entityClass to listOf(it))
      Pair(factory.createSerializer("${factory.directoryUrl}/$fileName"), entityMap)
    }
  }

  private fun <E : TypedEntity> saveEntitiesList(it: JpsFileSerializerFactory<E>, storage: TypedEntityStorage, writer: JpsFileContentWriter) {
    it.saveEntitiesList(storage.entities(it.entityClass.kotlin), writer)
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

internal class JpsFileContentWriterImpl : JpsFileContentWriter {
  val urlToComponents = LinkedHashMap<String, LinkedHashMap<String, Element>>()
  val filesToRemove = LinkedHashSet<String>()

  override fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?) {
    if (componentTag != null) {
      urlToComponents.computeIfAbsent(fileUrl) { LinkedHashMap() }[componentName] = componentTag
    }
    else if (PathUtil.getFileName(PathUtil.getParentPath(PathUtil.getParentPath(fileUrl))) == ".idea") {
      filesToRemove.add(fileUrl)
    }
  }
}