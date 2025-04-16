// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.CommonBundle
import com.intellij.openapi.application.impl.getGlobalThreadingSupport
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus

/**
 * [PotemkinProgress] done right.
 *
 * Imagine the following scenario:
 * ```kotlin
 * // bgt
 * writeAction { Thread.sleep(100000) } // some intensive work in background
 *
 * // edt
 * ReadAction.run {} // blocked until write lock is released
 * ```
 *
 * In this situation, we have a freeze, because EDT is blocked on a lock in a single event.
 * Instead of blocking, we can show a "modal" progress bar, and provide an impression that IDE is not dead.
 *
 * This progress bar starts when EDT is going to be blocked on the RWI lock, and finished when the required lock gets acquired.
 */
@ApiStatus.Internal
object SuvorovProgress {

  @JvmStatic
  fun dispatchEventsUntilConditionCompletes(shouldTerminate: () -> Boolean) {
    // some focus machinery may require Write-Intent read action
    // we need to remove it from there
    getGlobalThreadingSupport().relaxPreventiveLockingActions {
      // Unfortunately, we still have to use PotemkinProgress.
      // At this point, we have too many events that acquire the Write-Intent read lock,
      // so we need to have a strict control over events that are executing on EDT to avoid stack overflow of SuvorovProgresses
      val potemkinProgress = PotemkinProgress(CommonBundle.message("title.long.non.interactive.progress"), null, null, null)
      potemkinProgress.setAllowThreadDumpButton()
      potemkinProgress.start()
      try {
        do {
          potemkinProgress.interact()
          Thread.sleep(10); // avoid touching the progress too much
        } while (!shouldTerminate())
      }
      finally {
        // we cannot acquire WI on closing
        potemkinProgress.dialog.getPopup()?.setShouldDisposeInWriteIntentReadAction(false)
        potemkinProgress.progressFinished()
        potemkinProgress.processFinish()
        Disposer.dispose(potemkinProgress)
      }
    }
  }
}
