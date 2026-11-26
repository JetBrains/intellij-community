// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.ui.VcsLogNotificationIdsHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Shows the commit of a hash in a text, such as a link in the terminal output or in a Markdown file.
 *
 * A lookup has one of three outcomes. A match shows the commit. No match shows the error "not found". A match that the
 * log cannot show shows the error "not shown". The lookup runs with a background progress, and the navigator keeps one
 * lookup per hash.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class VcsCommitHashNavigator(private val project: Project, private val scope: CoroutineScope) {
  /** The running lookups by hash. */
  private val lookups = ConcurrentHashMap<String, Job>()

  /**
   * Shows the commit of the hexadecimal [hash] from a text near [context], see [findCommitRoots]. The [hash] can be a
   * prefix. The lookup runs with a background progress, and the call does not wait for it. A second call for the same
   * [hash] during the lookup does nothing. Any thread can call it.
   */
  fun showCommit(hash: String, context: FilePath?) {
    // `computeIfAbsent` holds the lock of the entry until it stores the job. So a lookup that ends at once still removes its job.
    lookups.computeIfAbsent(hash) {
      scope.launch {
        try {
          val outcome = withBackgroundProgress(project, VcsLogBundle.message("vcs.log.show.commit.in.log.process", hash)) {
            lookup(hash, context)
          }
          report(outcome, hash)
        }
        finally {
          lookups.remove(hash, coroutineContext.job)
        }
      }
    }
  }

  /**
   * Shows the commit in the log of the first item of [findCommitRoots] that holds it and can show it, see
   * [showCommitWithPrefix]. After it shows the commit in another project, it brings the window of that project to the front.
   */
  private suspend fun lookup(hash: String, context: FilePath?): LookupOutcome {
    var notShown = false
    for (commitRoots in findCommitRoots(project, context)) {
      val log = commitRoots.project.serviceAsync<VcsProjectLog>()
      when (log.showCommitWithPrefix(commitRoots.roots, hash)) {
        VcsLogShowCommitResult.SHOWN -> Unit
        VcsLogShowCommitResult.NOT_FOUND -> continue
        VcsLogShowCommitResult.NOT_SHOWN -> {
          notShown = true
          continue
        }
      }
      if (commitRoots.project != project) {
        withContext(Dispatchers.EDT) { ProjectUtil.focusProjectWindow(commitRoots.project, true) }
      }
      return LookupOutcome.Shown
    }
    return if (notShown) LookupOutcome.NotShown else LookupOutcome.NotFound
  }

  private fun report(outcome: LookupOutcome, hash: String) {
    when (outcome) {
      LookupOutcome.Shown -> Unit
      LookupOutcome.NotFound -> {
        LOG.info("The VCS Log does not hold the commit $hash")
        VcsNotifier.getInstance(project).notifyError(
          VcsLogNotificationIdsHolder.COMMIT_HASH_NOT_FOUND,
          VcsLogBundle.message("vcs.log.commit.hash.not.shown.title"),
          VcsLogBundle.message("vcs.log.commit.hash.not.found.message", hash),
        )
      }
      LookupOutcome.NotShown -> {
        LOG.info("The VCS Log holds the commit $hash but cannot show it")
        VcsNotifier.getInstance(project).notifyError(
          VcsLogNotificationIdsHolder.COMMIT_HASH_NOT_SHOWN,
          VcsLogBundle.message("vcs.log.commit.hash.not.shown.title"),
          VcsLogBundle.message("vcs.log.commit.hash.not.shown.message", hash),
        )
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): VcsCommitHashNavigator = project.service()
  }
}

/** The result of a lookup of a commit. */
private sealed interface LookupOutcome {
  object Shown : LookupOutcome
  object NotFound : LookupOutcome
  object NotShown : LookupOutcome
}

private val LOG = logger<VcsCommitHashNavigator>()
