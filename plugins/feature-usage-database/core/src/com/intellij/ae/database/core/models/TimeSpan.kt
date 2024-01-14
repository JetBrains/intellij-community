package com.intellij.ae.database.core.models

import com.intellij.ae.database.core.activities.DatabaseBackedTimeSpanUserActivity
import java.time.Instant

data class TimeSpan(
  val activity: DatabaseBackedTimeSpanUserActivity,
  val id: String,
  val start: Instant,
  val end: Instant
)
