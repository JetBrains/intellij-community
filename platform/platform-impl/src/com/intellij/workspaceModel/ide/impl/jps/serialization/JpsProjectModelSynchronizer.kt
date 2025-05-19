// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.configurationStore.isFireStorageFileChangedEvent
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.StartUpMeasurer.startActivity
import com.intellij.ide.IdeBundle
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ProjectLoadingErrorsNotifier
import com.intellij.openapi.module.impl.ModuleManagerEx
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.getExternalConfigurationDir
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.backend.workspace.*
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.diagnostic.telemetry.helpers.Milliseconds
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.workspace.jps.*
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.serialization.impl.*
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectEntitiesLoader.createProjectSerializers
import com.intellij.platform.workspace.storage.DummyParentEntitySource
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.impl.VersionedStorageChangeInternal
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.project.stateStore
import com.intellij.util.PlatformUtils.*
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.workspaceModel.ide.EntitiesOrphanage
import com.intellij.workspaceModel.ide.getJpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.*
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LegacyCustomLibraryEntitySource
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.util.JpsPathUtil
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages serialization and deserialization from JPS format (*.iml and *.ipr files, .idea directory) for a workspace model in the IDE.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class JpsProjectModelSynchronizer(private val project: Project) : Disposable {
  companion object {
    @RequiresBlockingContext
    fun getInstance(project: Project): JpsProjectModelSynchronizer = project.service()

    private val LOG = logger<JpsProjectModelSynchronizer>()

    private val jpsLoadProjectToEmptyStorageTimeMs = MillisecondsMeasurer()
    private val reloadProjectEntitiesTimeMs = MillisecondsMeasurer()
    private val applyLoadedStorageTimeMs = MillisecondsMeasurer()
    private val saveChangedProjectEntitiesTimeMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val jpsLoadProjectToEmptyStorageTimeCounter = meter.counterBuilder("jps.load.project.to.empty.storage.ms").buildObserver()
      val reloadProjectEntitiesTimeCounter = meter.counterBuilder("jps.reload.project.entities.ms").buildObserver()
      val applyLoadedStorageTimeCounter = meter.counterBuilder("jps.apply.loaded.storage.ms").buildObserver()
      val saveChangedProjectEntitiesTimeCounter = meter.counterBuilder("jps.save.changed.project.entities.ms").buildObserver()

      meter.batchCallback(
        {
          jpsLoadProjectToEmptyStorageTimeCounter.record(jpsLoadProjectToEmptyStorageTimeMs.asMilliseconds())
          reloadProjectEntitiesTimeCounter.record(reloadProjectEntitiesTimeMs.asMilliseconds())
          applyLoadedStorageTimeCounter.record(applyLoadedStorageTimeMs.asMilliseconds())
          saveChangedProjectEntitiesTimeCounter.record(saveChangedProjectEntitiesTimeMs.asMilliseconds())
        },
        jpsLoadProjectToEmptyStorageTimeCounter, reloadProjectEntitiesTimeCounter,
        applyLoadedStorageTimeCounter, saveChangedProjectEntitiesTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }

  private val incomingChanges = Collections.synchronizedList(ArrayList<JpsConfigurationFilesChange>())
  private val virtualFileManager: VirtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
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

  @OptIn(EntityStorageInstrumentationApi::class)
  suspend fun reloadProjectEntities(): Unit = reloadProjectEntitiesTimeMs.addMeasuredTime {
    if (StoreReloadManager.getInstance(project).isReloadBlocked()) {
      LOG.debug("Skip reloading because it's blocked")
      return@addMeasuredTime
    }

    val serializers = serializers.get()
    if (serializers == null) {
      LOG.debug("Skip reloading because initial loading wasn't performed yet")
      return@addMeasuredTime
    }

    val changes = getAndResetIncomingChanges()
    if (changes == null) {
      LOG.debug("Skip reloading because there are no changed files")
      return@addMeasuredTime
    }

    LOG.debug { "Reload entities from changed files:\n$changes" }

    val unloadedModuleNameHolder = UnloadedModulesListStorage.getInstance(project).unloadedModuleNameHolder
    val reloadingResult = loadAndReportErrors {
      serializers.reloadFromChangedFiles(changes, fileContentReader, unloadedModuleNameHolder, it)
    }

    fileContentReader.clearCache()
    LOG.debugValues("Changed entity sources", reloadingResult.affectedSources)

    if (reloadingResult.affectedSources.isEmpty() &&
        !(reloadingResult.builder as MutableEntityStorageInstrumentation).hasChanges() &&
        !(reloadingResult.unloadedEntityBuilder as MutableEntityStorageInstrumentation).hasChanges() &&
        !(reloadingResult.orphanageBuilder as MutableEntityStorageInstrumentation).hasChanges()) {
      return@addMeasuredTime
    }

    val description = "Reload entities after changes in JPS configuration files"
    val affectedEntityFilter: (EntitySource) -> Boolean = {
      it in reloadingResult.affectedSources
      || (it is JpsImportedEntitySource && !it.storedExternally && it.internalFile in reloadingResult.affectedSources)
      || it is DummyParentEntitySource
    }

    applyLoadedEntities(affectedEntityFilter, reloadingResult.builder, reloadingResult.unloadedEntityBuilder, reloadingResult.orphanageBuilder,
                        description) {
      sourcesToSave.removeAll(reloadingResult.affectedSources)
    }
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  private suspend fun applyLoadedEntities(sourcesFilter: (EntitySource) -> Boolean, builder: MutableEntityStorage,
                                          unloadedEntityBuilder: MutableEntityStorage, orphanageBuilder: MutableEntityStorage,
                                          description: String, onSuccessCallback: () -> Unit) {

    class CalculationResult(val builderSnapshot: BuilderSnapshot, val unloadBuilderSnapshot: BuilderSnapshot, val unloadedBuilderCopy: MutableEntityStorage,
                            val modulesToLoad: List<String>, val modulesToUnload: List<String>)

    @OptIn(EntityStorageInstrumentationApi::class)
    fun calculateChanges(workspaceModel: WorkspaceModelImpl): CalculationResult {
      val unloadBuilderSnapshot = workspaceModel.getUnloadBuilderSnapshot()
      // Update builder of unloaded entities
      if ((unloadedEntityBuilder as MutableEntityStorageInstrumentation).hasChanges()) {
        unloadBuilderSnapshot.builder.replaceBySource(sourcesFilter, unloadedEntityBuilder.toSnapshot())
      }

      // Update builder of regular entities
      val builderSnapshot = workspaceModel.getBuilderSnapshot()
      builderSnapshot.builder.replaceBySource(sourcesFilter, builder.toSnapshot())

      val unloadedBuilderCopy = MutableEntityStorage.from(unloadBuilderSnapshot.builder.toSnapshot())
      val moduleManagerEx = ModuleManagerEx.getInstanceEx(project)
      val (modulesToLoad, modulesToUnload) = moduleManagerEx.calculateUnloadModules(builderSnapshot.builder, unloadedBuilderCopy)
      return CalculationResult(builderSnapshot, unloadBuilderSnapshot, unloadedBuilderCopy, modulesToLoad, modulesToUnload)
    }

    @OptIn(EntityStorageInstrumentationApi::class)
    fun applyLoadedChanges(calculationResult: CalculationResult, workspaceModel: WorkspaceModelImpl): Boolean {
      val moduleManagerEx = ModuleManagerEx.getInstanceEx(project)
      // TODO If we don't have changes in [UNLOAD] part, it doesn't make sense to use this method
      val isSuccessful = workspaceModel.replaceProjectModel(
        mainStorageReplacement = calculationResult.builderSnapshot.getStorageReplacement(),
        unloadStorageReplacement = calculationResult.unloadBuilderSnapshot.getStorageReplacement(),
      )
      if (!isSuccessful) {
        return false
      }

      moduleManagerEx.updateUnloadedStorage(calculationResult.modulesToLoad, calculationResult.modulesToUnload)
      addUnloadedModuleEntities(calculationResult.unloadedBuilderCopy, workspaceModel)

      if ((orphanageBuilder as MutableEntityStorageInstrumentation).hasChanges()) {
        EntitiesOrphanage.getInstance(project).update { it.applyChangesFrom(orphanageBuilder) }
      }

      onSuccessCallback()
      return true
    }

    suspend fun applyChangesWithRetry(retryCount: Int): Boolean {
      val workspaceModel = project.serviceAsync<WorkspaceModel>() as WorkspaceModelImpl
      for (i in 1..retryCount) {
        LOG.info("Attempt $i: $description")
        val calculationResult = calculateChanges(workspaceModel)
        val isSuccessful = backgroundWriteAction { applyLoadedChanges(calculationResult, workspaceModel) }
        if (isSuccessful) {
          LOG.info("Attempt $i: Changes were successfully applied")
          return true
        }
      }
      return false
    }

    val isSuccessful = applyChangesWithRetry(2)
    if (isSuccessful) {
      return
    }

    val workspaceModel = project.serviceAsync<WorkspaceModel>() as WorkspaceModelImpl
    // Fallback strategy after the two unsuccessful attempts to apply the changes
    backgroundWriteAction {
      LOG.info("Fallback strategy after the unsuccessful attempts to apply the changes from BGT")
      val calculationResult = calculateChanges(workspaceModel)
      applyLoadedChanges(calculationResult, workspaceModel)
    }
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
        for (change in (event as VersionedStorageChangeInternal).getAllChanges()) {
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
    val start = Milliseconds.now()

    val configLocation = getJpsProjectConfigLocation(project)!!
    LOG.debug { "Initial loading of project located at $configLocation" }
    activity = startActivity("project workspace model loading")
    childActivity = activity?.startChild("serializers creation")

    val serializers = prepareSerializers()
    registerListener()
    val builder = MutableEntityStorage.create()
    val orphanage = MutableEntityStorage.create()
    val unloadedEntitiesBuilder = MutableEntityStorage.create()

    val loadedProjectEntities = if (WorkspaceModelInitialTestContent.hasInitialContent) {
      childActivity?.end()
      childActivity = null
      activity?.end()
      activity = null
      null
    }
    else if (TrustedProjects.isProjectTrusted(project)) {
      childActivity = childActivity?.endAndStart("loading entities from files")
      val unloadedModuleNamesHolder = UnloadedModulesListStorage.getInstance(project).unloadedModuleNameHolder
      val sourcesToUpdate = loadAndReportErrors {
        serializers.loadAll(fileContentReader, builder, orphanage, unloadedEntitiesBuilder, unloadedModuleNamesHolder, it)
      }
      fileContentReader.clearCache()
      (project.serviceAsync<WorkspaceModel>() as WorkspaceModelImpl).entityTracer.printInfoAboutTracedEntity(builder, "JPS files")
      childActivity = childActivity?.endAndStart("applying entities from global storage")
      val mutableStorage = MutableEntityStorage.create()
      GlobalWorkspaceModel.getInstance(project.getEelDescriptor()).applyStateToProjectBuilder(project, mutableStorage)
      builder.applyChangesFrom(mutableStorage)
      childActivity = childActivity?.endAndStart("applying loaded changes (in queue)")
      LoadedProjectEntities(builder, orphanage, unloadedEntitiesBuilder, sourcesToUpdate)
    }
    else {
      childActivity = childActivity?.endAndStart("loading untrusted project")

      NotificationGroupManager.getInstance()
        .getNotificationGroup("Project Loading Error")
        .createNotification(ProjectBundle.message("notification.title.error.loading.project"),
                            IdeBundle.message("untrusted.jps.project.not.loaded.notification"),
                            NotificationType.WARNING)
        .notify(project)

      // this should be not a "base path", but a folder that the user selected in the file chooser.
      // this works (at the moment) because: if the user selected a folder with .idea or .ipr inside, then basePath is pointing
      // to the directory selected by the user.
      // If there is no .idea nor .ipr in the selected directory, then we should not get here.
      val basePath = project.basePath
      if (basePath != null) {
        createProjectFromFolder(builder, "untrusted", virtualFileManager.getOrCreateFromUrl("file://$basePath"))
      }
      LoadedProjectEntities(builder, orphanage, unloadedEntitiesBuilder, emptyList())
    }

    jpsLoadProjectToEmptyStorageTimeMs.addElapsedTime(start)
    WorkspaceModelFusLogger.logLoadingJpsFromIml(Milliseconds.now().minus(start).value)
    return loadedProjectEntities
  }

  @Suppress("SameParameterValue")
  private fun createProjectFromFolder(builder: MutableEntityStorage, name: String, basePath: VirtualFileUrl) {
    // DummyParentEntitySource, because otherwise the module will be thrown away in [applyLoadedStorage]
    class UntrustedProjectEntitySource : DummyParentEntitySource

    val entitySource = UntrustedProjectEntitySource()
    val module = ModuleEntity(name, emptyList(), entitySource)
    // add everything (*) to excludes to avoid indexing (at the moment - minimize, not fully avoid)
    ContentRootEntity(url = basePath, excludedPatterns = listOf("*"), entitySource = entitySource) {
      this.module = module
    }
    builder.addEntity(module)
  }

  suspend fun applyLoadedStorage(projectEntities: LoadedProjectEntities?): Unit = applyLoadedStorageTimeMs.addMeasuredTime {
    if (projectEntities == null) {
      return@addMeasuredTime
    }

    val description = "Apply JPS storage (iml files)"
    val sourceFilter = { entitySource: EntitySource ->
      entitySource is JpsFileEntitySource // covers all global SDK and libraries
      || entitySource is JpsFileDependentEntitySource
      || entitySource is CustomModuleEntitySource || entitySource is DummyParentEntitySource // covers CIDR related entities
      || entitySource is LegacyCustomLibraryEntitySource // covers custom libraries
    }
    childActivity = childActivity?.endAndStart("applying loaded changes")
    applyLoadedEntities(sourceFilter, projectEntities.builder, projectEntities.unloadedEntitiesBuilder, projectEntities.orphanageBuilder, description) {
      sourcesToSave.clear()
      sourcesToSave.addAll(projectEntities.sourcesToUpdate)
    }
    childActivity?.end()
    childActivity = null

    activity?.end()
    activity = null
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  private fun addUnloadedModuleEntities(diff: MutableEntityStorage, workspaceModel: WorkspaceModelImpl) {
    if ((diff as MutableEntityStorageInstrumentation).hasChanges()) {
      workspaceModel.updateUnloadedEntities("Add new unloaded modules") { updater ->
        updater.applyChangesFrom(diff)
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
    serializers.get()?.let {
      return it
    }

    val serializers = createSerializers()
    this.serializers.set(serializers)
    return serializers
  }

  private fun createSerializers(): JpsProjectSerializers {
    val configLocation = getJpsProjectConfigLocation(project)!!
    fileContentReader = (project.stateStore as ProjectStoreWithJpsContentReader).createContentReader()
    val externalStoragePath = project.getExternalConfigurationDir()
    val externalStorageConfigurationManager = project.serviceOrNull<ExternalStorageConfigurationManager>()
    val fileInDirectorySourceNames = FileInDirectorySourceNames.from(WorkspaceModel.getInstance(project).currentSnapshot)
    val context = IdeSerializationContext(
      virtualFileUrlManager = virtualFileManager,
      fileContentReader = fileContentReader,
      fileInDirectorySourceNames = fileInDirectorySourceNames,
      externalStorageConfigurationManager = externalStorageConfigurationManager,
    )
    return createProjectSerializers(configLocation, externalStoragePath, context)
  }

  fun saveChangedProjectEntities(writer: JpsFileContentWriter, workspaceModel: WorkspaceModel): Unit = saveChangedProjectEntitiesTimeMs.addMeasuredTime {
    LOG.debug("Saving project entities")
    val data = serializers.get()
    if (data == null) {
      LOG.debug("Skipping save because initial loading wasn't performed")
      return@addMeasuredTime
    }

    val storage = workspaceModel.currentSnapshot
    val unloadedEntitiesStorage = (workspaceModel as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities
    val affectedSources = synchronized(sourcesToSave) {
      val copy = HashSet(sourcesToSave)
      sourcesToSave.clear()
      copy
    }
    LOG.debugValues("Saving affected entities", affectedSources)
    data.saveEntities(storage, unloadedEntitiesStorage, affectedSources, writer)
  }

  fun convertToDirectoryBasedFormat() {
    val newSerializers = createSerializers()
    val workspaceModel = WorkspaceModel.getInstance(project) as WorkspaceModelInternal
    workspaceModel.updateProjectModel("Convert to directory based format") {
      newSerializers.changeEntitySourcesToDirectoryBasedFormat(it)
    }
    val moduleSources = workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).map { it.entitySource }
    val unloadedModuleSources = workspaceModel
      .currentSnapshotOfUnloadedEntities.entities(ModuleEntity::class.java)
      .map { it.entitySource }
    synchronized(sourcesToSave) {
      // trigger save for modules.xml
      sourcesToSave.addAll(moduleSources)
      sourcesToSave.addAll(unloadedModuleSources)
    }
    serializers.set(newSerializers)
  }

  @TestOnly
  fun markAllEntitiesAsDirty() {
    val workspaceModel = WorkspaceModel.getInstance(project)
    val allSources = workspaceModel.currentSnapshot.entitiesBySource { true }.mapTo(HashSet()) { it.entitySource } +
                     (workspaceModel as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities.entitiesBySource { true }.mapTo(
                       HashSet()) { it.entitySource }
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

@ApiStatus.Internal
class LoadedProjectEntities(
  @JvmField val builder: MutableEntityStorage,
  @JvmField val orphanageBuilder: MutableEntityStorage,
  @JvmField val unloadedEntitiesBuilder: MutableEntityStorage,
  @JvmField val sourcesToUpdate: List<EntitySource>
)

