// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.diagnostic.runActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx.MAPPING_DETECTION_LOG
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.vcsUtil.VcsUtil

internal class ModuleVcsDetector(private val project: Project) {
  private val vcsManager by lazy(LazyThreadSafetyMode.NONE) { ProjectLevelVcsManagerImpl.getInstanceImpl(project) }

  private val queue = MergingUpdateQueue("ModuleVcsDetector", 1000, true, null, project, null, Alarm.ThreadToUse.POOLED_THREAD).also {
    it.setRestartTimerOnAdd(true)
  }

  private val dirtyContentRoots = mutableSetOf<VirtualFile>()

  private fun startDetection() {
    MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.startDetection")

    if (vcsManager.needAutodetectMappings() &&
        vcsManager.haveDefaultMapping() == null &&
        VcsUtil.shouldDetectVcsMappingsFor(project)) {
      queue.queue(DisposableUpdate.createDisposable(queue, "initial scan") {
        runActivity("ModuleVcsDetector.autoDetectDefaultRoots") {
          autoDetectDefaultRoots()
        }
      })
    }
  }

  @RequiresBackgroundThread
  private fun autoDetectDefaultRoots() {
    val contentRoots = DefaultVcsRootPolicy.getInstance(project).defaultVcsRoots
    MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.autoDetectDefaultRoots - contentRoots", contentRoots)
    autoDetectForContentRoots(contentRoots, true)
  }

  @RequiresBackgroundThread
  private fun autoDetectForContentRoots(contentRoots: Collection<VirtualFile>, isInitialDetection: Boolean = false) {
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

    val directMappings = detectedRoots.map { it.first }.toMutableSet()
    for (rootChecker in VcsRootChecker.EXTENSION_POINT_NAME.extensionList) {
      val vcs = vcsManager.findVcsByName(rootChecker.supportedVcs.name) ?: continue
      val detectedMappings = try {
        rootChecker.detectProjectMappings(project, contentRoots, directMappings) ?: continue
      }
      catch (e: VcsException) {
        MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.autoDetectForContentRoots - exception while detecting mapping", e)
        continue
      }
      if (detectedMappings.isEmpty()) continue

      usedVcses.add(vcs)
      for (file in detectedMappings) {
        detectedRoots.add(Pair(file, vcs))
      }
    }

    if (detectedRoots.isEmpty()) return
    MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.autoDetectForContentRoots - detectedRoots", detectedRoots)

    val commonVcs = usedVcses.singleOrNull()
    if (commonVcs != null) {
      if (isInitialDetection) {
        // Register <Project> mapping along with already existing direct mappings
        vcsManager.setAutoDirectoryMappings(vcsManager.directoryMappings + VcsDirectoryMapping.createDefault(commonVcs.name))
        return
      }

      if (!vcsManager.hasAnyMappings()) {
        vcsManager.setAutoDirectoryMappings(listOf(VcsDirectoryMapping.createDefault(commonVcs.name)))
        return
      }
    }

    vcsManager.registerNewDirectMappings(detectedRoots)
  }

  fun scheduleScanForNewContentRoots(removed: Collection<VirtualFile>, added: Collection<VirtualFile>) {
    if (!VcsUtil.shouldDetectVcsMappingsFor(project)) return
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

  internal class MyStartUpActivity : VcsStartupActivity {
    init {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override val order: Int
      get() = VcsInitObject.MAPPINGS.order + 10

    override suspend fun execute(project: Project) {
      project.serviceAsync<ModuleVcsDetector>().startDetection()
    }
  }
}
