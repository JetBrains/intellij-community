// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.coverage.CoverageLineMarkerRenderer
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.JavaCoverageEngine
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.rt.coverage.data.ClassData
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.rt.coverage.data.LineData
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@RunWith(JUnit4::class)
class PsiConditionsCoverageTest : CoverageIntegrationBaseTest() {

  override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path =
    Paths.get(PluginPathManager.getPluginHomePath("coverage") + "/testData/conditions")

  @Test
  fun `test conditions hints`() = assertHints("Conditions")

  @Test
  fun `test switches hints`() = assertHints("Switches")


  private fun assertHints(className: String): Unit = runBlocking {
    assertNoSuites()
    val suite = loadSuite()
    try {
      val (editor, classData) = openEditor(className, suite)

      val actual = buildString {
        for (line in 1..editor.document.lineCount) {
          val hint = getLineHint(line, editor, classData) ?: continue
          val status = classData.getLineData(line).stringStatus
          appendLine("Line ${line} coverage: $status\n$hint")
        }
      }

      val expectedFile = projectDirOrFile.resolve("$className.txt").toFile()
      assertEqualsFile(expectedFile, actual)
    }
    finally {
      closeSuite(suite)
      assertNoSuites()
    }
  }

  private suspend fun openEditor(className: String, suite: CoverageSuitesBundle): Pair<EditorImpl, ClassData> {
    openClass(myProject, className)
    return findEditor(myProject, className) to suite.coverageData!!.getClassData(className)
  }

  private fun loadSuite() = loadIJSuite(path = projectDirOrFile.resolve("conditions\$All_in_conditions.ic").toString())

  private suspend fun getLineHint(line: Int, editor: EditorImpl, classData: ClassData): String? {
    val engine = JavaCoverageEngine.getInstance()
    return readAction { CoverageLineMarkerRenderer.getReport(classData.getLineData(line), line - 1, editor, engine) }
  }
}

private fun assertEqualsFile(expectedFile: File, actual: String) {
  val content = expectedFile.readText()
  if (content != actual) {
    throw FileComparisonFailedError("File content differs", content, actual, expectedFilePath = expectedFile.absolutePath)
  }
}

private val LineData.stringStatus
  get() = when (status.toByte()) {
    LineCoverage.NONE -> "NONE"
    LineCoverage.PARTIAL -> "PARTIAL"
    LineCoverage.FULL -> "FULL"
    else -> error("Unexpected status")
  }
