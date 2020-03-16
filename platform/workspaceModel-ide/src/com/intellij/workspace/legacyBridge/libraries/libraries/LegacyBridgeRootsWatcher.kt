// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.libraries.libraries

import com.google.common.io.Files
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.impl.getModuleNameByFilePath
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.workspace.api.*
import com.intellij.workspace.bracket
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.ide.WorkspaceModelChangeListener
import com.intellij.workspace.ide.WorkspaceModelTopics
import org.jetbrains.annotations.ApiStatus

/**
 * Provides rootsChanged events if roots validity was changed.
 * It's implemented by a listener to VirtualFilePointerContainer containing all project roots
 */
@ApiStatus.Internal
class LegacyBridgeRootsWatcher(
  val project: Project
): Disposable {

  private val LOG = Logger.getInstance(javaClass)

  private val rootsValidityChangedListener
    get() = ProjectRootManagerImpl.getInstanceImpl(project).rootsValidityChangedListener

  private val virtualFilePointerManager = VirtualFilePointerManager.getInstance()

  private val rootFilePointers = LegacyModelRootsFilePointers(project)

  init {
    val messageBusConnection = project.messageBus.connect()
    WorkspaceModelTopics.getInstance(project).subscribeImmediately(messageBusConnection, object : WorkspaceModelChangeListener {
      override fun changed(event: EntityStoreChanged) = LOG.bracket("LibraryRootsWatcher.EntityStoreChange") {
        // TODO It's also possible to calculate it on diffs

        val roots = mutableSetOf<VirtualFileUrl>()
        val jarDirectories = mutableSetOf<VirtualFileUrl>()
        val recursiveJarDirectories = mutableSetOf<VirtualFileUrl>()

        val s = event.storageAfter

        s.entities(SourceRootEntity::class.java).forEach { roots.add(it.url) }
        s.entities(ContentRootEntity::class.java).forEach {
          roots.add(it.url)
          roots.addAll(it.excludedUrls)
        }
        s.entities(LibraryEntity::class.java).forEach {
          roots.addAll(it.excludedRoots)
          for (root in it.roots) {
            when (root.inclusionOptions) {
              LibraryRoot.InclusionOptions.ROOT_ITSELF -> roots.add(root.url)
              LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT -> jarDirectories.add(root.url)
              LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY -> recursiveJarDirectories.add(root.url)
            }.let { } // exhaustive when
          }
        }
        s.entities(SdkEntity::class.java).forEach { roots.add(it.homeUrl) }
        s.entities(JavaModuleSettingsEntity::class.java).forEach { javaSettings -> javaSettings.compilerOutput?.let { roots.add(it) } }
        s.entities(JavaModuleSettingsEntity::class.java).forEach { javaSettings -> javaSettings.compilerOutputForTests?.let { roots.add(it) } }

        rootFilePointers.onModelChange(s)
        syncNewRootsToContainer(
          newRoots = roots,
          newJarDirectories = jarDirectories,
          newRecursiveJarDirectories = recursiveJarDirectories
        )
      }
    })
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun before(events: List<VFileEvent>): Unit = events.forEach { event ->
        val (oldUrl, newUrl) = getUrls(event) ?: return@forEach

        rootFilePointers.onVfsChange(oldUrl, newUrl)
      }

      override fun after(events: List<VFileEvent>) = events.forEach { event ->
        val (oldUrl, newUrl) = getUrls(event) ?: return@forEach

        updateModuleName(oldUrl, newUrl)
        updateRoots(currentRoots, oldUrl, newUrl)
        updateRoots(currentJarDirectories, oldUrl, newUrl)
        updateRoots(currentRecursiveJarDirectories, oldUrl, newUrl)
      }

      private fun updateRoots(map: MutableMap<VirtualFileUrl, Disposable>, oldUrl: String, newUrl: String) {
        map.filter { it.key.url == oldUrl }.forEach { (url, disposable) ->
          map.remove(url)
          map[VirtualFileUrlManager.fromUrl(newUrl)] = disposable
        }
      }

      private fun updateModuleName(oldUrl: String, newUrl: String) {
        if (!oldUrl.isImlFile() || !newUrl.isImlFile()) return
        val oldModuleName = getModuleNameByFilePath(oldUrl)
        val newModuleName = getModuleNameByFilePath(newUrl)
        if (oldModuleName == newModuleName) return

        val workspaceModel = WorkspaceModel.getInstance(project)
        val moduleEntity = workspaceModel.entityStore.current.resolve(ModuleId(oldModuleName)) ?: return
        workspaceModel.updateProjectModel { diff ->
          diff.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) { this.name = newModuleName }
        }
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

  private val currentRoots = mutableMapOf<VirtualFileUrl, Disposable>()
  private val currentJarDirectories = mutableMapOf<VirtualFileUrl, Disposable>()
  private val currentRecursiveJarDirectories = mutableMapOf<VirtualFileUrl, Disposable>()

  private fun syncNewRootsToContainer(newRoots: Set<VirtualFileUrl>, newJarDirectories: Set<VirtualFileUrl>, newRecursiveJarDirectories: Set<VirtualFileUrl>) {
    if (currentRoots.keys == newRoots && currentJarDirectories.keys == newJarDirectories && currentRecursiveJarDirectories.keys == newRecursiveJarDirectories) {
      return
    }

    for (removed in currentRoots.keys - newRoots) {
      LOG.debug { "Removed root $removed" }

      Disposer.dispose(currentRoots.getValue(removed))
      currentRoots.remove(removed)
    }

    for (removedJarDirectory in currentJarDirectories.keys - newJarDirectories) {
      LOG.debug { "Removed jar directory root $removedJarDirectory" }

      Disposer.dispose(currentJarDirectories.getValue(removedJarDirectory))
      currentJarDirectories.remove(removedJarDirectory)
    }

    for (removedRecursiveJarDirectory in currentRecursiveJarDirectories.keys - newRecursiveJarDirectories) {
      LOG.debug { "Removed recursive jar directory root $removedRecursiveJarDirectory" }

      Disposer.dispose(currentRecursiveJarDirectories.getValue(removedRecursiveJarDirectory))
      currentRecursiveJarDirectories.remove(removedRecursiveJarDirectory)
    }

    for (added in newRoots - currentRoots.keys) {
      val dispose = Disposer.newDisposable()
      currentRoots[added] = dispose
      virtualFilePointerManager.create(added.url, dispose, rootsValidityChangedListener)

      LOG.debug { "Added root $added" }
    }

    for (addedJarDirectory in newJarDirectories - currentJarDirectories.keys) {
      val dispose = Disposer.newDisposable()
      currentRoots[addedJarDirectory] = dispose
      virtualFilePointerManager.createDirectoryPointer(addedJarDirectory.url, false, dispose, rootsValidityChangedListener)

      LOG.debug { "Added jar directory $addedJarDirectory" }
    }

    for (addedRecursiveJarDirectory in newRecursiveJarDirectories - currentRecursiveJarDirectories.keys) {
      val dispose = Disposer.newDisposable()
      currentRoots[addedRecursiveJarDirectory] = dispose
      virtualFilePointerManager.createDirectoryPointer(addedRecursiveJarDirectory.url, true, dispose, rootsValidityChangedListener)

      LOG.debug { "Added recursive jar directory $addedRecursiveJarDirectory" }
    }
  }

  fun clear() {
    currentRoots.values.forEach { Disposer.dispose(it) }
    currentJarDirectories.values.forEach { Disposer.dispose(it) }
    currentRecursiveJarDirectories.values.forEach { Disposer.dispose(it) }

    currentRoots.clear()
    currentJarDirectories.clear()
    currentRecursiveJarDirectories.clear()

    rootFilePointers.clear()
  }

  override fun dispose() {
    clear()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LegacyBridgeRootsWatcher = project.getComponent(LegacyBridgeRootsWatcher::class.java)
  }
}
