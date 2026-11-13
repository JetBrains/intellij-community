// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.slicer

import com.intellij.analysis.AnalysisScope
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiLiteralExpression
import com.intellij.slicer.DuplicateMap
import com.intellij.slicer.JavaSlicerAnalysisUtil
import com.intellij.slicer.LanguageSlicing
import com.intellij.slicer.SliceAnalysisParams
import com.intellij.slicer.SliceHandler
import com.intellij.slicer.SlicePanel
import com.intellij.slicer.SliceRootNode
import com.intellij.slicer.SliceTreeStructure
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.testNameFixture
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.slicer.GroovySliceProvider
import org.jetbrains.plugins.groovy.util.groovyFixture
import org.jetbrains.plugins.groovy.util.groovyProjectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class GroovySliceTreeGroupingTest {
  // The module has no JDK, as the JUnit 3 base DaemonAnalyzerTestCase of the old test had it.
  private val myFixture by groovyFixture(groovyProject, LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR, "slicer/tree/")
  private val testName by testNameFixture(false)

  private suspend fun loadTreeStructure(getTestFiles: (baseName: String) -> List<String> = { listOf("$it.groovy") }): SliceTreeStructure =
    withContext(Dispatchers.EDT) {
      val project = myFixture.project
      myFixture.configureByFiles(*getTestFiles(testName).toTypedArray())
      PsiDocumentManager.getInstance(project).commitAllDocuments()

      val element = SliceHandler.create(true).getExpressionAtCaret(myFixture.editor, myFixture.file)!!

      val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
      assertTrue(errors.isEmpty(), errors.toString())

      val params = SliceAnalysisParams().apply {
        scope = AnalysisScope(project)
        dataFlowToThis = true
      }
      val usage = LanguageSlicing.getProvider(element)!!.createRootUsage(element, params)

      val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
      val panel = object : SlicePanel(project, true, SliceRootNode(project, DuplicateMap(), usage), false, toolWindow) {
        override fun close() {}

        override fun isAutoScroll() = false

        override fun setAutoScroll(autoScroll: Boolean) {}

        override fun isPreview() = false

        override fun setPreview(preview: Boolean) {}
      }
      // The project lives for the whole class, so the panel goes with the test.
      Disposer.register(myFixture.testRootDisposable, panel)

      panel.builder.treeStructure
    }

  @Test
  fun testSimple() = timeoutRunBlocking {
    val treeStructure = loadTreeStructure()
    withContext(Dispatchers.EDT) {
      val root = treeStructure.rootElement
      val analyzer = GroovySliceProvider.getInstance().createLeafAnalyzer()
      val leaf = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap()).single()
      assertEquals(1234567, (leaf as GrLiteral).value)
    }
  }

  @Test
  fun testGroovyJavaGroovy() = timeoutRunBlocking {
    val treeStructure = loadTreeStructure { listOf("$it.groovy", "$it.java") }
    withContext(Dispatchers.EDT) {
      val root = treeStructure.rootElement
      val analyzer = GroovySliceProvider.getInstance().createLeafAnalyzer()
      val leaves = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap()).toList()
      assertEquals(2, leaves.size, leaves.toString())
      assertEquals(123, (leaves[0] as PsiLiteralExpression).value)
      assertEquals(456, (leaves[1] as GrLiteral).value)
    }
  }

  @Test
  fun testJavaGroovy() = timeoutRunBlocking {
    val treeStructure = loadTreeStructure { listOf("$it.java", "$it.groovy") }
    withContext(Dispatchers.EDT) {
      val root = treeStructure.rootElement
      val analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer()
      val leaves = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap()).toList()
      assertEquals(2, leaves.size, leaves.toString())
      assertEquals(123, (leaves[0] as PsiLiteralExpression).value)
      assertEquals(456, (leaves[1] as GrLiteral).value)
    }
  }

  companion object {
    private val groovyProject = groovyProjectFixture()
  }
}
