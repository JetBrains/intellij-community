// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.configurationStore.isFireStorageFileChangedEvent
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer.startActivity
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import com.intellij.platform.workspaceModel.jps.*
import com.intellij.platform.workspaceModel.jps.serialization.impl.FileInDirectorySourceNames
import com.intellij.project.stateStore
import com.intellij.util.PlatformUtils.isIntelliJ
import com.intellij.util.PlatformUtils.isRider
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.*
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectEntitiesLoader.createProjectSerializers
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.util.JpsPathUtil
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages serialization and deserialization from JPS format (*.iml and *.ipr files, .idea directory) for workspace model in IDE.
 */
@Service(Service.Level.PROJECT)
class JpsProjectModelSynchronizer(private val project: Project) : Disposable {
  companion object {
    fun getInstance(project: Project): JpsProjectModelSynchronizer = project.service()

    private val LOG = logger<JpsProjectModelSynchronizer>()
  }

  private val incomingChanges = Collections.synchronizedList(ArrayList<JpsConfigurationFilesChange>())
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private lateinit var fileContentReader: JpsFileContentReaderWithCache
  private val serializers = AtomicReference<JpsProjectSerializers?>()
  private val sourcesToSave = Collections.synchronizedSet(HashSet<EntitySource>())
  private var activity: Activity? = null
  private var childActivity: Activity? = null

  fun needToReloadProjectEntities(): Boolean {
    if (StoreReloadManager.getInstance().isReloadBlocked()) return false
    if (serializers.get() == null) return false

    synchronized(incomingChanges) {
      return incomingChanges.isNotEmpty()
    }
  }

