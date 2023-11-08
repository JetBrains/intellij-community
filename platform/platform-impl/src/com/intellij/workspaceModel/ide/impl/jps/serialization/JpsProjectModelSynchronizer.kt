// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.configurationStore.isFireStorageFileChangedEvent
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.StartUpMeasurer.startActivity
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ProjectLoadingErrorsNotifier
import com.intellij.openapi.module.impl.ModuleManagerEx
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getExternalConfigurationDir
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.backend.workspace.WorkspaceModelUnloadedStorageChangeListener
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMs
import com.intellij.platform.workspace.jps.*
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.serialization.impl.*
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectEntitiesLoader.createProjectSerializers
import com.intellij.platform.workspace.storage.DummyParentEntitySource
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.project.stateStore
import com.intellij.util.PlatformUtils.*
import com.intellij.workspaceModel.ide.EntitiesOrphanage
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.getJpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.*
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.util.JpsPathUtil
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages serialization and deserialization from JPS format (*.iml and *.ipr files, .idea directory) for a workspace model in the IDE.
 */
@Service(Service.Level.PROJECT)
class JpsProjectModelSynchronizer(private val project: Project) : Disposable {
  companion object {
    fun getInstance(project: Project): JpsProjectModelSynchronizer = project.service()

    private val LOG = logger<JpsProjectModelSynchronizer>()

    private val jpsLoadProjectToEmptyStorageTimeMs: AtomicLong = AtomicLong()
    private val reloadProjectEntitiesTimeMs: AtomicLong = AtomicLong()
    private val applyLoadedStorageTimeMs: AtomicLong = AtomicLong()
    private val saveChangedProjectEntitiesTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val jpsLoadProjectToEmptyStorageTimeGauge = meter.gaugeBuilder("jps.load.project.to.empty.storage.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      val reloadProjectEntitiesTimeGauge = meter.gaugeBuilder("jps.reload.project.entities.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      val applyLoadedStorageTimeGauge = meter.gaugeBuilder("jps.apply.loaded.storage.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      val saveChangedProjectEntitiesTimeGauge = meter.gaugeBuilder("jps.save.changed.project.entities.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      meter.batchCallback(
        {
          jpsLoadProjectToEmptyStorageTimeGauge.record(jpsLoadProjectToEmptyStorageTimeMs.get())
          reloadProjectEntitiesTimeGauge.record(reloadProjectEntitiesTimeMs.get())
          applyLoadedStorageTimeGauge.record(applyLoadedStorageTimeMs.get())
          saveChangedProjectEntitiesTimeGauge.record(saveChangedProjectEntitiesTimeMs.get())
        },
        jpsLoadProjectToEmptyStorageTimeGauge, reloadProjectEntitiesTimeGauge,
        applyLoadedStorageTimeGauge, saveChangedProjectEntitiesTimeGauge
      )
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }

  private val incomingChanges = Collections.synchronizedList(ArrayList<JpsConfigurationFilesChange>())
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private lateinit var fileContentReader: JpsFileContentReaderWithCache
  private val serializers = AtomicReference<JpsProjectSerializers?>()
  private val sourcesToSave = Collections.synchronizedSet(HashSet<EntitySource>())
  private var activity: Activity? = null
  private var childActivity: Activity? = null

  fun needToReloadProjectEntities(): Boolean {
    if (StoreReloadManager.getInstance(project).isReloadBlocked() || serializers.get() == null) {
      return false
    }

    synchronized(incomingChanges) {
      return incomingChanges.isNotEmpty()
    }
  }

  suspend fun reloadProjectEntities() {
    val start = System.currentTimeMillis()

    if (StoreReloadManager.getInstance(project).isReloadBlocked()) {
      LOG.debug("Skip reloading because it's blocked")
      return
    }

    val serializers = serializers.get()
    if (serializers == null) {
      LOG.debug("Skip reloading because initial loading wasn't performed yet")
      return
    }

    val changes = getAndResetIncomingChanges()
    if (changes == null) {
      LOG.debug("Skip reloading because there are no changed files")
      return
    }

    LOG.debug { "Reload entities from changed files:\n$changes" }

    val unloadedModuleNameHolder = UnloadedModulesListStorage.getInstance(project).unloadedModuleNameHolder
    val reloadingResult = loadAndReportErrors {
      serializers.reloadFromChangedFiles(changes, fileContentReader, unloadedModuleNameHolder, it)
    }

    fileContentReader.clearCache()
    LOG.debugValues("Changed entity sources", reloadingResult.affectedSources)

    if (reloadingResult.affectedSources.isEmpty() &&
        !reloadingResult.builder.hasChanges() &&
        !reloadingResult.unloadedEntityBuilder.hasChanges() &&
        !reloadingResult.orphanageBuilder.hasChanges()) {
      return
    }

    writeAction {
      val affectedEntityFilter: (EntitySource) -> Boolean = {
        it in reloadingResult.affectedSources
        || (it is JpsImportedEntitySource && !it.storedExternally && it.internalFile in reloadingResult.affectedSources)
        || it is DummyParentEntitySource
      }
      val description = "Reload entities after changes in JPS configuration files"

      // Update builder of unloaded entities
      if (reloadingResult.unloadedEntityBuilder.hasChanges()) {
        WorkspaceModel.getInstance(project).updateUnloadedEntities(description) { builder ->
          builder.replaceBySource(affectedEntityFilter, reloadingResult.unloadedEntityBuilder.toSnapshot())
        }
      }

      val unloadedBuilder = MutableEntityStorage.from(WorkspaceModel.getInstance(project).currentSnapshotOfUnloadedEntities)
      WorkspaceModel.getInstance(project).updateProjectModel(description) { updater ->
        val storage = reloadingResult.builder.toSnapshot()
        updater.replaceBySource(affectedEntityFilter, storage)
        runAutomaticModuleUnloader(updater, unloadedBuilder)
      }
      addUnloadedModuleEntities(unloadedBuilder)
      sourcesToSave.removeAll(reloadingResult.affectedSources)

      // Update orphanage storage
      if (reloadingResult.orphanageBuilder.hasChanges()) {
        EntitiesOrphanage.getInstance(project).update { it.addDiff(reloadingResult.orphanageBuilder) }
      }
    }

    reloadProjectEntitiesTimeMs.addElapsedTimeMs(start)
  }

  private inline fun <T> loadAndReportErrors(action: (ErrorReporter) -> T): T {
    val reporter = IdeErrorReporter(project)
    val result = action(reporter)
    val errors = reporter.errors
    if (errors.isNotEmpty()) {
      ProjectLoadingErrorsNotifier.getInstance(project).registerErrors(errors)
    }
    return result
  }

  private fun registerListener() {
    fun isParentOfStorageFiles(dir: VirtualFile): Boolean {
      if (dir.name == Project.DIRECTORY_STORE_FOLDER) return true
      val grandParent = dir.parent
      return grandParent != null && grandParent.name == Project.DIRECTORY_STORE_FOLDER
    }

    fun isStorageFile(file: VirtualFile): Boolean {
      val fileName = file.name
      if ((FileUtilRt.extensionEquals(fileName, ModuleFileType.DEFAULT_EXTENSION)
           || FileUtilRt.extensionEquals(fileName, ProjectFileType.DEFAULT_EXTENSION)) && !file.isDirectory) return true
      val parent = file.parent
      return parent != null && isParentOfStorageFiles(parent)
    }

    val connection = project.messageBus.connect(this)
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        //todo support move/rename
        //todo optimize: filter events before creating lists
        var addedUrls: MutableList<String>? = null
        var removedUrls: MutableList<String>? = null
        var changedUrls: MutableList<String>? = null
        // JPS model is loaded from *.iml files, files in .idea directory (modules.xml),
        // files from directories in .idea (libraries) and *.ipr file, so we can ignore all other events to speed up processing
        for (event in events) {
          if (isFireStorageFileChangedEvent(event)) {
            when (event) {
              is VFileCreateEvent -> {
                val fileName = event.childName
                if ((FileUtilRt.extensionEquals(fileName, ModuleFileType.DEFAULT_EXTENSION) && !event.isDirectory) ||
                    isParentOfStorageFiles(event.parent) && !event.isEmptyDirectory) {
                  if (addedUrls == null) {
                    addedUrls = mutableListOf()
                  }
                  addedUrls.add(JpsPathUtil.pathToUrl(event.path))
                }
              }
              is VFileDeleteEvent -> {
                if (isStorageFile(event.file)) {
                  if (removedUrls == null) {
                    removedUrls = mutableListOf()
                  }
                  removedUrls.add(JpsPathUtil.pathToUrl(event.path))
                }
              }
              is VFileContentChangeEvent -> {
                if (isStorageFile(event.file)) {
                  if (changedUrls == null) {
                    changedUrls = mutableListOf()
                  }
                  changedUrls.add(JpsPathUtil.pathToUrl(event.path))
                }
              }
            }
          }
        }
        if (!addedUrls.isNullOrEmpty() || !removedUrls.isNullOrEmpty() || !changedUrls.isNullOrEmpty()) {
          val change = JpsConfigurationFilesChange(addedFileUrls = addedUrls ?: emptyList(),
                                                   removedFileUrls = removedUrls ?: emptyList(),
                                                   changedFileUrls = changedUrls ?: emptyList())
          incomingChanges.add(change)
          project.serviceIfCreated<StoreReloadManager>()?.scheduleProcessingChangedFiles()
        }
      }
    })
    val listener = object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        LOG.debug("Marking changed entities for save")
        for (change in event.getAllChanges()) {
          change.oldEntity?.entitySource?.let { if (it !is JpsGlobalFileEntitySource) sourcesToSave.add(it) }
          change.newEntity?.entitySource?.let { if (it !is JpsGlobalFileEntitySource) sourcesToSave.add(it) }
        }
      }
    }
    connection.subscribe(WorkspaceModelTopics.CHANGED, listener)
    connection.subscribe(WorkspaceModelTopics.UNLOADED_ENTITIES_CHANGED, object : WorkspaceModelUnloadedStorageChangeListener {
      override fun changed(event: VersionedStorageChange) {
        listener.changed(event)
      }
    })
  }

  suspend fun loadProjectToEmptyStorage(project: Project): LoadedProjectEntities? {
    val start = System.currentTimeMillis()

    val configLocation = getJpsProjectConfigLocation(project)!!
    LOG.debug { "Initial loading of project located at $configLocation" }
    activity = startActivity("project workspace model loading")
    childActivity = activity?.startChild("serializers creation")

    val serializers = prepareSerializers()
    registerListener()
    val builder = MutableEntityStorage.create()
    val orphanage = MutableEntityStorage.create()
    val unloadedEntitiesBuilder = MutableEntityStorage.create()

    val loadedProjectEntities = if (!WorkspaceModelInitialTestContent.hasInitialContent) {
      childActivity = childActivity?.endAndStart("loading entities from files")
      val unloadedModuleNamesHolder = UnloadedModulesListStorage.getInstance(project).unloadedModuleNameHolder
      val sourcesToUpdate = loadAndReportErrors {
        serializers.loadAll(fileContentReader, builder, orphanage, unloadedEntitiesBuilder, unloadedModuleNamesHolder, it)
      }
      fileContentReader.clearCache()
      (WorkspaceModel.getInstance(project) as? WorkspaceModelImpl)?.entityTracer?.printInfoAboutTracedEntity(builder, "JPS files")
      if (GlobalLibraryTableBridge.isEnabled()) {
        childActivity = childActivity?.endAndStart("applying entities from global storage")
        val mutableStorage = MutableEntityStorage.create()
        GlobalWorkspaceModel.getInstance().applyStateToProjectBuilder(project, mutableStorage)
        builder.addDiff(mutableStorage)
      }
      childActivity = childActivity?.endAndStart("applying loaded changes (in queue)")
      LoadedProjectEntities(builder, orphanage, unloadedEntitiesBuilder, sourcesToUpdate)
    }
    else {
      childActivity?.end()
      childActivity = null
      activity?.end()
      activity = null
      null
    }

    jpsLoadProjectToEmptyStorageTimeMs.addElapsedTimeMs(start)
    WorkspaceModelFusLogger.logLoadingJpsFromIml(System.currentTimeMillis() - start)
    return loadedProjectEntities
  }

  suspend fun applyLoadedStorage(projectEntities: LoadedProjectEntities?) {
    val start = System.currentTimeMillis()

    if (projectEntities == null) {
      return
    }

    writeAction {
      // add logs
      childActivity = childActivity?.endAndStart("applying loaded changes")
      val description = "Apply JPS storage (iml files)"
      val sourceFilter = { entitySource: EntitySource ->
        entitySource is JpsFileEntitySource || entitySource is JpsFileDependentEntitySource || entitySource is CustomModuleEntitySource
        || entitySource is DummyParentEntitySource
      }
      if (projectEntities.unloadedEntitiesBuilder.hasChanges()) {
        WorkspaceModel.getInstance(project).updateUnloadedEntities(description) { updater ->
          updater.replaceBySource(sourceFilter, projectEntities.unloadedEntitiesBuilder)
        }
      }
      val unloadedBuilder = MutableEntityStorage.from(WorkspaceModel.getInstance(project).currentSnapshotOfUnloadedEntities)
      WorkspaceModel.getInstance(project).updateProjectModel(description) { updater ->
        updater.replaceBySource(sourceFilter, projectEntities.builder)
        childActivity = childActivity?.endAndStart("unloaded modules loading")
        runAutomaticModuleUnloader(updater, unloadedBuilder)
      }
      addUnloadedModuleEntities(unloadedBuilder)

      EntitiesOrphanage.getInstance(project).update {
        it.addDiff(projectEntities.orphanageBuilder)
      }
      childActivity?.end()
      childActivity = null
    }
    sourcesToSave.clear()
    sourcesToSave.addAll(projectEntities.sourcesToUpdate)

    activity?.end()
    activity = null

    applyLoadedStorageTimeMs.addElapsedTimeMs(start)
  }

  suspend fun loadProject(project: Project) {
    applyLoadedStorage(loadProjectToEmptyStorage(project))
  }

  private fun runAutomaticModuleUnloader(builder: MutableEntityStorage, unloadedEntitiesBuilder: MutableEntityStorage) {
    ModuleManagerEx.getInstanceEx(project).unloadNewlyAddedModulesIfPossible(builder, unloadedEntitiesBuilder)
  }

  private fun addUnloadedModuleEntities(diff: MutableEntityStorage) {
    if (diff.hasChanges()) {
      WorkspaceModel.getInstance(project).updateUnloadedEntities("Add new unloaded modules") { updater ->
        updater.addDiff(diff)
      }
    }
  }

  // IDEA-288703
  fun hasNoSerializedJpsModules(): Boolean {
    return !isIntelliJ() && // todo: https://youtrack.jetbrains.com/issue/IDEA-291451#focus=Comments-27-5967781.0-0
           !isRider() &&
           !isFleetBackend() && // https://youtrack.jetbrains.com/issue/IDEA-323592#focus=Comments-27-7967807.0-0
           (prepareSerializers() as JpsProjectSerializersImpl).moduleSerializers.isEmpty()
  }

  private fun prepareSerializers(): JpsProjectSerializers {
    val existingSerializers = this.serializers.get()
    if (existingSerializers != null) return existingSerializers

    val serializers = createSerializers()
    this.serializers.set(serializers)
    return serializers
  }

  private fun createSerializers(): JpsProjectSerializers {
    val configLocation: JpsProjectConfigLocation = getJpsProjectConfigLocation(project)!!
    fileContentReader = (project.stateStore as ProjectStoreWithJpsContentReader).createContentReader()
    val externalStoragePath = project.getExternalConfigurationDir()
    val externalStorageConfigurationManager = ExternalStorageConfigurationManager.getInstance(project)
    val fileInDirectorySourceNames = FileInDirectorySourceNames.from(WorkspaceModel.getInstance(project).currentSnapshot)
    val context = IdeSerializationContext(virtualFileManager, fileContentReader, fileInDirectorySourceNames,
                                          externalStorageConfigurationManager)
    return createProjectSerializers(configLocation, externalStoragePath, context)
  }

  fun saveChangedProjectEntities(writer: JpsFileContentWriter) {
    val start = System.currentTimeMillis()
    LOG.debug("Saving project entities")
    val data = serializers.get()
    if (data == null) {
      LOG.debug("Skipping save because initial loading wasn't performed")
      return
    }
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    val unloadedEntitiesStorage = WorkspaceModel.getInstance(project).currentSnapshotOfUnloadedEntities
    val affectedSources = synchronized(sourcesToSave) {
      val copy = HashSet(sourcesToSave)
      sourcesToSave.clear()
      copy
    }
    LOG.debugValues("Saving affected entities", affectedSources)
    data.saveEntities(storage, unloadedEntitiesStorage, affectedSources, writer)

    saveChangedProjectEntitiesTimeMs.addElapsedTimeMs(start)
  }

  fun convertToDirectoryBasedFormat() {
    val newSerializers = createSerializers()
    WorkspaceModel.getInstance(project).updateProjectModel("Convert to directory based format") {
      newSerializers.changeEntitySourcesToDirectoryBasedFormat(it)
    }
    val moduleSources = WorkspaceModel.getInstance(project).currentSnapshot.entities(ModuleEntity::class.java).map { it.entitySource }
    val unloadedModuleSources = WorkspaceModel.getInstance(project).currentSnapshotOfUnloadedEntities.entities(
      ModuleEntity::class.java).map { it.entitySource }
    synchronized(sourcesToSave) {
      // trigger save for modules.xml
      sourcesToSave.addAll(moduleSources)
      sourcesToSave.addAll(unloadedModuleSources)
    }
    serializers.set(newSerializers)
  }

  @TestOnly
  fun markAllEntitiesAsDirty() {
    val allSources = WorkspaceModel.getInstance(project).currentSnapshot.entitiesBySource { true }.keys +
                     WorkspaceModel.getInstance(project).currentSnapshotOfUnloadedEntities.entitiesBySource { true }.keys
    synchronized(sourcesToSave) {
      sourcesToSave.addAll(allSources)
    }
  }

  private fun getAndResetIncomingChanges(): JpsConfigurationFilesChange? {
    synchronized(incomingChanges) {
      if (incomingChanges.isEmpty()) return null
      val combinedChanges = combineChanges()
      incomingChanges.clear()
      return combinedChanges
    }
  }

  private fun combineChanges(): JpsConfigurationFilesChange {
    val singleChange = incomingChanges.singleOrNull()
    if (singleChange != null) {
      return singleChange
    }
    val allAdded = LinkedHashSet<String>()
    val allRemoved = LinkedHashSet<String>()
    val allChanged = LinkedHashSet<String>()
    for (change in incomingChanges) {
      allChanged.addAll(change.changedFileUrls)
      for (addedUrl in change.addedFileUrls) {
        if (allRemoved.remove(addedUrl)) {
          allChanged.add(addedUrl)
        }
        else {
          allAdded.add(addedUrl)
        }
      }
      for (removedUrl in change.removedFileUrls) {
        allChanged.remove(removedUrl)
        if (!allAdded.remove(removedUrl)) {
          allRemoved.add(removedUrl)
        }
      }
    }
    return JpsConfigurationFilesChange(allAdded, allRemoved, allChanged)
  }

  override fun dispose() {
  }

  @TestOnly
  fun getSerializers(): JpsProjectSerializersImpl = serializers.get() as JpsProjectSerializersImpl
}

class LoadedProjectEntities(
  val builder: MutableEntityStorage,
  val orphanageBuilder: MutableEntityStorage,
  val unloadedEntitiesBuilder: MutableEntityStorage,
  val sourcesToUpdate: List<EntitySource>
)

