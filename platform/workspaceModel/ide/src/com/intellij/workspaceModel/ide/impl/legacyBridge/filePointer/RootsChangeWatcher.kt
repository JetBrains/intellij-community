// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer

import com.google.common.io.Files
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.getModuleNameByFilePath
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.VirtualFilePointerContainerImpl
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.bracket
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChanged
import com.intellij.workspaceModel.storage.VirtualFileUrl
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.*
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * Provides rootsChanged events if roots validity was changed.
 * It's implemented by a listener to VirtualFilePointerContainer containing all project roots
 */
@ApiStatus.Internal
internal class RootsChangeWatcher(val project: Project): Disposable {
  private val LOG = Logger.getInstance(javaClass)

  private val rootsValidityChangedListener
    get() = ProjectRootManagerImpl.getInstanceImpl(project).rootsValidityChangedListener

  private val roots = Object2IntOpenHashMap<String>()
  private val jarDirectories = Object2IntOpenHashMap<String>()
  private val recursiveJarDirectories = Object2IntOpenHashMap<String>()

  private val moduleManager = ModuleManager.getInstance(project)
  private val rootFilePointers = LegacyModelRootsFilePointers(project)

  private val myExecutor = if (ApplicationManager.getApplication().isUnitTestMode) ConcurrencyUtil.newSameThreadExecutorService()
  else AppExecutorUtil.createBoundedApplicationPoolExecutor("Workspace Model Project Root Manager", 1)
  private var myCollectWatchRootsFuture: Future<*> = CompletableFuture.completedFuture(null) // accessed in EDT only

