// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.ide.startup.importSettings.data.ActionsDataProvider.Companion.toRelativeFormat
import com.intellij.idea.TestFor
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ActionsDataProviderTest {

  @Test
  @TestFor(issues = ["IDEA-342146"])
  fun checkWrongFormats() {
    for (i in 0L..1000L) {
      val format = LocalDate.now().minusDays(i).toRelativeFormat("used")
      assertFalse(format.contains(" 0"), "Unexpected format: $format for $i days")
      assertFalse(format.contains("13 months"), "Unexpected format: $format for $i days")
      assertFalse(format.contains("5 weeks"), "Unexpected format: $format for $i days")
      assertFalse(format.contains("8 days"), "Unexpected format: $format for $i days")
    }
  }

  @Test
  fun checkRightFormats() {
    checkFormat(0, "today")
    checkFormat(1, "yesterday")
    checkFormat(6, "6 days")
    checkFormat(7, "a week")
    checkFormat(22, "3 weeks")
    checkFormat(30, "a month")
    checkFormat(35, "a month")
    checkFormat(190, "6 months")
    checkFormat(365, "a year")
    checkFormat(750, "2 years")
  }

  private fun checkFormat(daysAgo: Int, text2contain: String) {
    assertTrue(LocalDate.now()
                 .minusDays(daysAgo.toLong())
                 .toRelativeFormat("used")
                 .contains(text2contain))
  }
}