package com.intellij.ae.database.counters.community.events

import com.intellij.ae.database.activities.WritableDatabaseBackedTimeSpanUserActivity
import com.intellij.ae.database.dbs.timespan.TimeSpanUserActivityDatabaseManualKind
import com.intellij.ae.database.runUpdateEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.startup.ProjectActivity

object ProjectOpenedTimeSpanUserActivity : WritableDatabaseBackedTimeSpanUserActivity() {
  override val canBeStale: Boolean
    get() = true
  override val id: String
    get() = "project.opened"

  suspend fun projectOpened(id: String, name: String) {
    submitManual(id, TimeSpanUserActivityDatabaseManualKind.Start, mapOf("name" to name))
  }

  suspend fun projectClosed(id: String, name: String) {
    submitManual(id, TimeSpanUserActivityDatabaseManualKind.End, mapOf("name" to name))
  }
}

internal class ProjectOpenedTimeSpanUserActivityProjectOpenListener : ProjectActivity {
  override suspend fun execute(project: Project) {
    FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(ProjectOpenedTimeSpanUserActivity) {
      it.projectOpened(project.locationHash, project.name)
    }
  }
}

internal class ProjectOpenedTimeSpanUserActivityProjectCloseListener : ProjectCloseListener {
  override fun projectClosing(project: Project) {
    FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(ProjectOpenedTimeSpanUserActivity) {
      it.projectClosed(project.locationHash, project.name)
    }
  }
}