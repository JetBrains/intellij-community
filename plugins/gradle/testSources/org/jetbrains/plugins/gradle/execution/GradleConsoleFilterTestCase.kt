// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNull

@TestApplication
abstract class GradleConsoleFilterTestCase {

  private val project = projectFixture()

  abstract fun getFilter(project: Project): Filter

  fun String.shouldNotMatch() {
    assertNull(applyFilter(this))
  }

  fun assertMatch(linePrefix: String, path: String, lineSuffix: String) {
    val line = linePrefix + path + lineSuffix
    val result = applyFilter(line) ?: throw AssertionError("Filter result should not be null")
    assertEquals(1, result.resultItems.size)
    val resultItem = result.resultItems.single()
    val parsedPath = line.substring(resultItem.highlightStartOffset, resultItem.highlightEndOffset)
    assertThat(parsedPath).isEqualTo(path)
  }

  private fun applyFilter(line: String): Filter.Result? = getFilter(project.get())
    .applyFilter(line, line.length)
}
