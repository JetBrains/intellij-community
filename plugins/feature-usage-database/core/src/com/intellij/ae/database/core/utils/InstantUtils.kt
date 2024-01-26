package com.intellij.ae.database.core.utils

import java.time.Instant
import java.time.temporal.ChronoUnit

object InstantUtils {
  val SomeTimeAgo: Instant = Instant.ofEpochSecond(0)
  val Now: Instant get() = Instant.now()
  val NowButABitLater: Instant get() = Now.plusSeconds(20 * 60)
  val StartOfDay: Instant get() = Now.truncatedTo(ChronoUnit.DAYS)
  val WeekAgo: Instant get() = Now.minus(7, ChronoUnit.DAYS)
  val WeekAgoStartOfDay: Instant get() = WeekAgo.truncatedTo(ChronoUnit.DAYS)

  fun formatForDatabase(instant: Instant): String = instant.truncatedTo(ChronoUnit.SECONDS).toString()

  fun fromString(str: String): Instant = Instant.parse(str)
}