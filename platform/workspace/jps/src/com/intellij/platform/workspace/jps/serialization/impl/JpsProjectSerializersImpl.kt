// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.java.workspace.entities.ArtifactEntity
import com.intellij.java.workspace.entities.ArtifactId
import com.intellij.openapi.diagnostic.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.jps.*
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.SerializationContext
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ConsistencyCheckingDisabler
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.PathUtilRt
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.util.text.UniqueNameGenerator
import io.opentelemetry.api.metrics.Meter
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.util.JpsPathUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors

class JpsProjectSerializersImpl(directorySerializersFactories: List<JpsDirectoryEntitiesSerializerFactory<*>>,
                                moduleListSerializers: List<JpsModuleListSerializer>,
                                context: SerializationContext,
                                private val entityTypeSerializers: List<JpsFileEntityTypeSerializer<*>>,
                                private val configLocation: JpsProjectConfigLocation,
                                private val externalStorageMapping: JpsExternalStorageMapping,
                                private val enableExternalStorage: Boolean) : JpsProjectSerializers {
  private val lock = Any()
  val moduleSerializers = BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsModuleListSerializer>()
  internal val serializerToDirectoryFactory = BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsDirectoryEntitiesSerializerFactory<*>>()
  private val internalSourceToExternal = HashMap<JpsFileEntitySource, JpsFileEntitySource>()
  internal val fileSerializersByUrl = BidirectionalMultiMap<String, JpsFileEntitiesSerializer<*>>()
  private val virtualFileManager: VirtualFileUrlManager = context.virtualFileUrlManager
  internal val fileIdToFileName = Int2ObjectOpenHashMap<String>()

  // Map of module serializer to boolean that defines whenever modules.xml is external or not
  private val moduleSerializerToExternalSourceBool = HashMap<JpsFileEntitiesSerializer<*>, Boolean>()

  private val additionalModuleRelatedEntities: List<Class<out WorkspaceEntity>>

  init {
    synchronized(lock) {
      for (factory in directorySerializersFactories) {
        createDirectorySerializers(factory, context.fileInDirectorySourceNames).associateWithTo(serializerToDirectoryFactory) { factory }
      }
      val enabledModuleListSerializers = moduleListSerializers.filter { enableExternalStorage || !it.isExternalStorage }
      val moduleFiles = enabledModuleListSerializers.flatMap { ser ->
        ser.loadFileList(context.fileContentReader, virtualFileManager).map {
          Triple(it.first, it.second, ser.isExternalStorage)
        }
      }
      for ((moduleFile, moduleGroup, isOriginallyExternal) in moduleFiles) {
        val directoryUrl = moduleFile.parent!!
        val internalSource =
          bindExistingSource(context.fileInDirectorySourceNames, ModuleEntity::class.java, moduleFile.fileName, directoryUrl)
          ?: createFileInDirectorySource(directoryUrl, moduleFile.fileName)
        for (moduleListSerializer in enabledModuleListSerializers) {
          val moduleSerializer = moduleListSerializer.createSerializer(internalSource, moduleFile, moduleGroup)
          moduleSerializers.put(moduleSerializer, moduleListSerializer)
          moduleSerializerToExternalSourceBool.put(moduleSerializer, isOriginallyExternal)
        }
      }

      val allFileSerializers = entityTypeSerializers.filter { enableExternalStorage || !it.isExternalStorage } +
                               serializerToDirectoryFactory.keys + moduleSerializers.keys
      for (serializer in allFileSerializers) {
        fileSerializersByUrl.put(serializer.fileUrl.url, serializer)
      }

      additionalModuleRelatedEntities = context.customFacetRelatedEntitySerializers.map { it.rootEntityType }
    }
  }

  internal val directorySerializerFactoriesByUrl = directorySerializersFactories.associateBy { it.directoryUrl }
  val moduleListSerializersByUrl = moduleListSerializers.associateBy { it.fileUrl }

  private fun createFileInDirectorySource(directoryUrl: VirtualFileUrl, fileName: String): JpsProjectFileEntitySource.FileInDirectory {
    val source = JpsProjectFileEntitySource.FileInDirectory(directoryUrl, configLocation)
    // Don't convert to links[key] = ... because it *may* became autoboxing
    fileIdToFileName.put(source.fileNameId, fileName)
    LOG.debug { "createFileInDirectorySource: ${source.fileNameId}=$fileName" }
    return source
  }

  private fun createDirectorySerializers(factory: JpsDirectoryEntitiesSerializerFactory<*>,
                                         fileInDirectorySourceNames: FileInDirectorySourceNames): List<JpsFileEntitiesSerializer<*>> {
    val osPath = JpsPathUtil.urlToOsPath(factory.directoryUrl)
    val libPath = Paths.get(osPath)
    val files = when {
      Files.isDirectory(libPath) -> Files.list(libPath).use { stream ->
        stream.filter { path: Path -> PathUtilRt.getFileExtension(path.toString()) == "xml" && Files.isRegularFile(path) }
          .collect(Collectors.toList())
      }
      else -> emptyList()
    }
    return files.map {
      val fileName = it.fileName.toString()
      val directoryUrl = virtualFileManager.getOrCreateFromUri(factory.directoryUrl)
      val entitySource =
        bindExistingSource(fileInDirectorySourceNames, factory.entityClass, fileName, directoryUrl) ?: createFileInDirectorySource(
          directoryUrl, fileName)
      factory.createSerializer("${factory.directoryUrl}/$fileName", entitySource, virtualFileManager)
    }
  }

  private fun bindExistingSource(fileInDirectorySourceNames: FileInDirectorySourceNames,
                                 entityType: Class<out WorkspaceEntity>,
                                 fileName: String,
                                 directoryUrl: VirtualFileUrl): JpsProjectFileEntitySource.FileInDirectory? {
    val source = fileInDirectorySourceNames.findSource(entityType, fileName)
    if (source == null || source.directory != directoryUrl) return null
    fileIdToFileName.put(source.fileNameId, fileName)
    LOG.debug { "bindExistingSource: ${source.fileNameId}=$fileName" }
    return source
  }

  override fun reloadFromChangedFiles(change: JpsConfigurationFilesChange,
                                      reader: JpsFileContentReader,
                                      unloadedModuleNames: UnloadedModulesNameHolder,
                                      errorReporter: ErrorReporter): ReloadingResult {
    val obsoleteSerializers = ArrayList<JpsFileEntitiesSerializer<*>>()
    val newFileSerializers = ArrayList<JpsFileEntitiesSerializer<*>>()

    val addedFileUrls = change.addedFileUrls.flatMap {
      val file = JpsPathUtil.urlToFile(it)
      if (file.isDirectory) {
        file.list()?.map { fileName -> "$it/$fileName" } ?: emptyList()
      }
      else listOf(it)
    }.toSet()

    val affectedFileLoaders: LinkedHashSet<JpsFileEntitiesSerializer<*>>
    val changedSources = HashSet<JpsFileEntitySource>()
    synchronized(lock) {
      for (addedFileUrl in addedFileUrls) {
        // The file may already be processed during class initialization
        if (fileSerializersByUrl.containsKey(addedFileUrl)) continue

        val factory = directorySerializerFactoriesByUrl[PathUtilRt.getParentPath(addedFileUrl)]
        val newFileSerializer = factory?.createSerializer(addedFileUrl, createFileInDirectorySource(
          virtualFileManager.getOrCreateFromUri(factory.directoryUrl), PathUtilRt.getFileName(addedFileUrl)), virtualFileManager)
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
          obsoleteSerializersForFactory.forEach {
            moduleSerializers.remove(it, serializerFactory)
            moduleSerializerToExternalSourceBool.remove(it)
          }
          val newFileSerializersForFactory = newFileUrls.filter { it.first !in oldFileUrls }.map {
            serializerFactory.createSerializer(createFileInDirectorySource(it.first.parent!!,
                                                                           it.first.fileName), it.first, it.second)
          }
          newFileSerializersForFactory.associateWithTo(moduleSerializerToExternalSourceBool) { serializerFactory.isExternalStorage }
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

      affectedFileLoaders = LinkedHashSet(newFileSerializers)
      addedFileUrls.flatMapTo(affectedFileLoaders) { fileSerializersByUrl.getValues(it) }
      change.changedFileUrls.flatMapTo(affectedFileLoaders) { fileSerializersByUrl.getValues(it) }

      affectedFileLoaders.mapTo(changedSources) { it.internalEntitySource }
      for (fileUrl in change.removedFileUrls) {

        val directorySerializer = directorySerializerFactoriesByUrl[fileUrl]
        if (directorySerializer != null) {
          val serializers = serializerToDirectoryFactory.getKeysByValue(directorySerializer)?.toList() ?: emptyList()
          for (serializer in serializers) {
            fileSerializersByUrl.removeValue(serializer)

            obsoleteSerializers.add(serializer)
            serializerToDirectoryFactory.remove(serializer, directorySerializer)
          }
        }
        else {
          val obsolete = fileSerializersByUrl.getValues(fileUrl)
          fileSerializersByUrl.removeKey(fileUrl)

          obsoleteSerializers.addAll(obsolete)
          obsolete.forEach {
            serializerToDirectoryFactory.remove(it)
          }
        }
      }
      obsoleteSerializers.mapTo(changedSources) { it.internalEntitySource }
      obsoleteSerializers.asSequence().map { it.internalEntitySource }.filterIsInstance(
        JpsProjectFileEntitySource.FileInDirectory::class.java).forEach {
        fileIdToFileName.remove(it.fileNameId)
        LOG.debug { "remove association for ${it.fileNameId}" }
      }
    }

    val builder = MutableEntityStorage.create()
    val orphanage = MutableEntityStorage.create()
    val unloadedEntityBuilder = MutableEntityStorage.create()
    affectedFileLoaders.forEach {
      val unloaded = unloadedModuleNames.isUnloaded((it as? ModuleImlFileEntitiesSerializer)?.modulePath?.moduleName)
      val targetBuilder = if (unloaded) unloadedEntityBuilder else builder
      loadEntitiesAndReportExceptions(it, targetBuilder, orphanage, reader, errorReporter)
    }
    return ReloadingResult(builder, orphanage, unloadedEntityBuilder, changedSources)
  }

  private data class BuilderWithLoadedState(
    @JvmField val builder: MutableEntityStorage,
    @JvmField val orphanage: MutableEntityStorage,
    @JvmField val unloaded: Boolean,
  )

  override suspend fun loadAll(reader: JpsFileContentReader,
                               builder: MutableEntityStorage,
                               orphanageBuilder: MutableEntityStorage,
                               unloadedEntityBuilder: MutableEntityStorage,
                               unloadedModuleNames: UnloadedModulesNameHolder,
                               errorReporter: ErrorReporter
  ): List<EntitySource> = loadEntitiesTimeMs.addMeasuredTime {

    val serializers = synchronized(lock) { fileSerializersByUrl.values.toList() }
    val buildersWithLoadedState = coroutineScope {
      serializers.map { serializer ->
        async {
          val result = MutableEntityStorage.create()
          val orphanage = MutableEntityStorage.create()
          loadEntitiesAndReportExceptions(serializer, result, orphanage, reader, errorReporter)
          val unloaded = unloadedModuleNames.isUnloaded((serializer as? ModuleImlFileEntitiesSerializer)?.modulePath?.moduleName)
          BuilderWithLoadedState(result, orphanage, unloaded)
        }
      }
    }.awaitAll()

    val sourcesToUpdate = removeDuplicatingEntities(buildersWithLoadedState, serializers)
    val loadedBuilders = buildersWithLoadedState.mapNotNull { if (!it.unloaded) it.builder else null }
    builder.applyChangesFrom(squash(loadedBuilders))
    val unloadedBuilders = buildersWithLoadedState.mapNotNull { if (it.unloaded) it.builder else null }
    if (unloadedBuilders.isNotEmpty()) {
      unloadedEntityBuilder.applyChangesFrom(squash(unloadedBuilders))
    }
    orphanageBuilder.applyChangesFrom(squash(buildersWithLoadedState.map { it.orphanage }))

    return@addMeasuredTime sourcesToUpdate
  }

  private fun loadEntitiesAndReportExceptions(serializer: JpsFileEntitiesSerializer<*>,
                                              builder: MutableEntityStorage,
                                              orphanage: MutableEntityStorage,
                                              reader: JpsFileContentReader,
                                              errorReporter: ErrorReporter) {
    fun reportError(e: Exception, url: VirtualFileUrl) {
      errorReporter.reportError(WorkspaceModelJpsBundle.message("module.cannot.load.error", url.presentableUrl, e.localizedMessage), url)
    }

    val newEntities = serializer.loadEntities(reader, errorReporter, virtualFileManager)
    serializer.checkAndAddToBuilder(builder, orphanage, newEntities.data)

    when (newEntities.exception) {
      is JDOMException -> reportError(newEntities.exception, serializer.fileUrl)
      is IOException -> reportError(newEntities.exception, serializer.fileUrl)
      else -> newEntities.exception?.let { throw it }
    }
  }

  // Check if the same module is loaded from different source. This may happen in case of two `modules.xml` with the same module.
  // See IDEA-257175
  // This code may be removed if we'll get rid of storing modules.xml and friends in external storage (cache/external_build_system)
  private fun removeDuplicatingEntities(builders: List<BuilderWithLoadedState>,
                                        serializers: List<JpsFileEntitiesSerializer<*>>): List<EntitySource> {
    val modules = mutableMapOf<String, MutableList<Triple<ModuleId, MutableEntityStorage, JpsFileEntitiesSerializer<*>>>>()
    val libraries = mutableMapOf<LibraryId, MutableList<Pair<MutableEntityStorage, JpsFileEntitiesSerializer<*>>>>()
    val artifacts = mutableMapOf<ArtifactId, MutableList<Pair<MutableEntityStorage, JpsFileEntitiesSerializer<*>>>>()
    builders.forEachIndexed { i, (builder, _) ->
      if (enableExternalStorage) {
        builder.entities(ModuleEntity::class.java).forEach { module ->
          val moduleId = module.symbolicId
          modules.computeIfAbsent(moduleId.name.lowercase(Locale.US)) { ArrayList() }.add(Triple(moduleId, builder, serializers[i]))
        }
      }
      builder.entities(LibraryEntity::class.java).filter { it.tableId == LibraryTableId.ProjectLibraryTableId }.forEach { library ->
        libraries.computeIfAbsent(library.symbolicId) { ArrayList() }.add(builder to serializers[i])
      }
      builder.entities(ArtifactEntity::class.java).forEach { artifact ->
        artifacts.computeIfAbsent(artifact.symbolicId) { ArrayList() }.add(builder to serializers[i])
      }
    }

    val sourcesToUpdate = mutableListOf<EntitySource>()
    for ((_, buildersWithModule) in modules) {
      if (buildersWithModule.size <= 1) continue

      var correctModuleFound = false

      var leftModuleId = 0

      // Leave only first module with "correct" entity source
      // If there is no such module, leave the last one
      buildersWithModule.forEachIndexed { index, (moduleId, builder, ser) ->
        val originalExternal = moduleSerializerToExternalSourceBool.get(ser) ?: return@forEachIndexed
        val moduleEntity = builder.resolve(moduleId)!!
        if (index != buildersWithModule.lastIndex) {
          if (!correctModuleFound && originalExternal) {
            correctModuleFound = true
            leftModuleId = index
          }
          else {
            sourcesToUpdate += moduleEntity.entitySource
            builder.removeEntity(moduleEntity)
          }
        }
        else {
          if (correctModuleFound) {
            sourcesToUpdate += moduleEntity.entitySource
            builder.removeEntity(moduleEntity)
          }
          else {
            leftModuleId = index
          }
        }
      }
      reportIssue(buildersWithModule.mapTo(HashSet()) { it.first }, buildersWithModule.map { it.third }, leftModuleId)
    }
    for ((libraryId, buildersWithSerializers) in libraries) {
      if (buildersWithSerializers.size <= 1) continue
      val defaultFileName = FileUtil.sanitizeFileName(libraryId.name) + ".xml"
      val hasImportedEntity = buildersWithSerializers.any { (builder, _) ->
        builder.resolve(libraryId)!!.entitySource is JpsImportedEntitySource
      }
      val entitiesToRemove = buildersWithSerializers.mapNotNull { (builder, serializer) ->
        val library = builder.resolve(libraryId)!!
        val entitySource = library.entitySource
        if (entitySource !is JpsProjectFileEntitySource.FileInDirectory) return@mapNotNull null
        val fileName = serializer.fileUrl.fileName
        if (fileName != defaultFileName || enableExternalStorage && hasImportedEntity) Triple(builder, library, fileName) else null
      }

      LOG.warn("Multiple configuration files were found for '${libraryId.name}' library.")
      if (entitiesToRemove.isNotEmpty() && entitiesToRemove.size < buildersWithSerializers.size) {
        for ((builder, entity) in entitiesToRemove) {
          sourcesToUpdate.add(entity.entitySource)
          builder.removeEntity(entity)
        }
        LOG.warn("Libraries defined in ${entitiesToRemove.joinToString { it.third }} files will ignored and these files will be removed.")
      }
      else {
        LOG.warn("Cannot determine which configuration file should be ignored: ${buildersWithSerializers.map { it.second }}")
      }
    }
    for ((artifactId, buildersWithSerializers) in artifacts) {
      if (buildersWithSerializers.size <= 1) continue
      val defaultFileName = FileUtil.sanitizeFileName(artifactId.name) + ".xml"
      val hasImportedEntity = buildersWithSerializers.any { (builder, _) ->
        builder.resolve(artifactId)!!.entitySource is JpsImportedEntitySource
      }
      val entitiesToRemove = buildersWithSerializers.mapNotNull { (builder, serializer) ->
        val artifact = builder.resolve(artifactId)!!
        val entitySource = artifact.entitySource
        if (entitySource !is JpsProjectFileEntitySource.FileInDirectory) return@mapNotNull null
        val fileName = serializer.fileUrl.fileName
        if (fileName != defaultFileName || enableExternalStorage && hasImportedEntity) Triple(builder, artifact, fileName) else null
      }

      LOG.warn("Multiple configuration files were found for '${artifactId.name}' artifact.")
      if (entitiesToRemove.isNotEmpty() && entitiesToRemove.size < buildersWithSerializers.size) {
        for ((builder, entity) in entitiesToRemove) {
          sourcesToUpdate.add(entity.entitySource)
          builder.removeEntity(entity)
        }
        LOG.warn("Artifacts defined in ${entitiesToRemove.joinToString { it.third }} files will ignored and these files will be removed.")
      }
      else {
        LOG.warn("Cannot determine which configuration file should be ignored: ${buildersWithSerializers.map { it.second }}")
      }
    }
    return sourcesToUpdate
  }

  private fun reportIssue(moduleIds: Set<ModuleId>, serializers: List<JpsFileEntitiesSerializer<*>>, leftModuleId: Int) {
    var serializerCounter = -1
    val attachments = mutableMapOf<String, Attachment>()
    val serializersInfo = serializers.joinToString(separator = "\n\n") {
      serializerCounter++
      it as ModuleImlFileEntitiesSerializer

      val externalFileUrl = it.let {
        it.externalModuleListSerializer?.createSerializer(it.internalEntitySource, it.fileUrl, it.modulePath.group)
      }?.fileUrl
      val fileUrl = it.fileUrl
      val internalModuleListSerializerUrl = it.internalModuleListSerializer?.fileUrl
      val externalModuleListSerializerUrl = it.externalModuleListSerializer?.fileUrl

      if (externalFileUrl != null && FileUtil.exists(JpsPathUtil.urlToPath(externalFileUrl.url))) {
        attachments[externalFileUrl.url] = AttachmentFactory.createAttachment(externalFileUrl.toPath(), false)
      }
      if (FileUtil.exists(JpsPathUtil.urlToPath(fileUrl.url))) {
        attachments[fileUrl.url] = AttachmentFactory.createAttachment(fileUrl.toPath(), false)
      }
      if (internalModuleListSerializerUrl != null && FileUtil.exists(JpsPathUtil.urlToPath(internalModuleListSerializerUrl))) {
        attachments[internalModuleListSerializerUrl] = AttachmentFactory.createAttachment(
          Path.of(JpsPathUtil.urlToPath(internalModuleListSerializerUrl)),
          false
        )
      }
      if (externalModuleListSerializerUrl != null && FileUtil.exists(JpsPathUtil.urlToPath(externalModuleListSerializerUrl))) {
        attachments[externalModuleListSerializerUrl] = AttachmentFactory.createAttachment(
          Path.of(JpsPathUtil.urlToPath(externalModuleListSerializerUrl)), false
        )
      }
      """
        Serializer info #$serializerCounter:
        Is external: ${moduleSerializerToExternalSourceBool.get(it)}
        fileUrl: ${fileUrl.presentableUrl}
        externalFileUrl: ${externalFileUrl?.presentableUrl}
        internal modules.xml: $internalModuleListSerializerUrl
        external modules.xml: $externalModuleListSerializerUrl
      """.trimIndent()
    }
    val text = """
      |Trying to load multiple modules with the same name.
      |
      |Project: ${configLocation.baseDirectoryUrl}
      |Module: ${moduleIds.map { it.name }}
      |Amount of modules: ${serializers.size}
      |Leave module of nth serializer: $leftModuleId
      |
      |$serializersInfo
    """.trimMargin()

    LOG.error(text, *attachments.values.toTypedArray())
  }

  private fun squash(builders: List<MutableEntityStorage>): MutableEntityStorage {
    val target = MutableEntityStorage.create()

    // Consistency check takes a lot of time when we make an "accumulator" storage.
    // To avoid a huge impact on performance metrics, we turn off consistency check for this particular case.
    // However, in general, this place should be refactored: instead of returning builders, we should return entities themselves.
    ConsistencyCheckingDisabler.withDisabled {
      builders.forEach { builder -> target.applyChangesFrom(builder) }
    }
    return target
  }

  @TestOnly
  override fun saveAllEntities(storage: EntityStorage, writer: JpsFileContentWriter) {
    moduleListSerializersByUrl.values.forEach {
      saveModulesList(it, storage, ImmutableEntityStorage.empty(), writer)
    }

    val allSources = storage.entitiesBySource { true }.mapTo(HashSet()) { it.entitySource }
    saveEntities(storage, ImmutableEntityStorage.empty(), allSources, writer)
  }

  internal fun getActualFileUrl(source: EntitySource): String? {
    val actualFileSource = getActualFileSource(source) ?: return null
    return when (actualFileSource) {
      is JpsGlobalFileEntitySource -> actualFileSource.file.url
      is JpsProjectFileEntitySource.ExactFile -> actualFileSource.file.url
      is JpsProjectFileEntitySource.FileInDirectory -> {
        val fileName = fileIdToFileName.get(actualFileSource.fileNameId) ?: run {
          // We have a situations when we don't have an association at for `fileIdToFileName` entity source returned from `getActualFileSource`
          // but we have it for the original `JpsImportedEntitySource.internalFile` and base on it we try to calculate actual file url
          if (source is JpsImportedEntitySource && source.internalFile is JpsProjectFileEntitySource.FileInDirectory && source.storedExternally) {
            fileIdToFileName.get(source.internalFile.fileNameId)?.substringBeforeLast(
              ".")?.let { "$it.xml" }
          }
          else null
        }
        if (fileName != null) actualFileSource.directory.url + "/" + fileName else null
      }
    }
  }

  private fun getActualFileSource(source: EntitySource): JpsFileEntitySource? {
    return when (source) {
      is JpsImportedEntitySource -> {
        if (source.storedExternally) {
          //todo remove obsolete entries
          internalSourceToExternal.getOrPut(source.internalFile) { externalStorageMapping.getExternalSource(source.internalFile) }
        }
        else {
          source.internalFile
        }
      }
      else -> getInternalFileSource(source)
    }
  }

  override fun getAllModulePaths(): List<ModulePath> {
    synchronized(lock) {
      return fileSerializersByUrl.values.filterIsInstance<ModuleImlFileEntitiesSerializer>().mapTo(
        LinkedHashSet()) { it.modulePath }.toList()
    }
  }

  private fun processObsoleteSource(fileUrl: String,
                                    deleteModuleFile: Boolean,
                                    writer: JpsFileContentWriter,
                                    affectedEntityTypeSerializers: HashSet<JpsFileEntityTypeSerializer<*>>,
                                    affectedModuleListSerializers: HashSet<JpsModuleListSerializer>,
                                    storage: EntityStorage) {
    val obsoleteSerializers = fileSerializersByUrl.getValues(fileUrl)
    fileSerializersByUrl.removeKey(fileUrl)
    LOG.trace { "processing obsolete source $fileUrl: serializers = $obsoleteSerializers" }
    obsoleteSerializers.forEach {
      // Clean up module files content
      val moduleListSerializer = moduleSerializers.remove(it)
      if (moduleListSerializer != null) {
        if (deleteModuleFile) {
          moduleListSerializer.deleteObsoleteFile(fileUrl, writer)
        }
        LOG.trace { "affected module list: $moduleListSerializer" }
        affectedModuleListSerializers.add(moduleListSerializer)
      }
      // Remove libraries under `.idea/libraries` folder
      val directoryFactory = serializerToDirectoryFactory.remove(it)
      if (directoryFactory != null) {
        writer.saveComponent(fileUrl, directoryFactory.componentName, null)
      }
      // Remove libraries under `external_build_system/libraries` folder
      if (it in entityTypeSerializers) {
        if (getFilteredEntitiesForSerializer(it as JpsFileEntityTypeSerializer, storage).isEmpty()) {
          it.deleteObsoleteFile(fileUrl, writer)
        }
        else {
          affectedEntityTypeSerializers.add(it)
        }
      }
    }
  }


  private fun processNewlyAddedDirectoryEntities(entitiesMap: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                                                 serializersToRun: HashMap<JpsFileEntitiesSerializer<*>, MutableMap<Class<out WorkspaceEntity>, MutableSet<WorkspaceEntity>>>) {
    directorySerializerFactoriesByUrl.values.forEach { factory ->
      val added = entitiesMap[factory.entityClass]
      if (added != null) {
        val newSerializers = createSerializersForDirectoryEntities(factory, added)
        newSerializers.forEach {
          serializerToDirectoryFactory[it.key] = factory
          fileSerializersByUrl.put(it.key.fileUrl.url, it.key)
        }
        newSerializers.forEach { (serializer, entitiesMap) -> mergeSerializerEntitiesMap(serializersToRun, serializer, entitiesMap) }
      }
    }
  }


  override fun saveEntities(storage: EntityStorage,
                            unloadedEntityStorage: EntityStorage,
                            affectedSources: Set<EntitySource>,
                            writer: JpsFileContentWriter) = saveEntitiesTimeMs.addMeasuredTime {
    val affectedModuleListSerializers = HashSet<JpsModuleListSerializer>()
    val serializersToRun = HashMap<JpsFileEntitiesSerializer<*>, MutableMap<Class<out WorkspaceEntity>, MutableSet<WorkspaceEntity>>>()

    synchronized(lock) {
      saveEntities(affectedSources, storage, unloadedEntityStorage, writer, affectedModuleListSerializers, serializersToRun)
    }

    if (affectedModuleListSerializers.isNotEmpty()) {
      moduleListSerializersByUrl.values.forEach {
        saveModulesList(it, storage, unloadedEntityStorage, writer)
      }
    }

    serializersToRun.forEach {
      saveEntitiesBySerializer(it.key, it.value.mapValues { entitiesMapEntry -> entitiesMapEntry.value.toList() }, storage, writer)
    }
  }

  private fun saveEntities(affectedSources: Set<EntitySource>,
                           storage: EntityStorage,
                           unloadedEntityStorage: EntityStorage,
                           writer: JpsFileContentWriter,
                           affectedModuleListSerializers: HashSet<JpsModuleListSerializer>,
                           serializersToRun: HashMap<JpsFileEntitiesSerializer<*>, MutableMap<Class<out WorkspaceEntity>, MutableSet<WorkspaceEntity>>>) {
    if (LOG.isTraceEnabled) {
      LOG.trace("save entities; current serializers (${fileSerializersByUrl.values.size}):")
      fileSerializersByUrl.values.forEach {
        LOG.trace(it.toString())
      }
    }
    val affectedEntityTypeSerializers = HashSet<JpsFileEntityTypeSerializer<*>>()

    val sourcesStoredInternally = affectedSources.asSequence().filterIsInstance<JpsImportedEntitySource>()
      .filter { !it.storedExternally }
      .associateBy { it.internalFile }
    val internalSourcesOfCustomModuleEntitySources = affectedSources.mapNotNullTo(
      HashSet()) { (it as? CustomModuleEntitySource)?.internalSource }

    /* Entities added via JPS and imported entities stored in internal storage must be passed to serializers together, otherwise incomplete
         data will be stored.
         It isn't necessary to save entities stored in external storage when their internal parts are affected, but add them to the list
         to ensure that obsolete *.iml files will be removed if their modules are stored in external storage.
      */
    val entitySourceFilter = { source: EntitySource ->
      source in affectedSources
      || source in sourcesStoredInternally
      || source is JpsImportedEntitySource && source.internalFile in affectedSources
      || source in internalSourcesOfCustomModuleEntitySources
      || source is CustomModuleEntitySource && source.internalSource in affectedSources
    }
    val loadedEntitiesToSave = storage.entitiesBySource(entitySourceFilter)
      .groupBy { it.entitySource }
      .mapValues { (_, value) -> value.groupBy { it.getEntityInterface() } }
    val unloadedEntitiesToSave = unloadedEntityStorage.entitiesBySource(entitySourceFilter)
      .groupBy { it.entitySource }
      .mapValues { (_, value) -> value.groupBy { it.getEntityInterface() } }
    //don't copy the map in the most common case (when there are no unloaded entities)
    val entitiesToSave = if (unloadedEntitiesToSave.isNotEmpty()) loadedEntitiesToSave + unloadedEntitiesToSave
    else loadedEntitiesToSave
    if (LOG.isTraceEnabled) {
      LOG.trace("Affected sources: $affectedSources")
      LOG.trace("Entities to save:")
      for ((source, entities) in entitiesToSave) {
        LOG.trace(" $source: $entities")
      }
    }
    val internalSourceConvertedToImported = affectedSources.filterIsInstance<JpsImportedEntitySource>().mapTo(HashSet()) {
      it.internalFile
    }
    val sourcesStoredExternally = entitiesToSave.keys.asSequence().filterIsInstance<JpsImportedEntitySource>()
      .filter { it.storedExternally }
      .associateBy { it.internalFile }

    val obsoleteSources = affectedSources - entitiesToSave.keys
    LOG.trace { "Obsolete sources: $obsoleteSources" }
    for (source in obsoleteSources) {
      val fileUrl = getActualFileUrl(source)
      if (fileUrl != null) {
        val affectedImportedSourceStoredExternally = when {
          source is JpsFileEntitySource -> sourcesStoredExternally[source]
          else -> null
        }
        // When user removes module from the project, we don't delete corresponding *.iml file located under project directory by default
        // because it may be included in other projects.
        //
        // However, we do remove the module file if:
        // - Module is imported from the external build system (like maven).
        // - Module actually wasn't removed, just its storage has been changed, e.g: if module was marked as imported from the external system
        //   (aka mavenize module).
        // - Imported module had user-configured information (like custom content roots). This additional information is stored in the local
        //    `.iml` file, and the `.iml` should be removed in case all custom elements are removed.
        // - TO DO: Fill new cases if found!
        val deleteObsoleteFile = shouldDeleteImportedFile(source, fileUrl) ||
                                 source in internalSourceConvertedToImported ||
                                 (affectedImportedSourceStoredExternally != null && affectedImportedSourceStoredExternally !in obsoleteSources)
        processObsoleteSource(fileUrl, deleteObsoleteFile, writer, affectedEntityTypeSerializers, affectedModuleListSerializers, storage)
        val actualSource = if (source is JpsImportedEntitySource && !source.storedExternally) source.internalFile else source
        if (actualSource is JpsProjectFileEntitySource.FileInDirectory) {
          fileIdToFileName.remove(actualSource.fileNameId)
          LOG.debug { "remove association for obsolete source $actualSource" }
        }
      }
    }

    entitiesToSave.forEach { (source, entities) ->
      val actualFileSource = getActualFileSource(source)
      if (actualFileSource is JpsProjectFileEntitySource.FileInDirectory) {
        val fileNameByEntity = calculateFileNameForEntity(actualFileSource, source, storage, entities)
        val oldFileName = fileIdToFileName.get(actualFileSource.fileNameId)
        if (oldFileName != fileNameByEntity) {
          // Don't convert to links[key] = ... because it *may* became autoboxing
          fileIdToFileName.put(actualFileSource.fileNameId, fileNameByEntity)
          LOG.debug { "update association for ${actualFileSource.fileNameId} to $fileNameByEntity (was $oldFileName)" }
          if (oldFileName != null) {
            processObsoleteSource("${actualFileSource.directory.url}/$oldFileName", true,
                                  writer, affectedEntityTypeSerializers, affectedModuleListSerializers, storage)
          }
          val existingSerializers = fileSerializersByUrl.getValues("${actualFileSource.directory.url}/$fileNameByEntity").filter {
            it in serializerToDirectoryFactory
          }
          if (existingSerializers.isNotEmpty()) {
            val existingSources = existingSerializers.map { it.internalEntitySource }
            val entitiesWithOldSource = storage.entitiesBySource { it in existingSources } + unloadedEntityStorage.entitiesBySource { it in existingSources }
            val entitiesSymbolicIds = entitiesWithOldSource
              .filterIsInstance<WorkspaceEntityWithSymbolicId>()
              .joinToString(separator = "||") { "$it (SymbolicId: ${it.symbolicId})" }
            //technically this is not an error, but cases when different entities have the same default file name are rare so let's report this
            // as error for now to find real cause of IDEA-265327
            val message = """
               |Cannot save entities to $fileNameByEntity because it's already used for other entities;
               |Current entity source: $actualFileSource
               |Old file name: $oldFileName
               |Existing serializers: $existingSerializers
               |Their entity sources: $existingSources
               |Entities with these sources in the storage: ${entitiesWithOldSource.toList()}
               |Entities with symbolic ids: $entitiesSymbolicIds
               |Original entities to save: ${
              entities.values.flatten().joinToString(
                separator = "||") { "$it (Persistent Id: ${(it as? WorkspaceEntityWithSymbolicId)?.symbolicId})" }
            }
              """.trimMargin()
            LOG.error(message)
          }
          if (existingSerializers.isEmpty() || existingSerializers.any { it.internalEntitySource != actualFileSource }) {
            processNewlyAddedDirectoryEntities(entities, serializersToRun)
          }
        }
      }
      val url = getActualFileUrl(source)
      val internalSource = getInternalFileSource(source)
      if (url != null && internalSource != null
          && (ModuleEntity::class.java in entities
              || ModuleGroupPathEntity::class.java in entities
              || ContentRootEntity::class.java in entities
              || SourceRootEntity::class.java in entities
              || ExcludeUrlEntity::class.java in entities
              || additionalModuleRelatedEntities.any { it in entities }
             )) {

        val existingSerializers = fileSerializersByUrl.getValues(
          url).toSet() // Additional toSet to avoid concurrent modification IDEA-316949

        //region Process change of the module group (deprecated mechanism of grouping modules)
        val moduleGroup = (entities[ModuleGroupPathEntity::class.java]?.first() as? ModuleGroupPathEntity)?.path?.joinToString("/")
        if (existingSerializers.isEmpty() || existingSerializers.any { it is ModuleImlFileEntitiesSerializer && it.modulePath.group != moduleGroup }) {
          moduleListSerializersByUrl.values.forEach { moduleListSerializer ->
            if (moduleListSerializer.entitySourceFilter(source)) {
              if (existingSerializers.isNotEmpty()) {
                existingSerializers.forEach {
                  if (it is ModuleImlFileEntitiesSerializer) {
                    moduleSerializers.remove(it)
                    fileSerializersByUrl.remove(url, it)
                  }
                }
              }
              val newSerializer = moduleListSerializer.createSerializer(internalSource, virtualFileManager.getOrCreateFromUri(url), moduleGroup)
              fileSerializersByUrl.put(url, newSerializer)
              moduleSerializers[newSerializer] = moduleListSerializer
              affectedModuleListSerializers.add(moduleListSerializer)
            }
          }
        }
        //endregion

        for (serializer in existingSerializers) {
          val moduleListSerializer = moduleSerializers.get(serializer)
          val storedExternally = moduleSerializerToExternalSourceBool.get(serializer)
          if (moduleListSerializer != null && storedExternally != null &&
              (moduleListSerializer.isExternalStorage == storedExternally && !moduleListSerializer.entitySourceFilter(source)
               || moduleListSerializer.isExternalStorage != storedExternally && moduleListSerializer.entitySourceFilter(source))) {
            moduleSerializerToExternalSourceBool.put(serializer, !storedExternally)
            affectedModuleListSerializers.add(moduleListSerializer)
          }
        }
      }
    }

    entitiesToSave.forEach { (source, entities) ->
      val serializers = fileSerializersByUrl.getValues(getActualFileUrl(source))
      serializers.filter { it !is JpsFileEntityTypeSerializer }.forEach { serializer ->
        mergeSerializerEntitiesMap(serializersToRun, serializer, entities)
      }
    }

    for (serializer in entityTypeSerializers) {
      if (entitiesToSave.any { serializer.mainEntityClass in it.value } || serializer in affectedEntityTypeSerializers) {
        val entitiesMap = mutableMapOf(serializer.mainEntityClass to getFilteredEntitiesForSerializer(serializer, storage))
        serializer.additionalEntityTypes.associateWithTo(entitiesMap) {
          storage.entities(it).toList()
        }
        fileSerializersByUrl.put(serializer.fileUrl.url, serializer)
        mergeSerializerEntitiesMap(serializersToRun, serializer, entitiesMap)
      }
    }
  }

  private fun shouldDeleteImportedFile(source: EntitySource, fileUrl: String): Boolean {
    // Always remove files that were generated during importing from external build systems like gradle, maven, sbt.
    // Example: If module was imported from gradle, and then it was removed in gradle, we don't need to keep the `.iml` file for this module
    return source is JpsImportedEntitySource
           // Except a corner case: We load `iml` files with broken structure that has duplicated modules.
           // This may happen if we have two `modules.xml` files (one in `.idea` and the second in `external_build_system`) and they
           //   both refer to the same `module.iml`. The duplicated module entity will be automatically removed, but the `module.iml` file
           //   should not be removed as it contains information about the module.
           && fileSerializersByUrl.getValues(fileUrl).size == 1
  }

  override fun changeEntitySourcesToDirectoryBasedFormat(builder: MutableEntityStorage) {
    for (factory in directorySerializerFactoriesByUrl.values) {
      factory.changeEntitySourcesToDirectoryBasedFormat(builder, configLocation)
    }
  }

  private fun mergeSerializerEntitiesMap(existingSerializer2EntitiesMap: HashMap<JpsFileEntitiesSerializer<*>, MutableMap<Class<out WorkspaceEntity>, MutableSet<WorkspaceEntity>>>,
                                         serializer: JpsFileEntitiesSerializer<*>,
                                         entitiesMap: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>) {
    val existingEntitiesMap = existingSerializer2EntitiesMap.computeIfAbsent(serializer) { HashMap() }
    entitiesMap.forEach { (type, entity) ->
      val existingEntities = existingEntitiesMap.computeIfAbsent(type) { HashSet() }
      existingEntities.addAll(entity)
    }
  }

  private fun calculateFileNameForEntity(source: JpsProjectFileEntitySource.FileInDirectory,
                                         originalSource: EntitySource,
                                         storage: EntityStorage,
                                         entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>): String? {
    val directoryFactory = directorySerializerFactoriesByUrl[source.directory.url]
    if (directoryFactory != null) {
      return getDefaultFileNameForEntity(directoryFactory, entities)
    }
    if (ModuleEntity::class.java in entities
        || ContentRootEntity::class.java in entities
        || SourceRootEntity::class.java in entities
        || ExcludeUrlEntity::class.java in entities
        || additionalModuleRelatedEntities.any { it in entities }
    ) {
      val moduleListSerializer = moduleListSerializersByUrl.values.find {
        it.entitySourceFilter(originalSource)
      }
      if (moduleListSerializer != null) {
        return getFileNameForModuleEntity(moduleListSerializer, storage, entities)
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
                                         storage: EntityStorage,
                                         entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>): String? {
    val entity = entities[ModuleEntity::class.java]?.singleOrNull() as? ModuleEntity
    if (entity != null) {
      return moduleListSerializer.getFileName(entity)
    }
    val contentRootEntity = entities[ContentRootEntity::class.java]?.firstOrNull() as? ContentRootEntity
    if (contentRootEntity != null) {
      return moduleListSerializer.getFileName(contentRootEntity.module)
    }
    val sourceRootEntity = entities[SourceRootEntity::class.java]?.firstOrNull() as? SourceRootEntity
    if (sourceRootEntity != null) {
      return moduleListSerializer.getFileName(sourceRootEntity.contentRoot.module)
    }
    val excludeUrlEntity = entities[ExcludeUrlEntity::class.java]?.firstOrNull() as? ExcludeUrlEntity
    val module = excludeUrlEntity?.contentRoot?.module
    if (module != null) {
      return moduleListSerializer.getFileName(module)
    }
    val moduleEntity = additionalModuleRelatedEntities.mapNotNull {
      val moduleId = (entities[it]?.firstOrNull() as? ModuleSettingsBase)?.moduleId ?: return@mapNotNull null
      storage.resolve(moduleId)
    }.firstOrNull() ?: return null
    return moduleListSerializer.getFileName(moduleEntity)
  }

  private fun <E : WorkspaceEntity> getFilteredEntitiesForSerializer(serializer: JpsFileEntityTypeSerializer<E>,
                                                                     storage: EntityStorage): List<E> {
    return storage.entities(serializer.mainEntityClass).filter(serializer.entityFilter).toList()
  }

  private fun <E : WorkspaceEntity> saveEntitiesBySerializer(serializer: JpsFileEntitiesSerializer<E>,
                                                             entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                                                             storage: EntityStorage,
                                                             writer: JpsFileContentWriter) {
    @Suppress("UNCHECKED_CAST")
    serializer.saveEntities(entities[serializer.mainEntityClass] as? Collection<E> ?: emptyList(), entities, storage, writer)
  }

  private fun <E : WorkspaceEntity> createSerializersForDirectoryEntities(factory: JpsDirectoryEntitiesSerializerFactory<E>,
                                                                          entities: List<WorkspaceEntity>)
    : Map<JpsFileEntitiesSerializer<*>, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>> {
    val serializers = serializerToDirectoryFactory.getKeysByValue(factory) ?: emptyList()
    val nameGenerator = UniqueNameGenerator(serializers) {
      PathUtilRt.getFileName(it.fileUrl.url)
    }
    return entities.asSequence()
      .filter { @Suppress("UNCHECKED_CAST") factory.entityFilter(it as E) }
      .associate { entity ->
        @Suppress("UNCHECKED_CAST")
        val defaultFileName = FileUtil.sanitizeFileName(factory.getDefaultFileName(entity as E))
        val fileName = nameGenerator.generateUniqueName(defaultFileName, "", ".xml")
        val entityMap = mapOf<Class<out WorkspaceEntity>, List<WorkspaceEntity>>(factory.entityClass to listOf(entity))
        val currentSource = entity.entitySource as? JpsProjectFileEntitySource.FileInDirectory

        val source =
          if (currentSource != null && fileIdToFileName.get(currentSource.fileNameId) == fileName) currentSource
          else createFileInDirectorySource(virtualFileManager.getOrCreateFromUri(factory.directoryUrl), fileName)
        factory.createSerializer("${factory.directoryUrl}/$fileName", source, virtualFileManager) to entityMap
      }
  }

  private fun saveModulesList(it: JpsModuleListSerializer,
                              storage: EntityStorage,
                              unloadedEntityStorage: EntityStorage,
                              writer: JpsFileContentWriter) {
    LOG.trace("saving modules list")
    it.saveEntityList(storage.entities(ModuleEntity::class.java) + unloadedEntityStorage.entities(ModuleEntity::class.java), writer)
  }

  companion object {
    private val LOG = logger<JpsProjectSerializersImpl>()

    private val loadEntitiesTimeMs = MillisecondsMeasurer()
    private val saveEntitiesTimeMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val loadEntitiesTimeCounter = meter.counterBuilder("jps.project.serializers.load.ms").buildObserver()
      val saveEntitiesTimeCounter = meter.counterBuilder("jps.project.serializers.save.ms").buildObserver()

      meter.batchCallback(
        {
          loadEntitiesTimeCounter.record(loadEntitiesTimeMs.asMilliseconds())
          saveEntitiesTimeCounter.record(saveEntitiesTimeMs.asMilliseconds())
        },
        loadEntitiesTimeCounter, saveEntitiesTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(JpsMetrics.getInstance().meter)
    }
  }
}

internal fun Element.getAttributeValueStrict(name: String): String {
  return getAttributeValue(name) ?: throw JDOMException("Expected attribute $name under ${this.name} element")
}

internal fun Element.getChildTagStrict(name: String): Element {
  return getChild(name) ?: throw JDOMException("Expected tag $name under ${this.name} element")
}

fun isExternalModuleFile(filePath: String): Boolean {
  val parentPath = PathUtilRt.getParentPath(filePath)
  return filePath.endsWith(".xml") && PathUtilRt.getFileName(parentPath) == "modules"
         && PathUtilRt.getFileName(PathUtilRt.getParentPath(parentPath)) != ".idea"
}

internal fun getInternalFileSource(source: EntitySource): JpsFileEntitySource? {
  return when (source) {
    is JpsFileDependentEntitySource -> source.originalSource
    is CustomModuleEntitySource -> source.internalSource
    is JpsFileEntitySource -> source
    else -> null
  }
}
