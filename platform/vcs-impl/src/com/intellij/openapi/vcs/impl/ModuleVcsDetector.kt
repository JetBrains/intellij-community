// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx.MAPPING_DETECTION_LOG
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.workspaceModel.ide.WorkspaceModelTopics

internal class ModuleVcsDetector(private val project: Project) {
  private val vcsManager by lazy(LazyThreadSafetyMode.NONE) { ProjectLevelVcsManagerImpl.getInstanceImpl(project) }

  private val queue = MergingUpdateQueue("ModuleVcsDetector", 1000, true, null, project, null, Alarm.ThreadToUse.POOLED_THREAD).also {
    it.setRestartTimerOnAdd(true)
  }

  private fun startDetection() {
    MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.startDetection")
    project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, MyWorkspaceModelChangeListener())

    if (vcsManager.needAutodetectMappings() &&
        vcsManager.haveDefaultMapping() == null) {
      queue.queue(DisposableUpdate.createDisposable(queue, "initial scan") { autoDetectDefaultRoots() })
    }
  }

  @RequiresBackgroundThread
  private fun autoDetectDefaultRoots() {
    MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.autoDetectDefaultRoots")
    if (vcsManager.haveDefaultMapping() != null) return

    val contentRoots = DefaultVcsRootPolicy.getInstance(project).defaultVcsRoots
    MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.autoDetectDefaultRoots - contentRoots", contentRoots)

    val usedVcses = mutableSetOf<AbstractVcs>()
    val detectedRoots = mutableSetOf<Pair<VirtualFile, AbstractVcs>>()

    contentRoots
      .forEach { root ->
        val foundVcs = vcsManager.findVersioningVcs(root)
        if (foundVcs != null) {
          detectedRoots.add(Pair(root, foundVcs))
          usedVcses.add(foundVcs)
        }
      }
    if (detectedRoots.isEmpty()) return
    MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.autoDetectDefaultRoots - detectedRoots", detectedRoots)

    val commonVcs = usedVcses.singleOrNull()
    if (commonVcs != null) {
      // Remove existing mappings that will duplicate added <Project> mapping.
      val rootPaths = detectedRoots.mapTo(mutableSetOf()) { it.first.path }
      val additionalMappings = vcsManager.directoryMappings.filter { it.vcs != commonVcs.name || it.directory !in rootPaths }
      vcsManager.setAutoDirectoryMappings(additionalMappings + VcsDirectoryMapping.createDefault(commonVcs.name))
    }
    else {
      registerNewDirectMappings(detectedRoots)
    }
  }

  @RequiresBackgroundThread
  private fun autoDetectForContentRoots(contentRoots: List<VirtualFile>) {
    MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.autoDetectForContentRoots - contentRoots", contentRoots)
    if (vcsManager.haveDefaultMapping() != null) return

    val usedVcses = mutableSetOf<AbstractVcs>()
    val detectedRoots = mutableSetOf<Pair<VirtualFile, AbstractVcs>>()

    contentRoots
      .filter { it.isInLocalFileSystem }
      .filter { it.isDirectory }
      .forEach { root ->
        val foundVcs = vcsManager.findVersioningVcs(root)
        if (foundVcs != null && foundVcs !== vcsManager.getVcsFor(root)) {
          detectedRoots.add(Pair(root, foundVcs))
          usedVcses.add(foundVcs)
        }
      }
    if (detectedRoots.isEmpty()) return
    MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.autoDetectForContentRoots - detectedRoots", detectedRoots)

    val commonVcs = usedVcses.singleOrNull()
    if (commonVcs != null && !vcsManager.hasAnyMappings()) {
      vcsManager.setAutoDirectoryMappings(listOf(VcsDirectoryMapping.createDefault(commonVcs.name)))
    }
    else {
      registerNewDirectMappings(detectedRoots)
    }
  }

  private fun registerNewDirectMappings(detectedRoots: Collection<Pair<VirtualFile, AbstractVcs>>) {
    val oldMappings = vcsManager.directoryMappings
    val knownMappedRoots = oldMappings.mapTo(mutableSetOf()) { it.directory }
    val newMappings = detectedRoots.asSequence()
      .map { (root, vcs) -> VcsDirectoryMapping(root.path, vcs.name) }
      .filter { it.directory !in knownMappedRoots }
    vcsManager.setAutoDirectoryMappings(oldMappings + newMappings)
  }

  private inner class MyWorkspaceModelChangeListener : ContentRootChangeListener(skipFileChanges = true) {
    private val dirtyContentRoots = mutableSetOf<VirtualFile>()

    override fun contentRootsChanged(removed: List<VirtualFile>, added: List<VirtualFile>) {
      if (added.isNotEmpty()) {
        MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.contentRootsChanged - roots added", added)
        if (vcsManager.haveDefaultMapping() == null) {
          synchronized(dirtyContentRoots) {
            dirtyContentRoots.addAll(added)
            dirtyContentRoots.removeAll(removed.toSet())
          }
          queue.queue(DisposableUpdate.createDisposable(queue, "modules scan") { runScanForNewContentRoots() })
        }
      }

      if (removed.isNotEmpty()) {
        MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.contentRootsChanged - roots removed", removed)
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

  internal class MyStartUpActivity : VcsStartupActivity {
    init {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override fun runActivity(project: Project) {
      project.service<ModuleVcsDetector>().startDetection()
    }

    override fun getOrder(): Int {
      return VcsInitObject.MAPPINGS.order + 10;
    }
  }
}
