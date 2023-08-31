// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.ide.CommandLineProgressReporterElement
import com.intellij.ide.warmup.WarmupConfigurator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.blockingContextScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties
import com.intellij.vcs.log.impl.VcsLogSharedSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Awaits git log indexing process
 */
class GitWarmupConfigurator : WarmupConfigurator {

  override val configuratorPresentableName: String = "git"

  override suspend fun runWarmup(project: Project): Boolean {
    val logger = coroutineContext[CommandLineProgressReporterElement.Key]?.reporter
    if (!VcsLogData.isIndexSwitchedOnInRegistry() || !VcsLogSharedSettings.isIndexSwitchedOn(project)) {
      logger?.reportMessage(1, "Indexing of git log is disabled")
      return false
    }

    val projectLevelVcsManager = project.serviceAsync<ProjectLevelVcsManager>()
    val logProviders = VcsLogManager.findLogProviders(projectLevelVcsManager.allVcsRoots.toList(), project)
    if (logProviders.isEmpty()) {
      logger?.reportMessage(1, "No git roots to index")
      return false
    }

    val manager = VcsLogManager(project, project.serviceAsync<VcsLogProjectTabsProperties>(), logProviders, false) { _, throwable ->
      logger?.reportMessage(1, throwable.stackTraceToString())
    }
    blockingContextScope {
      manager.scheduleInitialization()
    }

    assertVcsIndexed(manager)

    withContext(Dispatchers.EDT) {
      blockingContextScope {
        manager.dispose(null)
      }
    }

    logger?.reportMessage(1, "Git log indexing has finished")
    return false
  }

  private suspend fun assertVcsIndexed(manager: VcsLogManager) {
    withContext(Dispatchers.EDT) {
      assert(manager.isLogUpToDate)
    }
    val index = manager.dataManager.index
    assert(index.indexingRoots.all { !index.isIndexingEnabled(it) || index.isIndexed(it) })
  }
}