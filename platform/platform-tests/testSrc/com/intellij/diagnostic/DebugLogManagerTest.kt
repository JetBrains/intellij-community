// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DebugLogManagerTest {
  @get:Rule
  val applicationRule: ApplicationRule = ApplicationRule()

  @Test
  fun allCategoriesApplied_IDEA_297747() {
    val randomPackageName1 = "package1.ZzzHPTKMIzEBwvBCJpb"
    val randomPackageName2 = "package2.YcwuHPTKMIzEBwvBCJpb"

    val affectedLogCategories = listOf(
      "###$randomPackageName1", randomPackageName1, "#$randomPackageName1",
      randomPackageName2, "#$randomPackageName2",
    )

    for (category in affectedLogCategories) {
      assertNotEquals(Level.FINER, Logger.getLogger(category).level)
    }

    DebugLogManager.getInstance().applyCategories(listOf(
      DebugLogManager.Category("###$randomPackageName1", DebugLogManager.DebugLogLevel.TRACE),
      DebugLogManager.Category(randomPackageName2, DebugLogManager.DebugLogLevel.TRACE)))

    for (category in affectedLogCategories) {
      assertEquals(Level.FINER, Logger.getLogger(category).level)
    }
  }
}