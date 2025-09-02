// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.logs.DebugLogLevel
import com.intellij.diagnostic.logs.LogCategory
import com.intellij.diagnostic.logs.LogLevelConfigurationManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.awaitLogQueueProcessed
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import java.io.BufferedReader
import java.nio.file.Files
import java.util.concurrent.ThreadLocalRandom
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LogsPersStateManagerTest {
  @get:Rule
  val applicationRule: ApplicationRule = ApplicationRule()

  @BeforeEach
  fun resetLogCategories(@TestDisposable disposable: Disposable) {
    val instance = LogLevelConfigurationManager.getInstance()
    val oldState = instance.state
    Disposer.register(disposable) {
      instance.loadState(oldState)
    }
  }

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

  @Test
  fun `dedicated log file`() {
    fun readSomeLastLines(logName: String): String {
      val path = PathManager.getSystemDir().resolve("testlog").resolve(logName)
      return try {
        val size = Files.size(path)
        Files.newInputStream(path).use { inputStream ->
          if (size < 8096) {
            inputStream.bufferedReader().readText()
          }
          else {
            inputStream.skip((size - 8096).coerceAtLeast(0))
            BufferedReader(inputStream.reader()).lineSequence().drop(1).joinToString("\n")
          }
        }
      }
      catch (_: java.nio.file.NoSuchFileException) {
        ""
      }
    }

    try {
      Files.delete(PathManager.getSystemDir().resolve("testlog").resolve("idea_c.i.d.LogsPersStateManagerTest.log"))
    }
    catch (_: java.nio.file.NoSuchFileException) {
      // Nothing.
    }

    val logger = logger<LogsPersStateManagerTest>()
    val nestedLogger = com.intellij.openapi.diagnostic.Logger.getInstance(this::class.java.name + ".NestedLogger")

    var message = "some info message before enabling; ${ThreadLocalRandom.current().nextLong()}"
    logger.info(message)
    awaitLogQueueProcessed()

    assertThat(readSomeLastLines("idea.log"))
      .describedAs("The INFO log message is logged to idea.log before changing debug log settings")
      .contains(message)
    assertThat(readSomeLastLines("idea_c.i.d.LogsPersStateManagerTest.log"))
      .describedAs("The INFO log message is not logged to the dedicated log file before changing debug log settings")
      .doesNotContain(message)

    message = "some debug message before enabling; ${ThreadLocalRandom.current().nextLong()}"
    logger.debug(message)
    awaitLogQueueProcessed()

    assertThat(readSomeLastLines("idea.log"))
      .describedAs("The DEBUG log message is not logged to anywhere before changing debug log settings")
      .doesNotContain(message)

    assertThat(readSomeLastLines("idea_c.i.d.LogsPersStateManagerTest.log"))
      .describedAs("The DEBUG log message is not logged to anywhere before changing debug log settings")
      .doesNotContain(message)


    LogLevelConfigurationManager.getInstance().setCategories(LogLevelConfigurationManager.State(
      categories = listOf(LogCategory(category = this::class.java.name, level = DebugLogLevel.ALL)),
      categoriesWithSeparateFiles = setOf(this::class.java.name),
    ))


    message = "some info message after enabling; ${ThreadLocalRandom.current().nextLong()}"
    logger.info(message)
    var messageNested = "some info message after enabling; nested logger; ${ThreadLocalRandom.current().nextLong()}"
    nestedLogger.info(messageNested)
    awaitLogQueueProcessed()

    assertThat(readSomeLastLines("idea.log"))
      .describedAs("INFO log messages are written to both log files")
      .contains(message)
      .contains(messageNested)

    assertThat(readSomeLastLines("idea_c.i.d.LogsPersStateManagerTest.log"))
      .describedAs("INFO log messages are written to both log files")
      .contains(message)
      .contains(messageNested)

    message = "some debug message after enabling; ${ThreadLocalRandom.current().nextLong()}"
    logger.debug(message)
    messageNested = "some debug message after enabling; nested logger; ${ThreadLocalRandom.current().nextLong()}"
    nestedLogger.debug(messageNested)
    awaitLogQueueProcessed()

    assertThat(readSomeLastLines("idea.log"))
      .describedAs("DEBUG log messages aren't written to idea.log when dedicated log file is enabled")
      .doesNotContain(message)
      .doesNotContain(messageNested)

    assertThat(readSomeLastLines("idea_c.i.d.LogsPersStateManagerTest.log"))
      .describedAs("DEBUG log messages are written to dedicated log file")
      .contains(message)
      .contains(messageNested)

    message = "some trace message after enabling; ${ThreadLocalRandom.current().nextLong()}"
    logger.trace(message)
    awaitLogQueueProcessed()

    assertThat(readSomeLastLines("idea.log"))
      .describedAs("TRACE log messages aren't written to idea.log when dedicated log file is enabled")
      .doesNotContain(message)

    assertThat(readSomeLastLines("idea_c.i.d.LogsPersStateManagerTest.log"))
      .describedAs("TRACE log messages are written to dedicated log file")
      .contains(message)


    LogLevelConfigurationManager.getInstance().setCategories(LogLevelConfigurationManager.State())


    message = "some info message after disabling; ${ThreadLocalRandom.current().nextLong()}"
    logger.info(message)
    awaitLogQueueProcessed()

    assertThat(readSomeLastLines("idea.log"))
      .describedAs("The INFO log message is logged to idea.log after disabling the dedicated log file")
      .contains(message)
    assertThat(readSomeLastLines("idea_c.i.d.LogsPersStateManagerTest.log"))
      .describedAs("The INFO log message is not logged to the dedicated log file after disabling it")
      .doesNotContain(message)

    message = "some debug message after disabling; ${ThreadLocalRandom.current().nextLong()}"
    logger.debug(message)
    awaitLogQueueProcessed()

    assertThat(readSomeLastLines("idea.log"))
      .describedAs("The DEBUG log message is not logged to anywhere after disabling the dedicated log file")
      .doesNotContain(message)
    assertThat(readSomeLastLines("idea_c.i.d.LogsPersStateManagerTest.log"))
      .describedAs("The DEBUG log message is not logged to anywhere after disabling the dedicated log file")
      .doesNotContain(message)
  }
}