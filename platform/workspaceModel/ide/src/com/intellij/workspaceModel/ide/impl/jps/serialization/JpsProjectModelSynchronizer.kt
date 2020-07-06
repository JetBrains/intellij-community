// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.configurationStore.*
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.module.impl.AutomaticModuleUnloader
import com.intellij.openapi.module.impl.UnloadedModuleDescriptionImpl
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getExternalConfigurationDir
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.project.stateStore
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.WorkspaceModelInitialTestContent
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.isExternalModuleFile
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import org.jdom.Element
import org.jetbrains.jps.util.JpsPathUtil
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet

internal class JpsProjectModelSynchronizer(private val project: Project) : Disposable {
  private val incomingChanges = Collections.synchronizedList(ArrayList<JpsConfigurationFilesChange>())
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private lateinit var fileContentReader: StorageJpsConfigurationReader
  private val serializers = AtomicReference<JpsProjectSerializers?>()
  private val sourcesToSave = Collections.synchronizedSet(HashSet<EntitySource>())

  init {
    if (!project.isDefault && enabled) {
      ApplicationManager.getApplication().messageBus.connect(this).subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener {
        override fun projectComponentsInitialized(project: Project) {
          if (project === this@JpsProjectModelSynchronizer.project) {
            loadInitialProject(project.configLocation!!)
          }
        }
      })
    }
  }

  internal fun needToReloadProjectEntities(): Boolean {
    if (!enabled) return false
    if (StoreReloadManager.getInstance().isReloadBlocked()) return false
    if (serializers.get() == null) return false

    synchronized(incomingChanges) {
      return incomingChanges.isNotEmpty()
    }
  }

  internal fun reloadProjectEntities() {
    if (!enabled) return

    if (StoreReloadManager.getInstance().isReloadBlocked()) return
    val serializers = serializers.get() ?: return
    val changes = getAndResetIncomingChanges() ?: return

    val (changedEntities, builder) = serializers.reloadFromChangedFiles(changes, fileContentReader)
    if (changedEntities.isEmpty() && builder.isEmpty()) return

    ApplicationManager.getApplication().invokeAndWait(Runnable {
      runWriteAction {
        WorkspaceModel.getInstance(project).updateProjectModel { updater ->
          updater.replaceBySource({ it in changedEntities }, builder.toStorage())
        }
        sourcesToSave.removeAll(changedEntities)
      }
    })
  }

  private fun registerListener() {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        //todo support move/rename
        //todo optimize: filter events before creating lists
        val toProcess = events.asSequence().filter { isFireStorageFileChangedEvent(it) }
        val addedUrls = toProcess.filterIsInstance<VFileCreateEvent>().mapTo(ArrayList()) { JpsPathUtil.pathToUrl(it.path) }
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
      override fun changed(event: VersionedStorageChanged) {
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

  internal fun loadInitialProject(configLocation: JpsProjectConfigLocation) {
    val activity = StartUpMeasurer.startActivity("(wm) Load initial project")
    var childActivity = activity.startChild("(wm) Prepare serializers")
    val baseDirUrl = configLocation.baseDirectoryUrlString
    fileContentReader = StorageJpsConfigurationReader(project, baseDirUrl)
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
      serializers.loadAll(fileContentReader, builder)
      childActivity = childActivity.endAndStart("(wm) Add changes to store")
      WriteAction.runAndWait<RuntimeException> {
        WorkspaceModel.getInstance(project).updateProjectModel { updater ->
          updater.replaceBySource({ it is JpsFileEntitySource || it is JpsImportedEntitySource }, builder.toStorage())
        }
      }
      sourcesToSave.clear()
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
      serializers.fileSerializersByUrl.get(VfsUtilCore.pathToUrl(modulePath.path)).first()
        .loadEntities(tmpBuilder, fileContentReader, virtualFileManager)

      val moduleEntity = tmpBuilder.resolve(ModuleId(modulePath.moduleName)) ?: return@map null
      val pointerManager = VirtualFilePointerManager.getInstance()
      val contentRoots = moduleEntity.contentRoots.sortedBy { contentEntry -> contentEntry.url.url }
        .map { contentEntry -> pointerManager.create(contentEntry.url.url, this, null) }.toMutableList()
      val dependencyModuleNames = moduleEntity.dependencies.filterIsInstance(ModuleDependencyItem.Exportable.ModuleDependency::class.java)
        .map { moduleDependency -> moduleDependency.module.name }

      UnloadedModuleDescriptionImpl(modulePath, dependencyModuleNames, contentRoots)
    }.filterNotNull().toMutableList()

    if (unloaded.isNotEmpty()) {
      val changeUnloaded = AutomaticModuleUnloader.getInstance(project).processNewModules(modulePathsToLoad.toSet(), unloaded)
      unloaded.addAll(changeUnloaded.toUnloadDescriptions)
    }

    val unloadedModules = ModuleManagerComponentBridge.getInstance(project).unloadedModules
    unloadedModules.clear()
    unloaded.associateByTo(unloadedModules) { it.name }
  }

  internal fun saveChangedProjectEntities(writer: JpsFileContentWriter) {
    if (!enabled) return

    val data = serializers.get() ?: return
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    val affectedSources = synchronized(sourcesToSave) {
      val copy = HashSet(sourcesToSave)
      sourcesToSave.clear()
      copy
    }
    data.saveEntities(storage, affectedSources, writer)
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

    var enabled = Registry.`is`("ide.workspace.model.jps.enabled")
  }
}

