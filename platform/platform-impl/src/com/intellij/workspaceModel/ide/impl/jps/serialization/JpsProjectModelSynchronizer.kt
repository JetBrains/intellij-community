// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.configurationStore.isFireStorageFileChangedEvent
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ProjectLoadingErrorsNotifier
import com.intellij.openapi.module.impl.ModuleManagerEx
import com.intellij.openapi.module.impl.UnloadedModuleDescriptionImpl
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getExternalConfigurationDir
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.project.stateStore
import com.intellij.util.PlatformUtils
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelInitialTestContent
import com.intellij.workspaceModel.ide.impl.recordModuleLoadingActivity
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.util.JpsPathUtil
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages serialization and deserialization from JPS format (*.iml and *.ipr files, .idea directory) for workspace model in IDE.
 */
class JpsProjectModelSynchronizer(private val project: Project) : Disposable {
  private val incomingChanges = Collections.synchronizedList(ArrayList<JpsConfigurationFilesChange>())
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private lateinit var fileContentReader: JpsFileContentReaderWithCache
  private val serializers = AtomicReference<JpsProjectSerializers?>()
  private val sourcesToSave = Collections.synchronizedSet(HashSet<EntitySource>())

  init {
    if (!project.isDefault) {
      ApplicationManager.getApplication().messageBus.connect(this).subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener {
        override fun projectComponentsInitialized(project: Project) {
          LOG.debug { "Project component initialized" }
          val workspaceModelImpl = WorkspaceModel.getInstance(project) as WorkspaceModelImpl
          if (blockCidrDelayedUpdate()) workspaceModelImpl.blockDelayedLoading()
          if (project === this@JpsProjectModelSynchronizer.project && !workspaceModelImpl.loadedFromCache) {
            LOG.info("Workspace model loaded without cache. Loading real project state into workspace model. ${Thread.currentThread()}")
            loadRealProject(project)
            project.messageBus.syncPublisher(JpsProjectLoadedListener.LOADED).loaded()
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
    val (changedSources, builder) = loadAndReportErrors { serializers.reloadFromChangedFiles(changes, fileContentReader, it) }
    fileContentReader.clearCache()
    LOG.debugValues("Changed entity sources", changedSources)
    if (changedSources.isEmpty() && builder.isEmpty()) return

    ApplicationManager.getApplication().invokeAndWait(Runnable {
      runWriteAction {
        WorkspaceModel.getInstance(project).updateProjectModel { updater ->
          updater.replaceBySource({ it in changedSources || (it is JpsImportedEntitySource && !it.storedExternally && it.internalFile in changedSources) },
                                  builder.toStorage())
        }
        sourcesToSave.removeAll(changedSources)
      }
    })
  }

  private fun <T> loadAndReportErrors(action: (ErrorReporter) -> T): T {
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
        //JPS model is loaded from *.iml files, files in .idea directory (modules.xml), files from directories in .idea (libraries) and *.ipr file
        // so we can ignore all other events to speed up processing
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
    val configLocation: JpsProjectConfigLocation = getJpsProjectConfigLocation(project)!!
    LOG.debug { "Initial loading of project located at $configLocation" }
    if (!(WorkspaceModel.getInstance(project) as WorkspaceModelImpl).loadedFromCache) {
      recordModuleLoadingActivity()
    }
    val activity = StartUpMeasurer.startActivity("(wm) Load initial project")
    var childActivity = activity.startChild("(wm) Prepare serializers")
    val serializers = prepareSerializers()
    registerListener()
    val builder = WorkspaceEntityStorageBuilder.create()

    childActivity = childActivity.endAndStart("(wm) Load state of unloaded modules")
    loadStateOfUnloadedModules(serializers)

    if (!WorkspaceModelInitialTestContent.hasInitialContent) {
      childActivity = childActivity.endAndStart("(wm) Read serializers")
      loadAndReportErrors { serializers.loadAll(fileContentReader, builder, it) }
      childActivity = childActivity.endAndStart("(wm) Add changes to store")
      (WorkspaceModel.getInstance(project) as? WorkspaceModelImpl)?.printInfoAboutTracedEntity(builder, "JPS files")
      WriteAction.runAndWait<RuntimeException> {
        WorkspaceModel.getInstance(project).updateProjectModel { updater ->
          updater.replaceBySource({ it is JpsFileEntitySource || it is JpsFileDependentEntitySource || it is CustomModuleEntitySource
                                    || it is DummyParentEntitySource }, builder.toStorage())
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
      loadAndReportErrors {
        serializers.findModuleSerializer(modulePath)!!.loadEntities(tmpBuilder, fileContentReader, it, virtualFileManager)
      }

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

  // FIXME: 21.01.2021 This is a fix for OC-21192
  // We do disable delayed loading of JPS model if modules.xml file missing (what happens because of bug if you open a project in 2020.3)
  fun blockCidrDelayedUpdate(): Boolean {
    if (!PlatformUtils.isCidr()) return false

    val currentSerializers = prepareSerializers() as JpsProjectSerializersImpl
    if (currentSerializers.moduleSerializers.isNotEmpty()) return false
    return currentSerializers.moduleListSerializersByUrl.keys.all { !JpsPathUtil.urlToFile(it).exists() }
  }

  private fun prepareSerializers(): JpsProjectSerializers {
    val existingSerializers = this.serializers.get()
    if (existingSerializers != null) return existingSerializers

    val configLocation: JpsProjectConfigLocation = getJpsProjectConfigLocation(project)!!
    fileContentReader = (project.stateStore as ProjectStoreWithJpsContentReader).createContentReader()
    val externalStoragePath = project.getExternalConfigurationDir()
    //TODO:: Get rid of dependency on ExternalStorageConfigurationManager in order to use in build process
    val externalStorageConfigurationManager = ExternalStorageConfigurationManager.getInstance(project)
    val fileInDirectorySourceNames = FileInDirectorySourceNames.from(WorkspaceModel.getInstance(project).entityStorage.current)
    val serializers = JpsProjectEntitiesLoader.createProjectSerializers(configLocation, fileContentReader, externalStoragePath, false,
                                                                        virtualFileManager, externalStorageConfigurationManager, fileInDirectorySourceNames)

    this.serializers.set(serializers)
    return serializers
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

  @TestOnly
  fun getSerializers(): JpsProjectSerializersImpl = serializers.get() as JpsProjectSerializersImpl

  companion object {
    fun getInstance(project: Project): JpsProjectModelSynchronizer? = project.getComponent(JpsProjectModelSynchronizer::class.java)
    private val LOG = logger<JpsProjectModelSynchronizer>()
  }
}

