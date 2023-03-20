// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Assertions.assertTrue

class TestGradleConfigurationProducerUtilTest {

  @Test
  fun testThatSettingsGetProperTaskNames() {
    val settingsUnderTest = ExternalSystemTaskExecutionSettings()

    val fileWithTestsA = MockVirtualFile("FileNameA.kt")
    val fileWithTestsB = MockVirtualFile("FileNameB.kt")

    val applied = settingsUnderTest.applyTestConfiguration(
      "whatever-project-path",
      listOf("TestA", "TestB"),
      {
        when(it) {
          "TestA" -> fileWithTestsA
          "TestB" -> fileWithTestsB
          else -> null
        }
      },
      { "FILTER" },
      {
        when(it) {
          fileWithTestsA -> listOf(listOf("task A", "task B"))
          fileWithTestsB -> listOf(listOf(":module1:module2:taskC", "taskD"))
          else -> emptyList()
        }

      }
    )

    assertTrue(applied) { "Test configuration was not applied" }
    assertIterableEquals(listOf("'task A'", "'task B'", "FILTER", ":module1:module2:taskC", "taskD", "FILTER"), settingsUnderTest.taskNames)
  }
}