  init {
    val messageBusConnection = project.messageBus.connect()
    WorkspaceModelTopics.getInstance(project).subscribeImmediately(messageBusConnection, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChanged) = LOG.bracket("LibraryRootsWatcher.EntityStoreChange") {
        // TODO It's also possible to calculate it on diffs

        event.getAllChanges().forEach { change ->
          when (change) {
            is EntityChange.Added -> updateVirtualFileUrlCollections(change.entity)
            is EntityChange.Removed -> updateVirtualFileUrlCollections(change.entity, true)
            is EntityChange.Replaced -> {
              updateVirtualFileUrlCollections(change.oldEntity, true)
              updateVirtualFileUrlCollections(change.newEntity)
            }
          }
        }

        rootFilePointers.onModelChange(event.storageAfter)

        myCollectWatchRootsFuture.cancel(false)
        myCollectWatchRootsFuture = myExecutor.submit {
          ReadAction.run<Throwable> {
            newSync(
              newRoots = roots.keys,
              newJarDirectories = jarDirectories.keys,
              newRecursiveJarDirectories = recursiveJarDirectories.keys
            )
          }
        }
      }
    })
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun before(events: List<VFileEvent>): Unit = events.forEach { event ->
        val (oldUrl, newUrl) = getUrls(event) ?: return@forEach

        rootFilePointers.onVfsChange(oldUrl, newUrl)
      }

      override fun after(events: List<VFileEvent>) = events.forEach { event ->
        if (event is VFilePropertyChangeEvent) propertyChanged(event)

        val (oldUrl, newUrl) = getUrls(event) ?: return@forEach
        updateCollection(roots, oldUrl, newUrl)
        updateCollection(jarDirectories, oldUrl, newUrl)
        updateCollection(recursiveJarDirectories, oldUrl, newUrl)
        updateModuleName(oldUrl, newUrl)
      }

      private fun updateModuleName(oldUrl: String, newUrl: String) {
        if (!oldUrl.isImlFile() || !newUrl.isImlFile()) return
        val oldModuleName = getModuleNameByFilePath(oldUrl)
        val newModuleName = getModuleNameByFilePath(newUrl)
        if (oldModuleName == newModuleName) return

        val workspaceModel = WorkspaceModel.getInstance(project)
        val moduleEntity = workspaceModel.entityStorage.current.resolve(ModuleId(oldModuleName)) ?: return
        workspaceModel.updateProjectModel { diff ->
          diff.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) { this.name = newModuleName }
        }
      }

      private fun propertyChanged(event: VFilePropertyChangeEvent) {
        if (!event.file.isDirectory || event.requestor is StateStorage || event.propertyName != VirtualFile.PROP_NAME) return

        val parentPath = event.file.parent?.path ?: return
        val newAncestorPath = "$parentPath/${event.newValue}"
        val oldAncestorPath = "$parentPath/${event.oldValue}"
        var someModulePathIsChanged = false
        for (module in moduleManager.modules) {
          if (!module.isLoaded || module.isDisposed) continue

          val moduleFilePath = module.moduleFilePath
          if (FileUtil.isAncestor(oldAncestorPath, moduleFilePath, true)) {
            module.stateStore.setPath(Paths.get(newAncestorPath, FileUtil.getRelativePath(oldAncestorPath, moduleFilePath, '/')))
            ClasspathStorage.modulePathChanged(module)
            someModulePathIsChanged = true
          }
        }
        if (someModulePathIsChanged) moduleManager.incModificationCount()
      }

      private fun String.isImlFile() = Files.getFileExtension(this) == ModuleFileType.DEFAULT_EXTENSION

      /** Update stored urls after folder movement */
      private fun getUrls(event: VFileEvent): Pair<String, String>? {
        val oldUrl: String
        val newUrl: String
        when (event) {
          is VFilePropertyChangeEvent -> {
            oldUrl = VfsUtilCore.pathToUrl(event.oldPath)
            newUrl = VfsUtilCore.pathToUrl(event.newPath)
          }
          is VFileMoveEvent -> {
            oldUrl = VfsUtilCore.pathToUrl(event.oldPath)
            newUrl = VfsUtilCore.pathToUrl(event.newPath)
          }
          else -> return null
        }
        return oldUrl to newUrl
      }
    })
  }

  private var disposable = Disposer.newDisposable()

  private fun updateVirtualFileUrlCollections(entity: WorkspaceEntity, removeAction: Boolean = false) {
    when (entity) {
      is SourceRootEntity -> updateCollection(roots, entity.url, removeAction)
      is ContentRootEntity -> {
        updateCollection(roots, entity.url, removeAction)
        entity.excludedUrls.forEach { updateCollection(roots, it, removeAction) }
      }
      is LibraryEntity -> {
        entity.excludedRoots.forEach { updateCollection(roots, it, removeAction) }
        for (root in entity.roots) {
          when (root.inclusionOptions) {
            LibraryRoot.InclusionOptions.ROOT_ITSELF -> updateCollection(roots, root.url, removeAction)
            LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT -> updateCollection(jarDirectories, root.url, removeAction)
            LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY -> updateCollection(recursiveJarDirectories, root.url,
                                                                                             removeAction)
          }
        }
      }
      is SdkEntity -> updateCollection(roots, entity.homeUrl, removeAction)
      is JavaModuleSettingsEntity -> {
        entity.compilerOutput?.let { updateCollection(roots, it, removeAction) }
        entity.compilerOutputForTests?.let { updateCollection(roots, it, removeAction) }
      }
    }
  }

  private fun updateCollection(collection: Object2IntOpenHashMap<String>, fileUrl: VirtualFileUrl, removeAction: Boolean) {
    collection.compute(fileUrl.url) { _, usagesCount ->
      if (removeAction) {
        if (usagesCount == null || usagesCount - 1 <= 0) return@compute null else return@compute usagesCount - 1
      } else {
        if (usagesCount == null) return@compute 1 else return@compute usagesCount + 1
      }
    }
  }

  private fun updateCollection(collection: Object2IntOpenHashMap<String>, oldUrl: String, newUrl: String) {
    if (!collection.containsKey(oldUrl))  return
    val valueByOldUrl = collection.removeInt(oldUrl)
    val currentValueByNewUrl = collection.getInt(newUrl)
    collection[newUrl] = currentValueByNewUrl + valueByOldUrl
  }

  private fun newSync(newRoots: Set<String>,
                      newJarDirectories: Set<String>,
                      newRecursiveJarDirectories: Set<String>) {
    val oldDisposable = disposable
    val newDisposable = Disposer.newDisposable()
    // creating a container with these URLs with the sole purpose to get events to getRootsValidityChangedListener() when these roots change
    val container = VirtualFilePointerManager.getInstance().createContainer(newDisposable, rootsValidityChangedListener)

    container as VirtualFilePointerContainerImpl
    container.addAll(newRoots)
    container.addAllJarDirectories(newJarDirectories, false)
    container.addAllJarDirectories(newRecursiveJarDirectories, true)

    disposable = newDisposable
    Disposer.dispose(oldDisposable)
  }

  private fun clear() {
    roots.clear()
    jarDirectories.clear()
    recursiveJarDirectories.clear()
    rootFilePointers.clear()
  }

  override fun dispose() {
    clear()

    myCollectWatchRootsFuture.cancel(false)
    myExecutor.shutdownNow()

    Disposer.dispose(disposable)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): RootsChangeWatcher = project.getComponent(RootsChangeWatcher::class.java)
  }
}
