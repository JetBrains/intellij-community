// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class BaseCoverageSuiteTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `missing absolute coverage file path is preserved`() {
    val coverageFile = tempDir.resolve("missing.ic")

    val suite = TestCoverageSuite()
    suite.readExternal(suiteElement(coverageFile.toString()))

    assertEquals(coverageFile.toString(), suite.coverageDataFileName)
  }

  private fun suiteElement(filePath: String): Element = Element("SUITE")
    .setAttribute("FILE_PATH", filePath)
    .setAttribute("MODIFIED", "0")

  private class TestCoverageSuite : BaseCoverageSuite() {
    override fun getCoverageEngine(): CoverageEngine = error("Not needed by this test")
  }
}
