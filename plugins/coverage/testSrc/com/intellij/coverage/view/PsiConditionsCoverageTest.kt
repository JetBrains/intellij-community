// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.coverage.CoverageLineMarkerRenderer
import com.intellij.coverage.JavaCoverageEngine
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
  fun `test conditions hints`() = assertHints("Conditions")

  @Test
  fun `test switches hints`() = assertHints("Switches")

  @Test
  fun `test comments and parentheses`() = assertHints("CommentsAndParentheses")

  @Test
  fun `test all conditions`() = assertHints("AllConditions")
}

abstract class AbstractPsiConditionsCoverageTest : CoverageIntegrationBaseTest() {

  override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path =
    Paths.get(PluginPathManager.getPluginHomePath("coverage") + "/testData/conditions")

  protected fun assertHints(className: String): Unit = runBlocking {
    assertNoSuites()
    val editor = openEditor(className)
    val suite = loadSuite()
    try {
      val classData = suite.coverageData!!.getClassData(className)

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
      withContext(Dispatchers.EDT) { EditorFactory.getInstance().releaseEditor(editor) }
      closeSuite(suite)
    }
    assertNoSuites()
  }

  private suspend fun openEditor(className: String): EditorImpl {
    openClass(myProject, className)
    return findEditor(myProject, className)
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
