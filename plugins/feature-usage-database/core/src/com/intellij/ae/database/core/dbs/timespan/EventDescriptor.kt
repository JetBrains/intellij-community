package com.intellij.ae.database.core.dbs.timespan

import com.intellij.ae.database.core.activities.DatabaseBackedTimeSpanUserActivity
import com.intellij.ae.database.core.activities.toKey
import java.time.Instant

data class EventDescriptor private constructor (
  val activity: DatabaseBackedTimeSpanUserActivity,
  val id: String,
  val canBeStale: Boolean,
  val isPeriodic: Boolean,
  val startedAt: Instant,
  var endAt: Instant? = null,
  var databaseId: Int? = null,
  val extra: Map<String, String>? = null,
) {
  companion object {
    internal fun manual(
      activity: DatabaseBackedTimeSpanUserActivity,
      id: String,
      canBeStale: Boolean,
      startedAt: Instant,
      extra: Map<String, String>?,
    ): EventDescriptor = EventDescriptor(
      activity,
      id,
      canBeStale,
      isPeriodic = false,
      startedAt = startedAt,
      endAt = null,
      databaseId = null,
      extra = extra,
    )

    internal fun periodic(
      activity: DatabaseBackedTimeSpanUserActivity,
      id: String,
      startedAt: Instant,
      endAt: Instant,
      canBeStale: Boolean,
      extra: Map<String, String>?,
    ): EventDescriptor = EventDescriptor(
      activity,
      id,
      canBeStale = canBeStale,
      isPeriodic = true,
      startedAt = startedAt,
      endAt = endAt,
      databaseId = null,
      extra = extra,
    )
  }
}

internal fun EventDescriptor.toKey(): String = this.activity.toKey(id)