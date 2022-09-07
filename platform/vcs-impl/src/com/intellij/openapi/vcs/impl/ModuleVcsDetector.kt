// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.storage.VersionedStorageChange

internal class ModuleVcsDetector(private val project: Project) {
  private val vcsManager by lazy(LazyThreadSafetyMode.NONE) { ProjectLevelVcsManagerImpl.getInstanceImpl(project) }

  private val queue = MergingUpdateQueue("ModuleVcsDetector", 1000, true, null, project, null, Alarm.ThreadToUse.POOLED_THREAD).also {
    it.setRestartTimerOnAdd(true)
  }

  private fun startDetection() {
    val busConnection = project.messageBus.connect()

    WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(busConnection, MyWorkspaceModelChangeListener())

    if (vcsManager.needAutodetectMappings()) {
      WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(busConnection, InitialMappingsDetectionListener())
      queue.queue(InitialFullScan())
    }
  }

  @RequiresBackgroundThread
  private fun autoDetectDefaultRoots(tryMapPieces: Boolean) {
    if (vcsManager.haveDefaultMapping() != null) return

    val usedVcses = mutableSetOf<AbstractVcs>()
    val detectedRoots = mutableSetOf<Pair<VirtualFile, AbstractVcs>>()

    val contentRoots = DefaultVcsRootPolicy.getInstance(project).defaultVcsRoots
    contentRoots
      .forEach { root ->
        val foundVcs = vcsManager.findVersioningVcs(root)
        if (foundVcs != null) {
          detectedRoots.add(Pair(root, foundVcs))
          usedVcses.add(foundVcs)
        }
      }
    if (detectedRoots.isEmpty()) return

    val commonVcs = usedVcses.singleOrNull()
    if (commonVcs != null) {
      // Remove existing mappings that will duplicate added <Project> mapping.
      val rootPaths = detectedRoots.mapTo(mutableSetOf()) { it.first.path }
      val additionalMappings = vcsManager.directoryMappings.filter { it.vcs != commonVcs.name || it.directory !in rootPaths }
      vcsManager.setAutoDirectoryMappings(additionalMappings + VcsDirectoryMapping.createDefault(commonVcs.name))
    }
    else if (tryMapPieces) {
      registerNewDirectMappings(detectedRoots)
    }
  }

  @RequiresBackgroundThread
  private fun autoDetectForContentRoots(contentRoots: List<VirtualFile>) {
    if (vcsManager.haveDefaultMapping() != null) return

    val detectedRoots = mutableSetOf<Pair<VirtualFile, AbstractVcs>>()

    contentRoots
      .filter { it.isInLocalFileSystem }
      .filter { it.isDirectory }
      .forEach { root ->
        val foundVcs = vcsManager.findVersioningVcs(root)
        if (foundVcs != null && foundVcs !== vcsManager.getVcsFor(root)) {
          detectedRoots.add(Pair(root, foundVcs))
        }
      }
    if (detectedRoots.isEmpty()) return

    registerNewDirectMappings(detectedRoots)
  }

  private fun registerNewDirectMappings(detectedRoots: Collection<Pair<VirtualFile, AbstractVcs>>) {
    val oldMappings = vcsManager.directoryMappings
    val knownMappedRoots = oldMappings.mapTo(mutableSetOf()) { it.directory }
    val newMappings = detectedRoots.asSequence()
      .map { (root, vcs) -> VcsDirectoryMapping(root.path, vcs.name) }
      .filter { it.directory !in knownMappedRoots }
    vcsManager.setAutoDirectoryMappings(oldMappings + newMappings)
  }

  private inner class InitialFullScan : Update("initial scan") {
    override fun run() {
      autoDetectDefaultRoots(true)
    }

    override fun canEat(update: Update?): Boolean = update is DelayedFullScan
  }

  private inner class DelayedFullScan : Update("delayed scan") {
    override fun run() {
      autoDetectDefaultRoots(false)
    }
  }

  private inner class MyWorkspaceModelChangeListener : ContentRootChangeListener(skipFileChanges = true) {
    private val dirtyContentRoots = mutableSetOf<VirtualFile>()

    override fun contentRootsChanged(removed: List<VirtualFile>, added: List<VirtualFile>) {
      if (added.isNotEmpty() && vcsManager.haveDefaultMapping() == null) {
        synchronized(dirtyContentRoots) {
          dirtyContentRoots.addAll(added)
          dirtyContentRoots.removeAll(removed.toSet())
        }
        queue.queue(Update.create("content root scan") { runScanForNewContentRoots() })
      }

      if (removed.isNotEmpty()) {
        val remotedPaths = removed.map { it.path }.toSet()
        val removedMappings = vcsManager.directoryMappings.filter { it.directory in remotedPaths }
        removedMappings.forEach { mapping -> vcsManager.removeDirectoryMapping(mapping) }
      }
    }

    private fun runScanForNewContentRoots() {
      val contentRoots: List<VirtualFile>
      synchronized(dirtyContentRoots) {
        contentRoots = dirtyContentRoots.toList()
        dirtyContentRoots.clear()
      }

      autoDetectForContentRoots(contentRoots)
    }
  }

  private inner class InitialMappingsDetectionListener : ContentRootChangeListener(skipFileChanges = true) {
    override fun changed(event: VersionedStorageChange) {
      if (!vcsManager.needAutodetectMappings()) return
      super.changed(event)
    }

    override fun contentRootsChanged(removed: List<VirtualFile>, added: List<VirtualFile>) {
      queue.queue(DelayedFullScan())
    }
  }

  internal class MyPostStartUpActivity : StartupActivity.DumbAware {
    init {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override fun runActivity(project: Project) {
      project.service<ModuleVcsDetector>().startDetection()
    }
  }
}
