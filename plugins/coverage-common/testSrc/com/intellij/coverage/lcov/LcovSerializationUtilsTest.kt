package com.intellij.coverage.lcov

import com.intellij.rt.coverage.util.CoverageReport
import com.intellij.testFramework.assertEqualsToFile
import org.junit.Test
import java.io.File
import java.util.function.Function

class LcovSerializationUtilsTest {

  @Test
  fun lcovLoadedCorrectly() {
    val lcov = LcovSerializationUtils.readLCOV(listOf(File("testData/lcov/coverage.info"), File("testData/lcov/coverage2.info")))
    val lcovLines = lcov.info.values.joinToString("\n")
    assertEqualsToFile("LCOV report read incorrectly", File("testData/lcov/line-hints.expected"), lcovLines)
    val projectData = LcovSerializationUtils.convertToProjectData(lcov, Function.identity())
    val dataFile = File.createTempFile("dataFile", "").also { it.deleteOnExit() }
    val sourceMapFile = File.createTempFile("sourceMap", "").also { it.deleteOnExit() }
    CoverageReport.save(projectData, dataFile, sourceMapFile)
    assertEqualsToFile("Project data saved incorrectly", File("testData/lcov/project.data"), dataFile.readText())
  }
}