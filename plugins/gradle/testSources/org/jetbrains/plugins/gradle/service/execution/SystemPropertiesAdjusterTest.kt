// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.SystemProperties
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val JNA_PROPERTIES = mapOf(
  "jna.boot.library.path" to "/ide/lib/jna/aarch64",
  "jna.nosys" to "true",
  "jna.noclasspath" to "true",
)

private const val JANSI_PROPERTY = "library.jansi.path"

@TestApplication
class SystemPropertiesAdjusterTest {
  private val originalValues = HashMap<String, String?>()
  private val executor: ExecutorService = Executors.newFixedThreadPool(2)

  @BeforeEach
  fun setUp() {
    for ((key, value) in JNA_PROPERTIES + (JANSI_PROPERTY to "/ide/lib/jansi")) {
      originalValues[key] = SystemProperties.setProperty(key, value)
    }
  }

  @AfterEach
  fun tearDown() {
    for ((key, value) in originalValues) {
      SystemProperties.setProperty(key, value)
    }
    executor.shutdownNow()
  }

  @Test
  fun `Gradle 7_6 keeps the JNA properties`() {
    SystemPropertiesAdjuster.executeAdjusted("/project", GradleVersion.version("7.6")) {
      assertJnaPropertiesUnchanged()
      assertNull(System.getProperty(JANSI_PROPERTY))
    }
    assertJnaPropertiesUnchanged()
    assertEquals("/ide/lib/jansi", System.getProperty(JANSI_PROPERTY))
  }

  @Test
  fun `Gradle 7_5 masks the JNA properties`() {
    SystemPropertiesAdjuster.executeAdjusted("/project", GradleVersion.version("7.5")) {
      assertJnaPropertiesMasked()
    }
    assertJnaPropertiesUnchanged()
  }

  @Test
  fun `unknown Gradle version masks the JNA properties`() {
    SystemPropertiesAdjuster.executeAdjusted("/project", null) {
      assertJnaPropertiesMasked()
    }
    assertJnaPropertiesUnchanged()
  }

  @Test
  fun `nested operations restore the original values`() {
    SystemPropertiesAdjuster.executeAdjusted("/project", null) {
      SystemPropertiesAdjuster.executeAdjusted("/project", null) {
        assertJnaPropertiesMasked()
      }
      assertJnaPropertiesMasked()
    }
    assertJnaPropertiesUnchanged()
  }

  @Test
  fun `overlapping operations restore the original values when the first operation ends first`() {
    val firstStarted = CountDownLatch(1)
    val secondStarted = CountDownLatch(1)
    val firstEnded = CountDownLatch(1)

    val first = CompletableFuture.runAsync({
      SystemPropertiesAdjuster.executeAdjusted("/project", null) {
        firstStarted.countDown()
        secondStarted.await(10, TimeUnit.SECONDS)
      }
      firstEnded.countDown()
    }, executor)
    firstStarted.await(10, TimeUnit.SECONDS)
    val second = CompletableFuture.runAsync({
      SystemPropertiesAdjuster.executeAdjusted("/project", null) {
        secondStarted.countDown()
        firstEnded.await(10, TimeUnit.SECONDS)
        assertJnaPropertiesMasked()
      }
    }, executor)

    CompletableFuture.allOf(first, second).get(20, TimeUnit.SECONDS)
    assertJnaPropertiesUnchanged()
  }

  @Test
  fun `operation for Gradle 7_6 does not keep the JNA properties masked by an older Gradle`() {
    val oldStarted = CountDownLatch(1)
    val newStarted = CountDownLatch(1)
    val oldEnded = CountDownLatch(1)

    val old = CompletableFuture.runAsync({
      SystemPropertiesAdjuster.executeAdjusted("/project", GradleVersion.version("7.5")) {
        oldStarted.countDown()
        newStarted.await(10, TimeUnit.SECONDS)
      }
      oldEnded.countDown()
    }, executor)
    oldStarted.await(10, TimeUnit.SECONDS)
    val new = CompletableFuture.runAsync({
      SystemPropertiesAdjuster.executeAdjusted("/project", GradleVersion.version("8.14")) {
        newStarted.countDown()
        oldEnded.await(10, TimeUnit.SECONDS)
        assertJnaPropertiesUnchanged()
      }
    }, executor)

    CompletableFuture.allOf(old, new).get(20, TimeUnit.SECONDS)
    assertJnaPropertiesUnchanged()
    assertEquals("/ide/lib/jansi", System.getProperty(JANSI_PROPERTY))
  }
}

private fun assertJnaPropertiesUnchanged() {
  for ((key, value) in JNA_PROPERTIES) {
    assertEquals(value, System.getProperty(key), key)
  }
}

private fun assertJnaPropertiesMasked() {
  for (key in JNA_PROPERTIES.keys) {
    assertNull(System.getProperty(key), key)
  }
}
