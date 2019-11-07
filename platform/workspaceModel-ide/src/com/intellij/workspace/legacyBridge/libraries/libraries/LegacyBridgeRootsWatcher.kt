package com.intellij.workspace.legacyBridge.libraries.libraries

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.workspace.api.*
import com.intellij.workspace.bracket
import com.intellij.workspace.ide.WorkspaceModelChangeListener
import com.intellij.workspace.ide.WorkspaceModelTopics

/**
 * Provides rootsChanged events if roots validity was changed.
 * It's implemented by a listener to VirtualFilePointerContainer containing all project roots
 */
class LegacyBridgeRootsWatcher(
  val project: Project
): Disposable {

  private val LOG = Logger.getInstance(javaClass)

  private val rootsValidityChangedListener
    get() = ProjectRootManagerImpl.getInstanceImpl(project).rootsValidityChangedListener

  private val virtualFilePointerManager = VirtualFilePointerManager.getInstance()

  init {
    val messageBusConnection = project.messageBus.connect(project)
    messageBusConnection.subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun changed(event: EntityStoreChanged) = LOG.bracket("LibraryRootsWatcher.EntityStoreChange") {
        // TODO It's also possible to calculate it on diffs

        val roots = mutableSetOf<VirtualFileUrl>()
        val jarDirectories = mutableSetOf<VirtualFileUrl>()
        val recursiveJarDirectories = mutableSetOf<VirtualFileUrl>()

        val s = event.storageAfter

        s.entities(SourceRootEntity::class).forEach { roots.add(it.url) }
        s.entities(ContentRootEntity::class).forEach { roots.add(it.url) }
        s.entities(LibraryEntity::class).forEach {
          roots.addAll(it.excludedRoots)
          for (root in it.roots) {
            when (root.inclusionOptions) {
              LibraryRoot.InclusionOptions.ROOT_ITSELF -> roots.add(root.url)
              LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT -> jarDirectories.add(root.url)
              LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY -> recursiveJarDirectories.add(root.url)
            }.let { } // exhaustive when
          }
        }
        s.entities(SdkEntity::class).forEach { roots.add(it.homeUrl) }

        syncNewRootsToContainer(
          newRoots = roots,
          newJarDirectories = jarDirectories,
          newRecursiveJarDirectories = recursiveJarDirectories
        )
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
      if (LOG.isDebugEnabled) {
        LOG.debug("Removed root $removed")
      }

      Disposer.dispose(currentRoots.getValue(removed))
    }

    for (removedJarDirectory in currentJarDirectories.keys - newJarDirectories) {
      if (LOG.isDebugEnabled) {
        LOG.debug("Removed jar directory root $removedJarDirectory")
      }

      Disposer.dispose(currentJarDirectories.getValue(removedJarDirectory))
    }

    for (removedRecursiveJarDirectory in currentRecursiveJarDirectories.keys - newRecursiveJarDirectories) {
      if (LOG.isDebugEnabled) {
        LOG.debug("Removed recursive jar directory root $removedRecursiveJarDirectory")
      }

      Disposer.dispose(currentRecursiveJarDirectories.getValue(removedRecursiveJarDirectory))
    }

    for (added in newRoots - currentRoots.keys) {
      val dispose = Disposer.newDisposable()
      currentRoots[added] = dispose
      virtualFilePointerManager.create(added.url, dispose, rootsValidityChangedListener)

      if (LOG.isDebugEnabled) {
        LOG.debug("Added root $added")
      }
    }

    for (addedJarDirectory in newJarDirectories - currentJarDirectories.keys) {
      val dispose = Disposer.newDisposable()
      currentRoots[addedJarDirectory] = dispose
      virtualFilePointerManager.createDirectoryPointer(addedJarDirectory.url, false, dispose, rootsValidityChangedListener)

      if (LOG.isDebugEnabled) {
        LOG.debug("Added jar directory $addedJarDirectory")
      }
    }

    for (addedRecursiveJarDirectory in newRecursiveJarDirectories - currentRecursiveJarDirectories.keys) {
      val dispose = Disposer.newDisposable()
      currentRoots[addedRecursiveJarDirectory] = dispose
      virtualFilePointerManager.createDirectoryPointer(addedRecursiveJarDirectory.url, true, dispose, rootsValidityChangedListener)

      if (LOG.isDebugEnabled) {
        LOG.debug("Added recursive jar directory $addedRecursiveJarDirectory")
      }
    }
  }

  override fun dispose() {
    currentRoots.values.forEach { Disposer.dispose(it) }
    currentJarDirectories.values.forEach { Disposer.dispose(it) }
    currentRecursiveJarDirectories.values.forEach { Disposer.dispose(it) }

    currentRoots.clear()
    currentJarDirectories.clear()
    currentRecursiveJarDirectories.clear()
  }
}
