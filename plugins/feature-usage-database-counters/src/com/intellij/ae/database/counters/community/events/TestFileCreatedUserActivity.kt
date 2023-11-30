// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community.events

import com.intellij.ae.database.activities.WritableDatabaseBackedCounterUserActivity
import com.intellij.ae.database.runUpdateEvent
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

object TestFileCreatedUserActivity : WritableDatabaseBackedCounterUserActivity() {
  override val id = "test.file.created"

  internal suspend fun write() {
    submit(1)
  }
}

internal class TestFileCreationListener : AsyncFileListener {
  override fun prepareChange(events: MutableList<out VFileEvent>): ChangeApplier {
    val creationEvents = events.filterIsInstance<VFileCreateEvent>()

    return object : ChangeApplier {
      override fun afterVfsChange() {
        for (event in creationEvents) {
          val file = event.file ?: return
          val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return
          val isTest = TestSourcesFilter.isTestSources(file, project)
          if (isTest) {
            FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(TestFileCreatedUserActivity) {
              it.write()
            }
          }
        }
      }
    }
  }
}