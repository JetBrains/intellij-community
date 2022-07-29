// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.storage.VersionedStorageChange

internal class ModuleVcsDetector(private val project: Project) {
  private val vcsManager by lazy(LazyThreadSafetyMode.NONE) { ProjectLevelVcsManagerImpl.getInstanceImpl(project) }

  private val queue = MergingUpdateQueue("ModuleVcsDetector", 1000, true, null, project, null, Alarm.ThreadToUse.POOLED_THREAD).also {
    it.setRestartTimerOnAdd(true)
  }
  private val dirtyContentRoots: MutableSet<VirtualFile> = mutableSetOf()

  private fun startDetection() {
    val busConnection = project.messageBus.connect()

    WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(busConnection, MyWorkspaceModelChangeListener())

    if (vcsManager.needAutodetectMappings()) {
      WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(busConnection, InitialMappingsDetectionListener())
      queue.queue(InitialFullScan())
    }
  }

  private fun autoDetectVcsMappings(tryMapPieces: Boolean) {
    if (vcsManager.haveDefaultMapping() != null) return

    val usedVcses = mutableSetOf<AbstractVcs?>()
    val detectedRoots = mutableSetOf<Pair<VirtualFile, AbstractVcs>>()

    val contentRoots = runReadAction {
      ModuleManager.getInstance(project).modules.asSequence()
        .flatMap { it.rootManager.contentRoots.asSequence() }
        .filter { it.isDirectory }.distinct().toList()
    }
    for (root in contentRoots) {
      val moduleVcs = vcsManager.findVersioningVcs(root)
      if (moduleVcs != null) {
        detectedRoots.add(Pair(root, moduleVcs))
      }
      usedVcses.add(moduleVcs) // put 'null' for unmapped module
    }

    val commonVcs = usedVcses.singleOrNull()
    if (commonVcs != null) {
      // Remove existing mappings that will duplicate added <Project> mapping.
      val rootPaths = contentRoots.map { it.path }.toSet()
      val additionalMappings = vcsManager.directoryMappings.filter { it.directory !in rootPaths }

      vcsManager.setAutoDirectoryMappings(additionalMappings + VcsDirectoryMapping.createDefault(commonVcs.name))
    }
    else if (tryMapPieces) {
      val newMappings = detectedRoots.map { (root, vcs) -> VcsDirectoryMapping(root.path, vcs.name) }
      vcsManager.setAutoDirectoryMappings(vcsManager.directoryMappings + newMappings)
    }
  }

  private fun autoDetectModuleVcsMapping(contentRoots: List<VirtualFile>) {
    if (vcsManager.haveDefaultMapping() != null) return

    val newMappings = mutableListOf<VcsDirectoryMapping>()
    contentRoots
      .filter { it.isDirectory }
      .forEach { file ->
        val vcs = vcsManager.findVersioningVcs(file)
        if (vcs != null && vcs !== vcsManager.getVcsFor(file)) {
          newMappings.add(VcsDirectoryMapping(file.path, vcs.name))
        }
      }

    if (newMappings.isNotEmpty()) {
      vcsManager.setAutoDirectoryMappings(vcsManager.directoryMappings + newMappings)
    }
  }

  private inner class InitialFullScan : Update("initial scan") {
    override fun run() {
      autoDetectVcsMappings(true)
    }

    override fun canEat(update: Update?): Boolean = update is DelayedFullScan
  }

  private inner class DelayedFullScan : Update("delayed scan") {
    override fun run() {
      autoDetectVcsMappings(false)
    }
  }

  private inner class ContentRootsScan : Update("modules scan") {
    override fun run() {
      val contentRoots: List<VirtualFile>
      synchronized(dirtyContentRoots) {
        contentRoots = dirtyContentRoots.toList()
        dirtyContentRoots.clear()
      }

      autoDetectModuleVcsMapping(contentRoots)
    }
  }

  private inner class MyWorkspaceModelChangeListener : ContentRootChangeListener(skipFileChanges = true) {
    override fun contentRootsChanged(removed: List<VirtualFile>, added: List<VirtualFile>) {
      if (added.isNotEmpty() && vcsManager.haveDefaultMapping() == null) {
        synchronized(dirtyContentRoots) {
          dirtyContentRoots.addAll(added)
          dirtyContentRoots.removeAll(removed)
        }
        queue.queue(ContentRootsScan())
      }

      if (removed.isNotEmpty()) {
        val remotedPaths = removed.map { it.path }.toSet()
        val removedMappings = vcsManager.directoryMappings.filter { it.directory in remotedPaths }
        removedMappings.forEach { mapping -> vcsManager.removeDirectoryMapping(mapping) }
      }
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
