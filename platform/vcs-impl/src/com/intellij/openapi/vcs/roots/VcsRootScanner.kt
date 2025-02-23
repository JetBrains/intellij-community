// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.roots

import com.intellij.ide.impl.isTrusted
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vcs.impl.VcsEP
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.openapi.vcs.impl.VcsStartupActivity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<VcsRootScanner>()

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class VcsRootScanner(private val project: Project, coroutineScope: CoroutineScope) {
  private val rootProblemNotifier = VcsRootProblemNotifier.createInstance(project)

  private val scanRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    AsyncVfsEventsPostProcessor.getInstance().addListener(object : AsyncVfsEventsListener {
      override suspend fun filesChanged(events: List<VFileEvent>) {
        this@VcsRootScanner.filesChanged(events)
      }
    }, coroutineScope)
    VcsRootChecker.EXTENSION_POINT_NAME.addChangeListener(coroutineScope, ::scheduleScan)
    VcsEP.EP_NAME.addChangeListener(coroutineScope, ::scheduleScan)

    coroutineScope.launch {
      @OptIn(FlowPreview::class)
      scanRequests
        .debounce(1.seconds)
        .collectLatest {
          withContext(Dispatchers.IO) {
            project.service<InitialVfsRefreshService>().awaitInitialVfsRefreshFinished()

            coroutineToIndicator {
              rootProblemNotifier.rescanAndNotifyIfNeeded()
            }
          }
        }
    }
  }

  companion object {
    fun getInstance(project: Project): VcsRootScanner = project.service<VcsRootScanner>()

    @JvmStatic
    fun visitDirsRecursivelyWithoutExcluded(project: Project,
                                            root: VirtualFile,
                                            visitIgnoredFoldersThemselves: Boolean,
                                            processor: (VirtualFile) -> VirtualFileVisitor.Result) {
      val fileIndex = ProjectRootManager.getInstance(project).fileIndex
      val depthLimit = VirtualFileVisitor.limit(Registry.intValue("vcs.root.detector.folder.depth"))
      val ignorePattern = parseDirIgnorePattern()
      if (isUnderIgnoredDirectory(project, ignorePattern, if (visitIgnoredFoldersThemselves) root.parent else root)) {
        return
      }

      VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit?>(NO_FOLLOW_SYMLINKS, depthLimit) {
        override fun visitFileEx(file: VirtualFile): Result {
          if (!file.isDirectory) {
            return CONTINUE
          }

          if (visitIgnoredFoldersThemselves) {
            val apply = processor(file)
            if (apply != CONTINUE) {
              return apply
            }
          }

          if (isIgnoredDirectory(project, ignorePattern, file)) {
            return SKIP_CHILDREN
          }

          if (ReadAction.compute<Boolean, RuntimeException> { project.isDisposed || !fileIndex.isInContent(file) }) {
            return SKIP_CHILDREN
          }

          if (!visitIgnoredFoldersThemselves) {
            val apply = processor(file)
            if (apply != CONTINUE) {
              return apply
            }
          }
          return CONTINUE
        }
      })
    }

    private fun isVcsDir(checkers: List<VcsRootChecker>, filePath: String): Boolean {
      return checkers.any { it.isVcsDir(filePath) }
    }

    @JvmStatic
    fun isUnderIgnoredDirectory(project: Project, ignorePattern: Pattern?, dir: VirtualFile?): Boolean {
      var parent = dir
      while (parent != null) {
        if (isIgnoredDirectory(project, ignorePattern, parent)) {
          return true
        }
        parent = parent.parent
      }
      return false
    }

    @JvmStatic
    fun parseDirIgnorePattern(): Pattern? {
      try {
        return Pattern.compile(Registry.stringValue("vcs.root.detector.ignore.pattern"))
      }
      catch (e: MissingResourceException) {
        LOG.warn(e)
        return null
      }
      catch (e: PatternSyntaxException) {
        LOG.warn(e)
        return null
      }
    }
  }

  private suspend fun filesChanged(events: List<VFileEvent>) {
    val checkers = VcsRootChecker.EXTENSION_POINT_NAME.extensionList
    if (checkers.isEmpty()) {
      return
    }

    for (event in events) {
      val file = event.file
      if (file != null && file.isDirectory) {
        coroutineContext.ensureActive()
        visitDirsRecursivelyWithoutExcluded(project = project, root = file, visitIgnoredFoldersThemselves = true) { dir ->
          if (isVcsDir(checkers, dir.name)) {
            scheduleScan()
            return@visitDirsRecursivelyWithoutExcluded VirtualFileVisitor.skipTo(file)
          }
          VirtualFileVisitor.CONTINUE
        }
      }
    }
  }

  fun scheduleScan() {
    if (VcsRootChecker.EXTENSION_POINT_NAME.extensionList.isEmpty()) {
      return
    }

    ProjectLevelVcsManagerEx.MAPPING_DETECTION_LOG.debug("VcsRootScanner.scheduleScan")
    if (!VcsUtil.shouldDetectVcsMappingsFor(project)) {
      return
    }

    check(scanRequests.tryEmit(Unit))
  }

  internal class DetectRootsStartupActivity : VcsStartupActivity {
    override val order: Int
      get() = VcsInitObject.AFTER_COMMON.order

    override suspend fun execute(project: Project) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return
      }
      if (!project.isTrusted()) {
        // vcs is disabled
        return
      }

      ProjectLevelVcsManagerEx.MAPPING_DETECTION_LOG.debug("VcsRootScanner.start activity")
      project.serviceAsync<VcsRootScanner>().scheduleScan()
    }
  }

  internal class TrustListener : TrustedProjectsListener {
    override fun onProjectTrusted(project: Project) {
      ProjectLevelVcsManager.getInstance(project).runAfterInitialization { getInstance(project).scheduleScan() }
    }
  }
}

private fun isIgnoredDirectory(project: Project, ignorePattern: Pattern?, dir: VirtualFile): Boolean {
  if (ProjectLevelVcsManager.getInstance(project).isIgnored(dir)) {
    LOG.debug { "Skipping ignored dir: $dir" }
    return true
  }

  if (ignorePattern != null && ignorePattern.matcher(dir.name).matches()) {
    LOG.debug { "Skipping dir by pattern: $dir" }
    return true
  }
  return false
}
