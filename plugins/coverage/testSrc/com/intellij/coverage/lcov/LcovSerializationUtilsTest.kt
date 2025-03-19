// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.lcov

import com.intellij.testFramework.assertEqualsToFile
import org.junit.Test
import java.io.File
import java.util.function.Function
import com.intellij.openapi.application.PluginPathManager

// test got moved from coverage-common, since tests believe
// that intellij.appcode.main started to depend on intellij.java.ide.customization
class LcovSerializationUtilsTest {

  private val DATA_PATH = PluginPathManager.getPluginHomePath("coverage")

  @Test
  fun lcovLoadedCorrectly() {
    val lcov = LcovSerializationUtils.readLCOV(listOf(
      File("$DATA_PATH/testData/lcov/coverage.info"),
      File("$DATA_PATH/testData/lcov/coverage2.info")))
    val lcovLines = lcov.info.values.joinToString("\n")
    assertEqualsToFile("LCOV report read incorrectly", File("$DATA_PATH/testData/lcov/line-hints.expected"), lcovLines)
    val projectData = LcovSerializationUtils.convertToProjectData(lcov, Function.identity())
    for ((file, hints) in lcov.info) {
      val data = projectData.getClassData(file)
      assert(data != null)
      for (hint in hints) {
        val lineData = data.getLineData(hint.lineNumber)
        assert(lineData != null)
        assert(lineData!!.hits == hint.hits)
        assert(lineData.methodSignature == hint.functionName)
      }
    }
  }
}