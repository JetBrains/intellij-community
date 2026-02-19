// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.core.CoreBundle
import com.intellij.ide.ApplicationActivity
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.util.SystemProperties.getIntProperty
import com.intellij.util.SystemProperties.getLongProperty
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.toDuration

private val INITIAL_CHECK_DELAY = getLongProperty(
  "intellij.vfs.defragmentation.initial-check-delay-ms",
  10.minutes.inWholeMilliseconds
).toDuration(MILLISECONDS)

private val PERIODIC_CHECK_DELAY = getLongProperty(
  "intellij.vfs.defragmentation.periodic-check-delay-ms",
  6.hours.inWholeMilliseconds
).toDuration(MILLISECONDS)

/** Made configurable mainly for QA: to reduce the threshold to be able to test the functionality in a reasonable time */
private val CHANGES_COUNT_TO_START_ASKING = getIntProperty(
  "intellij.vfs.defragmentation.changes-count-to-start-asking",
  Int.MAX_VALUE / 8
)

internal object VFSDefragmentationCheckerStopper {
  @Volatile
  var stopChecking = false

  fun stopChecking() {
    stopChecking = true
  }
}

/**
 * Checks if VFS is 'wears off' enough, so it may be worth rebuilding it (with indexes)
 *
 * With an introduction of shorter release cycles we keep {system} folder between releases -- which makes VFS & indexes effectively
 * immortal. It is not good for them: both Indexes and VFS tend to accumulate garbage with time -- we don't have a GC for them
 * and don't want spent time developing it. So we need some way to 'reset' VFS+Indexes from time to time -- it is the
 * responsibility of that class.
 */
internal class VFSDefragmentationChecker : ApplicationActivity {
  override suspend fun execute() {
    delay(INITIAL_CHECK_DELAY)

    while (!VFSDefragmentationCheckerStopper.stopChecking) {
      val vfs = FSRecords.getInstance()

      if (isWorthToRebuild(vfs)) {
        //Ask user does it want to rebuild VFS+Indexes:
        val scheduleDefragmentationAction = ActionManager.getInstance().getAction("RequestCachesDefragmentation")
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("IDE Caches")
        notificationGroup.createNotification(
          CoreBundle.message("vfs.defragmentation.notification.title"),
          CoreBundle.message("vfs.defragmentation.notification.text"),
          NotificationType.INFORMATION
        )
          .setImportant(true)
          .addAction(scheduleDefragmentationAction)
          .notify(null)
      }

      delay(PERIODIC_CHECK_DELAY)

    }
  }

  private fun isWorthToRebuild(vfs: FSRecordsImpl): Boolean {
    if (vfs.persistentModCount > CHANGES_COUNT_TO_START_ASKING) {
      return true
    }
    //TODO RC: if vfs.age > 6-12 months?
    //TODO RC: if vfs.filesCount ~= MAX_INT => _force_ defragmentation immediately?
    //TODO RC: if vfs.contentStorage.size ~= max => _force_ defragmentation immediately?
    return false
  }
}
