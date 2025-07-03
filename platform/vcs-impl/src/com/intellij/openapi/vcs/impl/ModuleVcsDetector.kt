// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx.MAPPING_DETECTION_LOG
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting

private const val INITIAL_DETECTION_KEY = "ModuleVcsDetector.initialDetectionPerformed"

@Internal
@Service(Service.Level.PROJECT)
class ModuleVcsDetector(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val queue = MergingUpdateQueue(
    name = "ModuleVcsDetector",
    mergingTimeSpan = 1000,
    isActive = true,
    modalityStateComponent = null,
    parent = null,
    activationComponent = null,
    thread = Alarm.ThreadToUse.POOLED_THREAD,
    coroutineScope = coroutineScope,
  ).also {
    it.setRestartTimerOnAdd(true)
  }

  private val initialDetectionDone = CompletableDeferred<Unit>(parent = coroutineScope.coroutineContext[Job])

  private val dirtyContentRoots = LinkedHashSet<VirtualFile>()

  private suspend fun getVcsManager(): ProjectLevelVcsManagerImpl {
    return project.serviceAsync<ProjectLevelVcsManager>() as ProjectLevelVcsManagerImpl
  }

  /**
   * Returns 'true' during initial project setup, i.e.:
   * * Project was not reopened a second time ([INITIAL_DETECTION_KEY])
   * * There are detectors that should be run each time the project is opened
   * * There are no configured mappings
   */
  @VisibleForTesting
  fun needInitialDetection(props: PropertiesComponent, vcsManager: ProjectLevelVcsManager): Boolean {
    return (!props.getBoolean(INITIAL_DETECTION_KEY) || VcsRootChecker.EXTENSION_POINT_NAME.extensionList.any { it.shouldAlwaysRunInitialDetection() })
           && !vcsManager.hasAnyMappings() && VcsUtil.shouldDetectVcsMappingsFor(project)
  }

  suspend fun awaitInitialDetection() {
    val props = project.serviceAsync<PropertiesComponent>()
    val vcsManager = getVcsManager()
    val willRun = needInitialDetection(props, vcsManager)
    if (!willRun) return
    initialDetectionDone.await()
  }

  private suspend fun startInitialDetection() {
    MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.startDetection")

    val vcsManager = getVcsManager()
    val props = project.serviceAsync<PropertiesComponent>()
    if (needInitialDetection(props, vcsManager)) {
      queue.queue(object : Update("initial scan") {
        override fun run() = throw UnsupportedOperationException("Sync execution is not supported")

        override suspend fun execute() {
          val contentRoots = project.serviceAsync<DefaultVcsRootPolicy>().defaultVcsRoots
          MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.autoDetectDefaultRoots - contentRoots", contentRoots)
          val rootCheckers = if (props.getBoolean(INITIAL_DETECTION_KEY)) {
            VcsRootChecker.EXTENSION_POINT_NAME.extensionList.filter { it.shouldAlwaysRunInitialDetection() }
          }
          else {
            VcsRootChecker.EXTENSION_POINT_NAME.extensionList
          }

          autoDetectForContentRoots(contentRoots = contentRoots, isInitialDetection = true, vcsManager = vcsManager, rootCheckers = rootCheckers)
          props.updateValue(INITIAL_DETECTION_KEY, true)
          initialDetectionDone.complete(Unit)
        }
      })
    }
  }

  private fun autoDetectForContentRoots(
    contentRoots: Collection<VirtualFile>,
    isInitialDetection: Boolean = false,
    vcsManager: ProjectLevelVcsManagerImpl,
    rootCheckers: List<VcsRootChecker> = VcsRootChecker.EXTENSION_POINT_NAME.extensionList,
  ) {
    if (rootCheckers.isEmpty()) {
      return
    }
    MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.autoDetectForContentRoots - contentRoots", contentRoots)
    if (vcsManager.haveDefaultMapping() != null) {
      return
    }

    val usedVcses = HashSet<AbstractVcs>()
    val detectedRoots = LinkedHashSet<Pair<VirtualFile, AbstractVcs>>()

    contentRoots
      .asSequence()
      .filter { it.isInLocalFileSystem && it.isDirectory }
      .forEach { root ->
        val foundVcs = vcsManager.findVersioningVcs(root)
        if (foundVcs != null && foundVcs !== vcsManager.getVcsFor(root)) {
          detectedRoots.add(Pair(root, foundVcs))
          usedVcses.add(foundVcs)
        }
      }

    val directMappings = detectedRoots.mapTo(HashSet(detectedRoots.size)) { it.first }
    for (rootChecker in rootCheckers) {
      val vcs = vcsManager.findVcsByName(rootChecker.supportedVcs.name) ?: continue
      val detectedMappings = try {
        rootChecker.detectProjectMappings(project, contentRoots, directMappings) ?: continue
      }
      catch (e: VcsException) {
        MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.autoDetectForContentRoots - exception while detecting mapping", e)
        continue
      }

      if (detectedMappings.isEmpty()) {
        continue
      }

      usedVcses.add(vcs)
      for (file in detectedMappings) {
        detectedRoots.add(Pair(file, vcs))
      }
    }

    if (detectedRoots.isEmpty()) {
      return
    }

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
    if (!VcsUtil.shouldDetectVcsMappingsFor(project)) {
      return
    }

    if (added.isNotEmpty()) {
      MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.contentRootsChanged - roots added", added)
      if (ProjectLevelVcsManagerImpl.getInstanceImpl(project).haveDefaultMapping() == null) {
        synchronized(dirtyContentRoots) {
          dirtyContentRoots.addAll(added)
          dirtyContentRoots.removeAll(removed.toSet())
        }
        queue.queue(object : Update("modules scan") {
          override fun run() = throw UnsupportedOperationException("Sync execution is not supported")

          override suspend fun execute() {
            runScanForNewContentRoots()
          }
        })
      }
    }

    if (removed.isNotEmpty()) {
      MAPPING_DETECTION_LOG.debug("ModuleVcsDetector.contentRootsChanged - roots removed", removed)
      val remotedPaths = removed.mapTo(HashSet()) { it.path }
      val vcsManager = ProjectLevelVcsManagerImpl.getInstanceImpl(project)
      val removedMappings = vcsManager.directoryMappings.filter { it.directory in remotedPaths }
      removedMappings.forEach { mapping -> vcsManager.removeDirectoryMapping(mapping) }
    }
  }

  private suspend fun runScanForNewContentRoots() {
    val contentRoots: List<VirtualFile>
    synchronized(dirtyContentRoots) {
      contentRoots = dirtyContentRoots.toList()
      dirtyContentRoots.clear()
    }

    autoDetectForContentRoots(contentRoots = contentRoots, vcsManager = getVcsManager())
  }

  internal class ModuleVcsDetectorStartUpActivity : VcsStartupActivity {
    init {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override val order: Int
      get() = VcsInitObject.MAPPINGS.order + 10

    override suspend fun execute(project: Project) {
      project.serviceAsync<ModuleVcsDetector>().startInitialDetection()
    }
  }
}
