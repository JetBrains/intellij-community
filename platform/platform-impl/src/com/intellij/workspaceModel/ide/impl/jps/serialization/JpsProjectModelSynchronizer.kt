// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.configurationStore.*
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.impl.*
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getExternalConfigurationDir
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.project.stateStore
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelInitialTestContent
import com.intellij.workspaceModel.ide.impl.recordModuleLoadingActivity
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.util.JpsPathUtil
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet

/**
 * Manages serialization and deserialization from JPS format (*.iml and *.ipr files, .idea directory) for workspace model in IDE.
 */
class JpsProjectModelSynchronizer(private val project: Project) : Disposable {
  private val incomingChanges = Collections.synchronizedList(ArrayList<JpsConfigurationFilesChange>())
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private lateinit var fileContentReader: JpsFileContentReaderWithCache
  private val serializers = AtomicReference<JpsProjectSerializers?>()
  private val sourcesToSave = Collections.synchronizedSet(HashSet<EntitySource>())
  private val errorReporter = IdeErrorReporter()

  init {
    if (!project.isDefault) {
      ApplicationManager.getApplication().messageBus.connect(this).subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener {
        override fun projectComponentsInitialized(project: Project) {
          LOG.debug { "Project component initialized" }
          if (project === this@JpsProjectModelSynchronizer.project
              && !(WorkspaceModel.getInstance(project) as WorkspaceModelImpl).loadedFromCache) {
            LOG.info("Workspace model loaded without cache. Loading real project state into workspace model. ${Thread.currentThread()}")
            loadRealProject(project)
          }
        }
      })
    }
  }

  fun needToReloadProjectEntities(): Boolean {
    if (StoreReloadManager.getInstance().isReloadBlocked()) return false
    if (serializers.get() == null) return false

    synchronized(incomingChanges) {
      return incomingChanges.isNotEmpty()
    }
  }

  fun reloadProjectEntities() {
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
    val (changedSources, builder) = serializers.reloadFromChangedFiles(changes, fileContentReader, errorReporter)
    fileContentReader.clearCache()
    LOG.debugValues("Changed entity sources", changedSources)
    if (changedSources.isEmpty() && builder.isEmpty()) return

    ApplicationManager.getApplication().invokeAndWait(Runnable {
      runWriteAction {
        WorkspaceModel.getInstance(project).updateProjectModel { updater ->
          updater.replaceBySource({ it in changedSources }, builder.toStorage())
        }
        sourcesToSave.removeAll(changedSources)
      }
    })
  }

  private fun registerListener() {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        //todo support move/rename
        //todo optimize: filter events before creating lists
        val toProcess = events.asSequence().filter { isFireStorageFileChangedEvent(it) }
        val addedUrls = toProcess.filterIsInstance<VFileCreateEvent>().filterNot { it.isEmptyDirectory }.mapTo(ArrayList()) { JpsPathUtil.pathToUrl(it.path) }
        val removedUrls = toProcess.filterIsInstance<VFileDeleteEvent>().mapTo(ArrayList()) { JpsPathUtil.pathToUrl(it.path) }
        val changedUrls = toProcess.filterIsInstance<VFileContentChangeEvent>().mapTo(ArrayList()) { JpsPathUtil.pathToUrl(it.path) }
        if (addedUrls.isNotEmpty() || removedUrls.isNotEmpty() || changedUrls.isNotEmpty()) {
          val change = JpsConfigurationFilesChange(addedUrls, removedUrls, changedUrls)
          incomingChanges.add(change)

          StoreReloadManager.getInstance().scheduleProcessingChangedFiles()
        }
      }
    })
    WorkspaceModelTopics.getInstance(project).subscribeImmediately(project.messageBus.connect(), object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        LOG.debug("Marking changed entities for save")
        event.getAllChanges().forEach {
          when (it) {
            is EntityChange.Added -> sourcesToSave.add(it.entity.entitySource)
            is EntityChange.Removed -> sourcesToSave.add(it.entity.entitySource)
            is EntityChange.Replaced -> {
              sourcesToSave.add(it.oldEntity.entitySource)
              sourcesToSave.add(it.newEntity.entitySource)
            }
          }
        }
      }
    })
  }

  fun loadRealProject(project: Project) {
    val configLocation: JpsProjectConfigLocation = project.configLocation!!
    LOG.debug { "Initial loading of project located at $configLocation" }
    if (!(WorkspaceModel.getInstance(project) as WorkspaceModelImpl).loadedFromCache) {
      recordModuleLoadingActivity()
    }
    val activity = StartUpMeasurer.startActivity("(wm) Load initial project")
    var childActivity = activity.startChild("(wm) Prepare serializers")
    fileContentReader = (project.stateStore as ProjectStoreWithJpsContentReader).createContentReader()
    val externalStoragePath = project.getExternalConfigurationDir()
    //TODO:: Get rid of dependency on ExternalStorageConfigurationManager in order to use in build process
    val externalStorageConfigurationManager = ExternalStorageConfigurationManager.getInstance(project)
    val serializers = JpsProjectEntitiesLoader.createProjectSerializers(configLocation, fileContentReader, externalStoragePath, false,
                                                                        virtualFileManager, externalStorageConfigurationManager)
    this.serializers.set(serializers)
    registerListener()
    val builder = WorkspaceEntityStorageBuilder.create()

    childActivity = childActivity.endAndStart("(wm) Load state of unloaded modules")
    loadStateOfUnloadedModules(serializers)

    if (!WorkspaceModelInitialTestContent.hasInitialContent) {
      childActivity = childActivity.endAndStart("(wm) Read serializers")
      serializers.loadAll(fileContentReader, builder, errorReporter)
      childActivity = childActivity.endAndStart("(wm) Add changes to store")
      WriteAction.runAndWait<RuntimeException> {
        WorkspaceModel.getInstance(project).updateProjectModel { updater ->
          updater.replaceBySource({ it is JpsFileEntitySource || it is JpsFileDependentEntitySource }, builder.toStorage())
        }
      }
      sourcesToSave.clear()
      fileContentReader.clearCache()
      childActivity.end()
    }
    activity.end()
  }

  private fun loadStateOfUnloadedModules(serializers: JpsProjectSerializers) {
    val unloadedModuleNames = UnloadedModulesListStorage.getInstance(project).unloadedModuleNames.toSet()

    serializers as JpsProjectSerializersImpl
    val (unloadedModulePaths, modulePathsToLoad) = serializers.getAllModulePaths().partition { it.moduleName in unloadedModuleNames }

    val tmpBuilder = WorkspaceEntityStorageBuilder.create()
    val unloaded = unloadedModulePaths.map { modulePath ->
      serializers.findModuleSerializer(modulePath)!!.loadEntities(tmpBuilder, fileContentReader, errorReporter, virtualFileManager)

      val moduleEntity = tmpBuilder.resolve(ModuleId(modulePath.moduleName)) ?: return@map null
      val pointerManager = VirtualFilePointerManager.getInstance()
      val contentRoots = moduleEntity.contentRoots.sortedBy { contentEntry -> contentEntry.url.url }
        .map { contentEntry -> pointerManager.create(contentEntry.url.url, this, null) }.toMutableList()
      val dependencyModuleNames = moduleEntity.dependencies.filterIsInstance(ModuleDependencyItem.Exportable.ModuleDependency::class.java)
        .map { moduleDependency -> moduleDependency.module.name }

      UnloadedModuleDescriptionImpl(modulePath, dependencyModuleNames, contentRoots)
    }.filterNotNull().toMutableList()

    if (unloaded.isNotEmpty()) {
      val modulesToLoad = HashSet(modulePathsToLoad)
      ModuleManagerEx.getInstanceEx(project).unloadNewlyAddedModulesIfPossible(modulesToLoad, unloaded)
    }
  }

  fun saveChangedProjectEntities(writer: JpsFileContentWriter) {
    LOG.debug("Saving project entities")
    val data = serializers.get()
    if (data == null) {
      LOG.debug("Skipping save because initial loading wasn't performed")
      return
    }
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    val affectedSources = synchronized(sourcesToSave) {
      val copy = HashSet(sourcesToSave)
      sourcesToSave.clear()
      copy
    }
    LOG.debugValues("Saving affected entities", affectedSources)
    data.saveEntities(storage, affectedSources, writer)
  }

  @TestOnly
  fun markAllEntitiesAsDirty() {
    val allSources = WorkspaceModel.getInstance(project).entityStorage.current.entitiesBySource { true }.keys
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

  companion object {
    fun getInstance(project: Project): JpsProjectModelSynchronizer? = project.getComponent(JpsProjectModelSynchronizer::class.java)
    private val LOG = logger<JpsProjectModelSynchronizer>()
  }

  private class IdeErrorReporter : ErrorReporter {
    override fun reportError(message: String, file: VirtualFileUrl) {
      //todo implement
    }
  }
}

