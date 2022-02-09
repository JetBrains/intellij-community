// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.diagnostic.AttachmentFactory
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.ModulePathMacroManager
import com.intellij.openapi.components.impl.ProjectPathMacroManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.module.impl.ModulePath
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.Function
import com.intellij.util.PathUtil
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.impl.reportErrorAndAttachStorage
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.util.JpsPathUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ForkJoinTask
import kotlin.streams.toList

class JpsProjectSerializersImpl(directorySerializersFactories: List<JpsDirectoryEntitiesSerializerFactory<*>>,
                                moduleListSerializers: List<JpsModuleListSerializer>,
                                reader: JpsFileContentReader,
                                private val entityTypeSerializers: List<JpsFileEntityTypeSerializer<*>>,
                                private val configLocation: JpsProjectConfigLocation,
                                private val externalStorageMapping: JpsExternalStorageMapping,
                                private val enableExternalStorage: Boolean,
                                private val virtualFileManager: VirtualFileUrlManager,
                                fileInDirectorySourceNames: FileInDirectorySourceNames) : JpsProjectSerializers {
  private val lock = Any()
  val moduleSerializers = BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsModuleListSerializer>()
  internal val serializerToDirectoryFactory = BidirectionalMap<JpsFileEntitiesSerializer<*>, JpsDirectoryEntitiesSerializerFactory<*>>()
  private val internalSourceToExternal = HashMap<JpsFileEntitySource, JpsFileEntitySource>()
  internal val fileSerializersByUrl = BidirectionalMultiMap<String, JpsFileEntitiesSerializer<*>>()
  internal val fileIdToFileName = Int2ObjectOpenHashMap<String>()

  // Map of module serializer to boolean that defines whenever modules.xml is external or not
  private val moduleSerializerToExternalSourceBool = mutableMapOf<JpsFileEntitiesSerializer<*>, Boolean>()

  init {
    synchronized(lock) {
      for (factory in directorySerializersFactories) {
        createDirectorySerializers(factory, fileInDirectorySourceNames).associateWithTo(serializerToDirectoryFactory) { factory }
      }
      val enabledModuleListSerializers = moduleListSerializers.filter { enableExternalStorage || !it.isExternalStorage }
      val moduleFiles = enabledModuleListSerializers.flatMap { ser ->
        ser.loadFileList(reader, virtualFileManager).map {
          Triple(it.first, it.second, ser.isExternalStorage)
        }
      }
      for ((moduleFile, moduleGroup, isOriginallyExternal) in moduleFiles) {
        val directoryUrl = virtualFileManager.getParentVirtualUrl(moduleFile)!!
        val internalSource =
          bindExistingSource(fileInDirectorySourceNames, ModuleEntity::class.java, moduleFile.fileName, directoryUrl) ?:
          createFileInDirectorySource(directoryUrl, moduleFile.fileName)
        for (moduleListSerializer in enabledModuleListSerializers) {
          val moduleSerializer = moduleListSerializer.createSerializer(internalSource, moduleFile, moduleGroup)
          moduleSerializers[moduleSerializer] = moduleListSerializer
          moduleSerializerToExternalSourceBool[moduleSerializer] = isOriginallyExternal
        }
      }

      val allFileSerializers = entityTypeSerializers.filter { enableExternalStorage || !it.isExternalStorage } +
                               serializerToDirectoryFactory.keys + moduleSerializers.keys
      allFileSerializers.forEach {
        fileSerializersByUrl.put(it.fileUrl.url, it)
      }
    }
  }

  internal val directorySerializerFactoriesByUrl = directorySerializersFactories.associateBy { it.directoryUrl }
  val moduleListSerializersByUrl = moduleListSerializers.associateBy { it.fileUrl }

  private fun createFileInDirectorySource(directoryUrl: VirtualFileUrl, fileName: String): JpsFileEntitySource.FileInDirectory {
    val source = JpsFileEntitySource.FileInDirectory(directoryUrl, configLocation)
    // Don't convert to links[key] = ... because it *may* became autoboxing
    @Suppress("ReplacePutWithAssignment")
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
        stream.filter { path: Path -> PathUtil.getFileExtension(path.toString()) == "xml" && Files.isRegularFile(path) }
          .toList()
      }
      else -> emptyList()
    }
    return files.map {
      val fileName = it.fileName.toString()
      val directoryUrl = virtualFileManager.fromUrl(factory.directoryUrl)
      val entitySource =
        bindExistingSource(fileInDirectorySourceNames, factory.entityClass, fileName, directoryUrl) ?:
        createFileInDirectorySource(directoryUrl, fileName)
      factory.createSerializer("${factory.directoryUrl}/$fileName", entitySource, virtualFileManager)
    }
  }

  private fun bindExistingSource(fileInDirectorySourceNames: FileInDirectorySourceNames,
                                 entityType: Class<out WorkspaceEntity>,
                                 fileName: String,
                                 directoryUrl: VirtualFileUrl): JpsFileEntitySource.FileInDirectory? {
    val source = fileInDirectorySourceNames.findSource(entityType, fileName)
    if (source == null || source.directory != directoryUrl) return null
    @Suppress("ReplacePutWithAssignment")
    fileIdToFileName.put(source.fileNameId, fileName)
    LOG.debug { "bindExistingSource: ${source.fileNameId}=$fileName" }
    return source
  }

  fun findModuleSerializer(modulePath: ModulePath): JpsFileEntitiesSerializer<*>? {
    synchronized(lock) {
      return fileSerializersByUrl.getValues(VfsUtilCore.pathToUrl(modulePath.path)).first()
    }
  }

  override fun reloadFromChangedFiles(change: JpsConfigurationFilesChange,
                                      reader: JpsFileContentReader,
                                      errorReporter: ErrorReporter): Pair<Set<EntitySource>, WorkspaceEntityStorageBuilder> {
    val obsoleteSerializers = ArrayList<JpsFileEntitiesSerializer<*>>()
    val newFileSerializers = ArrayList<JpsFileEntitiesSerializer<*>>()

    val addedFileUrls = change.addedFileUrls.flatMap {
      val file = JpsPathUtil.urlToFile(it)
      if (file.isDirectory) {
        file.list()?.map { fileName -> "$it/$fileName" } ?: emptyList()
      } else listOf(it)
    }.toSet()

    val affectedFileLoaders: LinkedHashSet<JpsFileEntitiesSerializer<*>>
    val changedSources = HashSet<JpsFileEntitySource>()
    synchronized(lock) {
      for (addedFileUrl in addedFileUrls) {
        // The file may already be processed during class initialization
        if (fileSerializersByUrl.containsKey(addedFileUrl)) continue

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
          obsoleteSerializersForFactory.forEach {
            moduleSerializers.remove(it, serializerFactory)
            moduleSerializerToExternalSourceBool.remove(it)
          }
          val newFileSerializersForFactory = newFileUrls.filter { it.first !in oldFileUrls }.map {
            serializerFactory.createSerializer(createFileInDirectorySource(virtualFileManager.getParentVirtualUrl(it.first)!!,
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
        LOG.debug { "remove association for ${it.fileNameId}" }
      }
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    affectedFileLoaders.forEach {
      loadEntitiesAndReportExceptions(it, builder, reader, errorReporter)
    }
    return Pair(changedSources, builder)
  }

  override fun loadAll(reader: JpsFileContentReader,
                       builder: WorkspaceEntityStorageBuilder,
                       errorReporter: ErrorReporter,
                       project: Project?): List<EntitySource> {
    val serializers = synchronized(lock) { fileSerializersByUrl.values.toList() }
    val tasks = serializers.map { serializer ->
      ForkJoinTask.adapt(Callable {
        val myBuilder = WorkspaceEntityStorageBuilder.create()
        loadEntitiesAndReportExceptions(serializer, myBuilder, reader, errorReporter)
        myBuilder
      })
    }

    ForkJoinTask.invokeAll(tasks)
    val builders = tasks.mapNotNull { it.rawResult }
    val sourcesToUpdate = removeDuplicatingEntities(builders, serializers, project)
    val squashedBuilder = squash(builders)
    builder.addDiff(squashedBuilder)
    return sourcesToUpdate
  }

  private fun loadEntitiesAndReportExceptions(serializer: JpsFileEntitiesSerializer<*>,
                                              builder: WorkspaceEntityStorageBuilder,
                                              reader: JpsFileContentReader,
                                              errorReporter: ErrorReporter) {
    fun reportError(e: Exception, url: VirtualFileUrl) {
      errorReporter.reportError(ProjectModelBundle.message("module.cannot.load.error", url.presentableUrl, e.localizedMessage), url)
    }

    try {
      serializer.loadEntities(builder, reader, errorReporter, virtualFileManager)
    }
    catch (e: JDOMException) {
      reportError(e, serializer.fileUrl)
    }
    catch (e: IOException) {
      reportError(e, serializer.fileUrl)
    }
  }

  // Check if the same module is loaded from different source. This may happen in case of two `modules.xml` with the same module.
  // See IDEA-257175
  // This code may be removed if we'll get rid of storing modules.xml and friends in external storage (cache/external_build_system)
  private fun removeDuplicatingEntities(builders: List<WorkspaceEntityStorageBuilder>, serializers: List<JpsFileEntitiesSerializer<*>>, project: Project?): List<EntitySource> {
    if (project == null) return emptyList()

    val modules = mutableMapOf<String, MutableList<Triple<ModuleId, WorkspaceEntityStorageBuilder, JpsFileEntitiesSerializer<*>>>>()
    val libraries = mutableMapOf<LibraryId, MutableList<Pair<WorkspaceEntityStorageBuilder, JpsFileEntitiesSerializer<*>>>>()
    val artifacts = mutableMapOf<ArtifactId, MutableList<Pair<WorkspaceEntityStorageBuilder, JpsFileEntitiesSerializer<*>>>>()
    builders.forEachIndexed { i, builder ->
      if (enableExternalStorage) {
        builder.entities(ModuleEntity::class.java).forEach { module ->
          val moduleId = module.persistentId()
          modules.getOrPut(moduleId.name.toLowerCase(Locale.US)) { ArrayList() }.add(Triple(moduleId, builder, serializers[i]))
        }
      }
      builder.entities(LibraryEntity::class.java).filter { it.tableId == LibraryTableId.ProjectLibraryTableId }.forEach { library ->
        libraries.getOrPut(library.persistentId()) { ArrayList() }.add(builder to serializers[i])
      }
      builder.entities(ArtifactEntity::class.java).forEach { artifact ->
        artifacts.getOrPut(artifact.persistentId()) { ArrayList() }.add(builder to serializers[i])
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
        val originalExternal = moduleSerializerToExternalSourceBool[ser] ?: return@forEachIndexed
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
      reportIssue(project, buildersWithModule.mapTo(HashSet()) { it.first }, buildersWithModule.map { it.third }, leftModuleId)
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
        if (entitySource !is JpsFileEntitySource.FileInDirectory) return@mapNotNull null
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
        if (entitySource !is JpsFileEntitySource.FileInDirectory) return@mapNotNull null
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

  private fun reportIssue(project: Project, moduleIds: Set<ModuleId>, serializers: List<JpsFileEntitiesSerializer<*>>, leftModuleId: Int) {
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
          Path.of(JpsPathUtil.urlToPath(internalModuleListSerializerUrl)
          ), false)
      }
      if (externalModuleListSerializerUrl != null && FileUtil.exists(JpsPathUtil.urlToPath(externalModuleListSerializerUrl))) {
        attachments[externalModuleListSerializerUrl] = AttachmentFactory.createAttachment(
          Path.of(JpsPathUtil.urlToPath(externalModuleListSerializerUrl)), false
        )
      }
      """
        Serializer info #$serializerCounter:
        Is external: ${moduleSerializerToExternalSourceBool[it]}
        fileUrl: ${fileUrl.presentableUrl}
        externalFileUrl: ${externalFileUrl?.presentableUrl}
        internal modules.xml: $internalModuleListSerializerUrl
        external modules.xml: $externalModuleListSerializerUrl
      """.trimIndent()
    }
    val text = """
      |Trying to load multiple modules with the same name.
      |
      |Project: ${project.name}
      |Module: ${moduleIds.map { it.name }}
      |Amount of modules: ${serializers.size}
      |Leave module of nth serializer: $leftModuleId
      |
      |$serializersInfo
    """.trimMargin()

    LOG.error(text, *attachments.values.toTypedArray())
  }

  private fun squash(builders: List<WorkspaceEntityStorageBuilder>): WorkspaceEntityStorageBuilder {
    var result = builders

    while (result.size > 1) {
      result = result.chunked(2) { list ->
        val res = list.first()
        if (list.size == 2) res.addDiff(list.last())
        res
      }
    }

    return result.singleOrNull() ?: WorkspaceEntityStorageBuilder.create()  }

  @TestOnly
  override fun saveAllEntities(storage: WorkspaceEntityStorage, writer: JpsFileContentWriter) {
    moduleListSerializersByUrl.values.forEach {
      saveModulesList(it, storage, writer)
    }

    val allSources = storage.entitiesBySource { true }.keys
    saveEntities(storage, allSources, writer)
  }

  internal fun getActualFileUrl(source: EntitySource): String? {
    val actualFileSource = getActualFileSource(source) ?: return null

    return when (actualFileSource) {
      is JpsFileEntitySource.ExactFile -> actualFileSource.file.url
      is JpsFileEntitySource.FileInDirectory -> {
        val fileName = fileIdToFileName.get(actualFileSource.fileNameId) ?: run {
          // We have a situations when we don't have an association at for `fileIdToFileName` entity source returned from `getActualFileSource`
          // but we have it for the original `JpsImportedEntitySource.internalFile` and base on it we try to calculate actual file url
          if (source is JpsImportedEntitySource && source.internalFile is JpsFileEntitySource.FileInDirectory && source.storedExternally) {
            fileIdToFileName.get((source.internalFile as JpsFileEntitySource.FileInDirectory).fileNameId)?.substringBeforeLast(".")?.let { "$it.xml" }
          } else null
        }
        if (fileName != null) actualFileSource.directory.url + "/" + fileName else null
      }
      else -> error("Unexpected implementation of JpsFileEntitySource: ${actualFileSource.javaClass}")
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
      return fileSerializersByUrl.values.filterIsInstance<ModuleImlFileEntitiesSerializer>().mapTo(LinkedHashSet()) { it.modulePath }.toList()
    }
  }

  override fun saveEntities(storage: WorkspaceEntityStorage, affectedSources: Set<EntitySource>, writer: JpsFileContentWriter) {
    val affectedModuleListSerializers = HashSet<JpsModuleListSerializer>()
    val serializersToRun = HashMap<JpsFileEntitiesSerializer<*>, MutableMap<Class<out WorkspaceEntity>, MutableSet<WorkspaceEntity>>>()

    synchronized(lock) {
      if (LOG.isTraceEnabled) {
        LOG.trace("save entities; current serializers (${fileSerializersByUrl.values.size}):")
        fileSerializersByUrl.values.forEach {
          LOG.trace(it.toString())
        }
      }
      val affectedEntityTypeSerializers = HashSet<JpsFileEntityTypeSerializer<*>>()

      fun processObsoleteSource(fileUrl: String, deleteModuleFile: Boolean) {
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

      val sourcesStoredInternally = affectedSources.asSequence().filterIsInstance<JpsImportedEntitySource>()
        .filter { !it.storedExternally }
        .associateBy { it.internalFile }
      val internalSourcesOfCustomModuleEntitySources = affectedSources.mapNotNullTo(HashSet()) { (it as? CustomModuleEntitySource)?.internalSource }

      /* Entities added via JPS and imported entities stored in internal storage must be passed to serializers together, otherwise incomplete
         data will be stored.
         It isn't necessary to save entities stored in external storage when their internal parts are affected, but add them to the list
         to ensure that obsolete *.iml files will be removed if their modules are stored in external storage.
      */
      val entitiesToSave = storage.entitiesBySource { source ->
        source in affectedSources
        || source in sourcesStoredInternally
        || source is JpsImportedEntitySource && source.internalFile in affectedSources
        || source in internalSourcesOfCustomModuleEntitySources
        || source is CustomModuleEntitySource && source.internalSource in affectedSources
      }
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
            source is JpsImportedEntitySource && source.storedExternally -> sourcesStoredInternally[source.internalFile]
            source is JpsImportedEntitySource && !source.storedExternally -> sourcesStoredExternally[source.internalFile]
            source is JpsFileEntitySource -> sourcesStoredExternally[source]
            else -> null
          }
          // When user removes module from project we don't delete corresponding *.iml file located under project directory by default
          // (because it may be included in other projects). However we do remove the module file if module actually wasn't removed, just
          // its storage has been changed, e.g. if module was marked as imported from external system, or the place where module imported
          // from external system was changed, or part of a module configuration is imported from external system and data stored in *.iml
          // file was removed.
          val deleteObsoleteFile = source in internalSourceConvertedToImported || (affectedImportedSourceStoredExternally != null &&
                                                                                   affectedImportedSourceStoredExternally !in obsoleteSources)
          processObsoleteSource(fileUrl, deleteObsoleteFile)
          val actualSource = if (source is JpsImportedEntitySource && !source.storedExternally) source.internalFile else source
          if (actualSource is JpsFileEntitySource.FileInDirectory) {
            fileIdToFileName.remove(actualSource.fileNameId)
            LOG.debug { "remove association for obsolete source $actualSource" }
          }
        }
      }

      fun processNewlyAddedDirectoryEntities(entitiesMap: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>) {
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

      entitiesToSave.forEach { (source, entities) ->
        val actualFileSource = getActualFileSource(source)
        if (actualFileSource is JpsFileEntitySource.FileInDirectory) {
          val fileNameByEntity = calculateFileNameForEntity(actualFileSource, source, entities)
          val oldFileName = fileIdToFileName.get(actualFileSource.fileNameId)
          if (oldFileName != fileNameByEntity) {
            // Don't convert to links[key] = ... because it *may* became autoboxing
            @Suppress("ReplacePutWithAssignment")
            fileIdToFileName.put(actualFileSource.fileNameId, fileNameByEntity)
            LOG.debug { "update association for ${actualFileSource.fileNameId} to $fileNameByEntity (was $oldFileName)" }
            if (oldFileName != null) {
              processObsoleteSource("${actualFileSource.directory.url}/$oldFileName", true)
            }
            val existingSerializers = fileSerializersByUrl.getValues("${actualFileSource.directory.url}/$fileNameByEntity").filter {
              it in serializerToDirectoryFactory
            }
            if (existingSerializers.isNotEmpty()) {
              val existingSources = existingSerializers.map { it.internalEntitySource }
              val entitiesWithOldSource = storage.entitiesBySource { it in existingSources }
              val entitiesPersistentIds = entitiesWithOldSource.values
                .flatMap { it.values }
                .flatten()
                .filterIsInstance<WorkspaceEntityWithPersistentId>()
                .joinToString(separator = "||") { "$it (PersistentId: ${it.persistentId()})" }
              //technically this is not an error, but cases when different entities have the same default file name are rare so let's report this
              // as error for now to find real cause of IDEA-265327
              val message = """
             |Cannot save entities to $fileNameByEntity because it's already used for other entities;
             |Current entity source: $actualFileSource
             |Old file name: $oldFileName
             |Existing serializers: $existingSerializers
             |Their entity sources: $existingSources
             |Entities with these sources in the storage: ${entitiesWithOldSource.mapValues { (_, value) -> value.values }}
             |Entities with persistent ids: $entitiesPersistentIds
             |Original entities to save: ${entities.values.flatten().joinToString(separator = "||") { "$it (Persistent Id: ${(it as? WorkspaceEntityWithPersistentId)?.persistentId()})" }}
            """.trimMargin()
              reportErrorAndAttachStorage(message, storage)
            }
            if (existingSerializers.isEmpty() || existingSerializers.any { it.internalEntitySource != actualFileSource }) {
              processNewlyAddedDirectoryEntities(entities)
            }
          }
        }
        val url = getActualFileUrl(source)
        val internalSource = getInternalFileSource(source)
        if (url != null && internalSource != null
            && (ModuleEntity::class.java in entities || FacetEntity::class.java in entities || ModuleGroupPathEntity::class.java in entities)) {
          val existingSerializers = fileSerializersByUrl.getValues(url)
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
                val newSerializer = moduleListSerializer.createSerializer(internalSource, virtualFileManager.fromUrl(url), moduleGroup)
                fileSerializersByUrl.put(url, newSerializer)
                moduleSerializers[newSerializer] = moduleListSerializer
                affectedModuleListSerializers.add(moduleListSerializer)
              }
            }
          }
          for (serializer in existingSerializers) {
            val moduleListSerializer = moduleSerializers[serializer]
            val storedExternally = moduleSerializerToExternalSourceBool[serializer]
            if (moduleListSerializer != null && storedExternally != null &&
                (moduleListSerializer.isExternalStorage == storedExternally && !moduleListSerializer.entitySourceFilter(source)
                || moduleListSerializer.isExternalStorage != storedExternally && moduleListSerializer.entitySourceFilter(source))) {
              moduleSerializerToExternalSourceBool[serializer] = !storedExternally
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

    if (affectedModuleListSerializers.isNotEmpty()) {
      moduleListSerializersByUrl.values.forEach {
        saveModulesList(it, storage, writer)
      }
    }

    serializersToRun.forEach {
      saveEntitiesBySerializer(it.key, it.value.mapValues { entitiesMapEntry -> entitiesMapEntry.value.toList() }, storage, writer)
    }
  }

  override fun changeEntitySourcesToDirectoryBasedFormat(builder: WorkspaceEntityStorageBuilder) {
    for (factory in directorySerializerFactoriesByUrl.values) {
      factory.changeEntitySourcesToDirectoryBasedFormat(builder, configLocation)
    }
  }

  private fun mergeSerializerEntitiesMap(existingSerializer2EntitiesMap: HashMap<JpsFileEntitiesSerializer<*>, MutableMap<Class<out WorkspaceEntity>, MutableSet<WorkspaceEntity>>>,
                                         serializer: JpsFileEntitiesSerializer<*>,
                                         entitiesMap: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>) {
    val existingEntitiesMap = existingSerializer2EntitiesMap.getOrPut(serializer) { HashMap() }
    entitiesMap.forEach { (type, entity) ->
      val existingEntities = existingEntitiesMap.getOrPut(type) { HashSet() }
      existingEntities.addAll(entity)
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
    : Map<JpsFileEntitiesSerializer<*>, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>> {
    val serializers = serializerToDirectoryFactory.getKeysByValue(factory) ?: emptyList()
    val nameGenerator = UniqueNameGenerator(serializers, Function {
      PathUtil.getFileName(it.fileUrl.url)
    })
    return entities.asSequence()
      .filter { @Suppress("UNCHECKED_CAST") factory.entityFilter(it as E) }
      .associate { entity ->
        @Suppress("UNCHECKED_CAST")
        val defaultFileName = FileUtil.sanitizeFileName(factory.getDefaultFileName(entity as E))
        val fileName = nameGenerator.generateUniqueName(defaultFileName, "", ".xml")
        val entityMap = mapOf<Class<out WorkspaceEntity>, List<WorkspaceEntity>>(factory.entityClass to listOf(entity))
        val currentSource = entity.entitySource as? JpsFileEntitySource.FileInDirectory

        val source =
          if (currentSource != null && fileIdToFileName.get(currentSource.fileNameId) == fileName) currentSource
          else createFileInDirectorySource(virtualFileManager.fromUrl(factory.directoryUrl), fileName)
        factory.createSerializer("${factory.directoryUrl}/$fileName", source, virtualFileManager) to entityMap
      }
  }

  private fun saveModulesList(it: JpsModuleListSerializer, storage: WorkspaceEntityStorage, writer: JpsFileContentWriter) {
    LOG.trace("saving modules list")
    it.saveEntitiesList(storage.entities(ModuleEntity::class.java), writer)
  }

  companion object {
    private val LOG = logger<JpsProjectSerializersImpl>()
  }
}

class CachingJpsFileContentReader(projectBaseDirUrl: String) : JpsFileContentReader {
  private val projectPathMacroManager = ProjectPathMacroManager.createInstance({ JpsPathUtil.urlToPath(projectBaseDirUrl) }, null)
  private val fileContentCache = ConcurrentHashMap<String, Map<String, Element>>()

  override fun loadComponent(fileUrl: String, componentName: String, customModuleFilePath: String?): Element? {
    val content = fileContentCache.computeIfAbsent(fileUrl + customModuleFilePath) {
      loadComponents(fileUrl, customModuleFilePath)
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

internal fun Element.getAttributeValueStrict(name: String): String =
  getAttributeValue(name) ?: throw JDOMException("Expected attribute $name under ${this.name} element")

fun isExternalModuleFile(filePath: String): Boolean {
  val parentPath = PathUtil.getParentPath(filePath)
  return FileUtil.extensionEquals(filePath, "xml") && PathUtil.getFileName(parentPath) == "modules"
         && PathUtil.getFileName(PathUtil.getParentPath(parentPath)) != ".idea"
}

internal fun getInternalFileSource(source: EntitySource) = when (source) {
  is JpsFileDependentEntitySource -> source.originalSource
  is CustomModuleEntitySource -> source.internalSource
  is JpsFileEntitySource -> source
  else -> null
}
