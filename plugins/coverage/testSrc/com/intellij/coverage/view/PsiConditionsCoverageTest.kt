// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.coverage.CoverageLineMarkerRenderer
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.rt.coverage.data.ClassData
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.rt.coverage.data.LineData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@RunWith(JUnit4::class)
class PsiConditionsCoverageTest : AbstractPsiConditionsCoverageTest() {
  @Test
  fun `test conditions hints`() = assertHints("Conditions", true)

  @Test
  fun `test switches hints`() = assertHints("Switches", true)

  @Test
  fun `test comments and parentheses`() = assertHints("CommentsAndParentheses", true)

  @Test
  fun `test all conditions`() = assertHints("AllConditions", true)

  @Test
  fun `test jacoco conditions hints`() = assertHints("Conditions", false)

  @Test
  fun `test jacoco switches hints`() = assertHints("Switches", false)

  @Test
  fun `test jacoco comments and parentheses`() = assertHints("CommentsAndParentheses", false)

  @Test
  fun `test jacoco all conditions`() = assertHints("AllConditions", false)
}

abstract class AbstractPsiConditionsCoverageTest : CoverageIntegrationBaseTest() {

  override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path =
    Paths.get(PluginPathManager.getPluginHomePath("coverage") + "/testData/conditions")

  protected fun assertHints(className: String, ij: Boolean = true): Unit = runBlocking {
    assertNoSuites()
    val editor = openEditor(className)
    val suite = loadSuite(ij)
    try {
      val coverageData = suite.coverageData ?: error("Failed to load ProjectData from suite ${suite.presentableName}")
      val classData = coverageData.getClassData(className) ?: error("ClassData not found: $className\nAvailable classes: ${coverageData.classes.keys.toSortedSet()}")

      val actual = buildString {
        for (line in 1..editor.document.lineCount) {
          val hint = getLineHint(line, editor, classData, suite) ?: continue
          val status = classData.getLineData(line).stringStatus
          appendLine("Line ${line} coverage: $status\n$hint")
        }
      }

      val expectedFile = projectDirOrFile.resolve("outputs/${if (ij) "ij" else "jacoco"}/$className.txt").toFile()
      assertEqualsFile(expectedFile, actual)
    }
    finally {
      withContext(Dispatchers.EDT) { EditorFactory.getInstance().releaseEditor(editor) }
      closeSuite(suite)
    }
    assertNoSuites()
  }

  private suspend fun openEditor(className: String): EditorImpl {
    openClass(myProject, className)
    return findEditor(myProject, className)
  }

  private fun loadSuite(ij: Boolean) =
    if (ij) loadIJSuite(path = projectDirOrFile.resolve("conditions\$All_in_conditions.ic").toString(), includeFilters = emptyArray())
    else loadJaCoCoSuite(path = projectDirOrFile.resolve("conditions\$All_in_conditions.exec").toString(), includeFilters = emptyArray())

  private suspend fun getLineHint(line: Int, editor: EditorImpl, classData: ClassData, suite: CoverageSuitesBundle): String? {
    val lineData = classData.getLineData(line) ?: return null
    if (lineData.status.toByte() == LineCoverage.NONE) return null
    return readAction { CoverageLineMarkerRenderer.getReport(lineData, line - 1, editor, suite) }
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

private fun generateBoolExpressions(operators: Int): Collection<Pair<String, String>> {
  if (operators == 0) return listOf("?" to "")
  val result = LinkedHashSet<Pair<String, String>>()
  for (left in 0..<operators) {
    val right = operators - 1 - left
    val leftExpressions = generateBoolExpressions(left)
    val rightExpressions = generateBoolExpressions(right)
    for ((l, lop) in leftExpressions) {
      for ((r, rop) in rightExpressions) {
        for (op in listOf("&&", "||")) {
          val ls = wrap(lop, op, l)
          val rs = wrap(rop, op, r)
          result.add("$ls $op $rs" to op)
        }
      }
    }
  }
  return result
}

private fun wrap(topOp: String, newOp: String, expr: String) = if (topOp == "" || topOp == newOp || newOp == "||" && topOp == "&&") expr else "($expr)"

private fun main() {
  val operators = 2
  val variables = (0..operators).map { 'a' + it }.map(Char::toString).toList()
  val prefix = "void test$operators(${variables.joinToString { "boolean $it" }}) {\n  "
  generateBoolExpressions(operators)
    .map { it.first  }
    .map { expression ->
      val indices = expression.withIndex().filter { it.value == '?' }.map { it.index }
      var s = expression
      indices.forEachIndexed { i, index -> s = s.replaceRange(index..index, variables[i]) }
      s
    }
    .mapIndexed { i, e -> "int res$i = $e ? 1 : 2;" }
    .joinToString(prefix = prefix, separator = "\n  ", postfix = "\n}")
    .also { println(it) }
}