  suspend fun reloadProjectEntities() {
    if (StoreReloadManager.getInstance().isReloadBlocked()) {
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

    val unloadedModuleNames = UnloadedModulesListStorage.getInstance(project).unloadedModuleNames.toSet()
    val reloadingResult = loadAndReportErrors {
      serializers.reloadFromChangedFiles(changes, fileContentReader, unloadedModuleNames, it)
    }

    fileContentReader.clearCache()
    LOG.debugValues("Changed entity sources", reloadingResult.affectedSources)

    if (reloadingResult.affectedSources.isEmpty()
        && !reloadingResult.builder.hasChanges()
        && !reloadingResult.unloadedEntityBuilder.hasChanges()
        && !reloadingResult.orphanageBuilder.hasChanges()) return

    withContext(Dispatchers.EDT) {
      runWriteAction {
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

    ApplicationManager.getApplication().messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        //todo support move/rename
        //todo optimize: filter events before creating lists
        val addedUrls = ArrayList<String>()
        val removedUrls = ArrayList<String>()
        val changedUrls = ArrayList<String>()
        // JPS model is loaded from *.iml files, files in .idea directory (modules.xml),
        // files from directories in .idea (libraries) and *.ipr file, so we can ignore all other events to speed up processing
        for (event in events) {
          if (isFireStorageFileChangedEvent(event)) {
            when (event) {
              is VFileCreateEvent -> {
                val fileName = event.childName
                if (FileUtilRt.extensionEquals(fileName, ModuleFileType.DEFAULT_EXTENSION) && !event.isDirectory
                    || isParentOfStorageFiles(event.parent) && !event.isEmptyDirectory) {
                  addedUrls.add(JpsPathUtil.pathToUrl(event.path))
                }
              }
              is VFileDeleteEvent -> {
                if (isStorageFile(event.file)) {
                  removedUrls.add(JpsPathUtil.pathToUrl(event.path))
                }
              }
              is VFileContentChangeEvent -> {
                if (isStorageFile(event.file)) {
                  changedUrls.add(JpsPathUtil.pathToUrl(event.path))
                }
              }
            }
          }
        }
        if (addedUrls.isNotEmpty() || removedUrls.isNotEmpty() || changedUrls.isNotEmpty()) {
          val change = JpsConfigurationFilesChange(addedUrls, removedUrls, changedUrls)
          incomingChanges.add(change)

          StoreReloadManager.getInstance().scheduleProcessingChangedFiles()
        }
      }
    })
    val listener = object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        LOG.debug("Marking changed entities for save")
        event.getAllChanges().forEach { change ->
          change.oldEntity?.entitySource?.let { if (it !is JpsGlobalFileEntitySource) sourcesToSave.add(it) }
          change.newEntity?.entitySource?.let { if (it !is JpsGlobalFileEntitySource) sourcesToSave.add(it) }
        }
      }
    }
    project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, listener)
    project.messageBus.connect().subscribe(WorkspaceModelTopics.UNLOADED_ENTITIES_CHANGED, listener)
  }

  suspend fun loadProjectToEmptyStorage(project: Project): LoadedProjectEntities? {
    val configLocation = getJpsProjectConfigLocation(project)!!
    LOG.debug { "Initial loading of project located at $configLocation" }
    activity = startActivity("project files loading", ActivityCategory.DEFAULT)
    childActivity = activity?.startChild("serializers creation")
    val serializers = prepareSerializers()
    registerListener()
    val builder = MutableEntityStorage.create()
    val orphanage = MutableEntityStorage.create()
    val unloadedEntitiesBuilder = MutableEntityStorage.create()
    if (!WorkspaceModelInitialTestContent.hasInitialContent) {
      childActivity = childActivity?.endAndStart("loading entities from files")
      val unloadedModuleNames = UnloadedModulesListStorage.getInstance(project).unloadedModuleNames.toSet()
      val sourcesToUpdate = loadAndReportErrors { serializers.loadAll(fileContentReader, builder, orphanage, unloadedEntitiesBuilder, unloadedModuleNames, it) }
      fileContentReader.clearCache()
      (WorkspaceModel.getInstance(project) as? WorkspaceModelImpl)?.entityTracer?.printInfoAboutTracedEntity(builder, "JPS files")
      if (GlobalLibraryTableBridge.isEnabled()) {
        childActivity = childActivity?.endAndStart("applying entities from global storage")
        val mutableStorage = MutableEntityStorage.create()
        GlobalWorkspaceModel.getInstance().applyStateToProjectBuilder(project, mutableStorage)
        builder.addDiff(mutableStorage)
      }
      childActivity = childActivity?.endAndStart("applying loaded changes (in queue)")
      return LoadedProjectEntities(builder, orphanage, unloadedEntitiesBuilder, sourcesToUpdate)
    }
    else {
      childActivity?.end()
      childActivity = null
      activity?.end()
      activity = null
      return null
    }
  }

  suspend fun applyLoadedStorage(projectEntities: LoadedProjectEntities?) {
    if (projectEntities == null) {
      return
    }

    withContext(Dispatchers.EDT) {
      // add logs
      ApplicationManager.getApplication().runWriteAction {
        if (project.isDisposed) return@runWriteAction
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
    }
    sourcesToSave.clear()
    sourcesToSave.addAll(projectEntities.sourcesToUpdate)

    activity?.end()
    activity = null
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
           !isRider() && (prepareSerializers() as JpsProjectSerializersImpl).moduleSerializers.isEmpty()
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
  }

  fun convertToDirectoryBasedFormat() {
    val newSerializers = createSerializers()
    WorkspaceModel.getInstance(project).updateProjectModel("Convert to directory based format") {
      newSerializers.changeEntitySourcesToDirectoryBasedFormat(it)
    }
    val moduleSources = WorkspaceModel.getInstance(project).currentSnapshot.entities(ModuleEntity::class.java).map { it.entitySource }
    val unloadedModuleSources = WorkspaceModel.getInstance(project).currentSnapshotOfUnloadedEntities.entities(ModuleEntity::class.java).map { it.entitySource }
    synchronized(sourcesToSave) {
      //to trigger save for modules.xml
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

