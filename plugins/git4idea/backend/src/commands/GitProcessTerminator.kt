// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import java.util.function.LongPredicate

/**
 * Stops the process of a cancelled [GitHandler].
 *
 * The terminator sends a soft kill first. If the process does not exit in time, it sends a hard kill.
 * Under a read lock, a pooled thread waits for the exit. Otherwise, the caller waits for the exit.
 */
internal object GitProcessTerminator {
  /**
   * @param timeoutMs the time to wait for the exit after each kill
   * @param destroy sends the soft kill
   * @param waitFor waits for the exit for the given milliseconds, and returns `true` if the process exited
   * @param destroyForcibly sends the hard kill
   */
  @JvmStatic
  fun terminate(handler: GitHandler, timeoutMs: Long, destroy: Runnable, waitFor: LongPredicate, destroyForcibly: Runnable) {
    ProgressManager.getInstance().executeNonCancelableSection(destroy)

    val waitAndHardKill = Runnable { waitAndHardKill(handler, timeoutMs, waitFor, destroyForcibly) }
    if (ApplicationManager.getApplication().isReadAccessAllowed || GitHandler.shouldSuppressReadLocks()) {
      // Some Git operations are called while holding the global locks.
      // Ex: access to the 'GitIndexVirtualFile' or 'GitDirectoryVirtualFile'.
      // In this case, we should not delay the current thread cancellation.
      ApplicationManager.getApplication().executeOnPooledThread(waitAndHardKill)
    }
    else {
      ProgressManager.getInstance().executeNonCancelableSection(waitAndHardKill)
    }
  }

  private fun waitAndHardKill(handler: GitHandler, timeoutMs: Long, waitFor: LongPredicate, destroyForcibly: Runnable) {
    // The signal was sent, but we still need to wait for the process to finish its dark deeds
    if (waitFor.test(timeoutMs)) {
      return
    }

    GitHandler.LOG.warn("Soft-kill failed for [${handler.printableCommandLine()}].")

    destroyForcibly.run()
    if (waitFor.test(timeoutMs)) {
      return
    }

    GitHandler.LOG.warn("Could not terminate [${handler.printableCommandLine()}].")
  }
}
