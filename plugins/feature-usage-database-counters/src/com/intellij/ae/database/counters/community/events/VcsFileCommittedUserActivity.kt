// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community.events

import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.platform.ae.database.activities.WritableDatabaseBackedCounterUserActivity
import com.intellij.platform.ae.database.runUpdateEvent
import java.time.Instant

object VcsFileCommittedUserActivity : WritableDatabaseBackedCounterUserActivity() {
  override val id = "vcs.changes"

  suspend fun getFilesCommitted(from: Instant, until: Instant): Int {
    return getDatabase().getActivitySum(this, from, until)
  }

  suspend fun write(filesChanged: Int) {
    submit(filesChanged)
  }
}

internal class VcsListenerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return object : CheckinHandler() {
      override fun checkinSuccessful() {
        val count = panel.files.size
        FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(VcsFileCommittedUserActivity) {
          it.write(count)
        }
      }
    }
  }
}