// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.logs.DebugLogLevel
import com.intellij.diagnostic.logs.LogCategory
import com.intellij.diagnostic.logs.LogLevelConfigurationManager
import com.intellij.testFramework.ApplicationRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LogsPersStateManagerTest {
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

    LogLevelConfigurationManager.getInstance().addCategories(listOf(
      LogCategory("###$randomPackageName1", DebugLogLevel.TRACE),
      LogCategory(randomPackageName2, DebugLogLevel.TRACE)))

    val filteredCategories = listOf(LogCategory(randomPackageName1, DebugLogLevel.TRACE),
                                    LogCategory(randomPackageName2, DebugLogLevel.TRACE))

    for (category in filteredCategories) {
      assertEquals(Level.FINER, Logger.getLogger(category.category).level)
    }
  }

  @Test
  fun addedCategoriesAreNotDoubled_IJPL_148678() {
    val randomPackageName1 = "package1.abc"
    val randomPackageName2 = "package2.cde"

    LogLevelConfigurationManager.getInstance().addCategories(
      listOf(LogCategory("#$randomPackageName1", DebugLogLevel.TRACE),
             LogCategory(randomPackageName2, DebugLogLevel.TRACE))
    )
    assertThat(LogLevelConfigurationManager.getInstance().getCategories())
      .describedAs("Both category were added")
      .hasSize(2)

    LogLevelConfigurationManager.getInstance().addCategories(listOf(LogCategory("#$randomPackageName1", DebugLogLevel.TRACE)))
    assertThat(LogLevelConfigurationManager.getInstance().getCategories())
      .describedAs("After we added existing category with '#', number of categories stayed the same")
      .hasSize(2)

    LogLevelConfigurationManager.getInstance().addCategories(listOf(LogCategory(randomPackageName2, DebugLogLevel.TRACE)))
    assertThat(LogLevelConfigurationManager.getInstance().getCategories())
      .describedAs("After we added existing category without '#', number of categories stayed the same")
      .hasSize(2)

    val randomPackageName3 = "package3.xzf"
    val logCategory3Debug = LogCategory(randomPackageName3, DebugLogLevel.DEBUG)
    LogLevelConfigurationManager.getInstance().addCategories(listOf(logCategory3Debug))
    assertThat(LogLevelConfigurationManager.getInstance().getCategories())
      .describedAs("After we added new category without '#', number of categories incremented")
      .hasSize(3)
      .contains(logCategory3Debug)

    val logCategory3Trace = LogCategory(randomPackageName3, DebugLogLevel.TRACE)
    LogLevelConfigurationManager.getInstance().addCategories(listOf(logCategory3Trace))

    assertThat(LogLevelConfigurationManager.getInstance().getCategories())
      .describedAs("After we added existing category with different level, number of categories stayed the same")
      .hasSize(3)
      .contains(logCategory3Trace)
      .describedAs("After we added existing category with different level, the category with the higher level stayed")
      .doesNotContain(logCategory3Debug)
  }
}