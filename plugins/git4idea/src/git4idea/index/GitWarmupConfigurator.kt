// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index

import com.intellij.ide.CommandLineProgressReporterElement
import com.intellij.ide.warmup.WarmupConfigurator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext

/**
 * Awaits git log indexing process
 */
class GitWarmupConfigurator : WarmupConfigurator {

  override val configuratorPresentableName: String = "git"

  override suspend fun runWarmup(project: Project): Boolean {
    val logger = coroutineContext[CommandLineProgressReporterElement.Key]?.reporter
    if (!Registry.`is`("vcs.log.index.git")) {
      logger?.reportMessage(1,"Indexing of git log is disabled")
      return false
    }

    val vcsProjectLog = VcsProjectLog.getInstance(project)

    val logIndexed = Job()
    project.messageBus.simpleConnect().subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, object : VcsProjectLog.ProjectLogListener {
      override fun logCreated(manager: VcsLogManager) {
        vcsProjectLog.dataManager?.index?.addListener {
          logIndexed.complete()
        } ?: logIndexed.complete()
      }

      override fun logDisposed(manager: VcsLogManager) {
      }
    })
    VcsProjectLog.waitWhenLogIsReady(project)

    val dataManager = vcsProjectLog.dataManager ?: return false
    val isLogEnabled = dataManager.logProviders.any { (_, provider) ->
      VcsLogProperties.SUPPORTS_INDEXING.getOrDefault(provider)
    }
    if (!isLogEnabled) {
      logger?.reportMessage(1, "Git log is not supported for project; exiting")
      return false
    }

    logger?.reportMessage(1, "Starting awaiting git log index")
    logIndexed.join()
    logger?.reportMessage(1, "Git log indexing has finished")

    return false
  }
}