private class StorageJpsConfigurationReader(private val project: Project,
                                            private val baseDirUrl: String) : JpsFileContentReader {
  override fun loadComponent(fileUrl: String, componentName: String, customModuleFilePath: String?): Element? {
    val filePath = JpsPathUtil.urlToPath(fileUrl)
    if (FileUtil.extensionEquals(filePath, "iml") || isExternalModuleFile(filePath)) {
      //todo fetch data from ModuleStore (https://jetbrains.team/p/wm/issues/51)
      return CachingJpsFileContentReader(baseDirUrl).loadComponent(fileUrl, componentName, customModuleFilePath)
    }
    else {
      val storage = getProjectStateStorage(filePath, project.stateStore, project) ?: return null
      val stateMap = storage.getStorageData()
      return if (storage is DirectoryBasedStorageBase) {
        val elementContent = stateMap.getElement(PathUtil.getFileName(filePath))
        if (elementContent != null) {
          Element(FileStorageCoreUtil.COMPONENT).setAttribute(FileStorageCoreUtil.NAME, componentName).addContent(elementContent)
        }
        else {
          null
        }
      }
      else {
        stateMap.getElement(componentName)
      }
    }
  }
}

internal fun getProjectStateStorage(filePath: String,
                                    store: IProjectStore,
                                    project: Project): StateStorageBase<StateMap>? {
  val storageSpec = getStorageSpec(filePath, project) ?: return null
  @Suppress("UNCHECKED_CAST")
  return store.storageManager.getStateStorage(storageSpec) as StateStorageBase<StateMap>
}

private fun getStorageSpec(filePath: String, project: Project): Storage? {
  val collapsedPath: String
  val splitterClass: Class<out StateSplitterEx>
  if (FileUtil.extensionEquals(filePath, "ipr")) {
    collapsedPath = "\$PROJECT_FILE$"
    splitterClass = StateSplitterEx::class.java
  }
  else {
    val fileName = PathUtil.getFileName(filePath)
    val parentPath = PathUtil.getParentPath(filePath)
    val parentFileName = PathUtil.getFileName(parentPath)
    if (parentFileName == Project.DIRECTORY_STORE_FOLDER) {
      collapsedPath = fileName
      splitterClass = StateSplitterEx::class.java
    }
    else {
      val grandParentPath = PathUtil.getParentPath(parentPath)
      collapsedPath = parentFileName
      splitterClass = FakeDirectoryBasedStateSplitter::class.java
      if (PathUtil.getFileName(grandParentPath) != Project.DIRECTORY_STORE_FOLDER) {
        val providerFactory = StreamProviderFactory.EP_NAME.getExtensions(project).firstOrNull() ?: return null
        if (parentFileName == "project") {
          if (fileName == "libraries.xml" || fileName == "artifacts.xml") {
            val inProjectStorage = FileStorageAnnotation(FileUtil.getNameWithoutExtension(fileName), false, splitterClass)
            val componentName = if (fileName == "libraries.xml") "libraryTable" else "ArtifactManager"
            return providerFactory.getOrCreateStorageSpec(fileName, StateAnnotation(componentName, inProjectStorage))
          }
          if (fileName == "modules.xml") {
            return providerFactory.getOrCreateStorageSpec(fileName)
          }
        }
        error("$filePath is not under .idea directory and not under external system cache")
      }
    }
  }
  return FileStorageAnnotation(collapsedPath, false, splitterClass)
}

/**
 * This fake implementation is used to force creating directory based storage in StateStorageManagerImpl.createStateStorage
 */
private class FakeDirectoryBasedStateSplitter : StateSplitterEx() {
  override fun splitState(state: Element): MutableList<Pair<Element, String>> {
    throw AssertionError()
  }
}

internal class StoreReloadManagerBridge : StoreReloadManagerImpl() {
  override fun mayHaveAdditionalConfigurations(project: Project): Boolean {
    return JpsProjectModelSynchronizer.getInstance(project)?.needToReloadProjectEntities() ?: false
  }

  override fun reloadAdditionalConfigurations(project: Project) {
    JpsProjectModelSynchronizer.getInstance(project)?.reloadProjectEntities()
  }
}