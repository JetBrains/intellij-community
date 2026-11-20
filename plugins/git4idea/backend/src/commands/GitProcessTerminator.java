// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;

import java.util.function.LongPredicate;

/**
 * Stops the process of a cancelled {@link GitHandler}.
 * <p>
 * The terminator sends a soft kill first. If the process does not exit in time, it sends a hard kill.
 * Under a read lock, a pooled thread waits for the exit. Otherwise, the caller waits for the exit.
 */
final class GitProcessTerminator {
  private GitProcessTerminator() {
  }

  /**
   * @param timeoutMs       the time to wait for the exit after each kill
   * @param destroy         sends the soft kill
   * @param waitFor         waits for the exit for the given milliseconds, and returns {@code true} if the process exited
   * @param destroyForcibly sends the hard kill
   */
  static void terminate(@NotNull GitHandler handler,
                        long timeoutMs,
                        @NotNull Runnable destroy,
                        @NotNull LongPredicate waitFor,
                        @NotNull Runnable destroyForcibly) {
    ProgressManager.getInstance().executeNonCancelableSection(destroy);

    Runnable waitAndHardKill = () -> waitAndHardKill(handler, timeoutMs, waitFor, destroyForcibly);
    if (ApplicationManager.getApplication().isReadAccessAllowed() ||
        GitHandler.shouldSuppressReadLocks()) {
      // Some Git operations are called while holding the global locks.
      // Ex: access to the 'GitIndexVirtualFile' or 'GitDirectoryVirtualFile'.
      // In this case, we should not delay the current thread cancellation.
      ApplicationManager.getApplication().executeOnPooledThread(waitAndHardKill);
    }
    else {
      ProgressManager.getInstance().executeNonCancelableSection(waitAndHardKill);
    }
  }

  private static void waitAndHardKill(@NotNull GitHandler handler,
                                      long timeoutMs,
                                      @NotNull LongPredicate waitFor,
                                      @NotNull Runnable destroyForcibly) {
    // The signal was sent, but we still need to wait for the process to finish its dark deeds
    if (waitFor.test(timeoutMs)) {
      return;
    }

    GitHandler.LOG.warn("Soft-kill failed for [" + handler.printableCommandLine() + "].");

    destroyForcibly.run();
    if (waitFor.test(timeoutMs)) {
      return;
    }

    GitHandler.LOG.warn("Could not terminate [" + handler.printableCommandLine() + "].");
  }
}
