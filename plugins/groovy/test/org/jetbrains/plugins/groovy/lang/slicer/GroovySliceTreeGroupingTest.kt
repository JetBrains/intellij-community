/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.slicer

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiLiteralExpression
import com.intellij.slicer.*
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.slicer.GroovySliceProvider
import org.jetbrains.plugins.groovy.util.TestUtils

class GroovySliceTreeGroupingTest : DaemonAnalyzerTestCase() {
  override fun getTestProjectJdk() = null

  override fun getTestDataPath() = TestUtils.getAbsoluteTestDataPath() + "slicer/tree/"

  private fun loadTreeStructure(getTestFiles: (baseName: String) -> List<String> = { listOf("$it.groovy") }): SliceTreeStructure {
    configureByFiles(null, *getTestFiles(getTestName(false)).toTypedArray())
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val element = SliceHandler(true).getExpressionAtCaret(editor, file)!!

    val errors = highlightErrors()
    UsefulTestCase.assertEmpty(errors)

    val params = SliceAnalysisParams().apply {
      scope = AnalysisScope(project)
      dataFlowToThis = true
    }
    val usage = LanguageSlicing.getProvider(element)!!.createRootUsage(element, params)

    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(myProject)
    val panel = object : SlicePanel(project, true, SliceRootNode(project, DuplicateMap(), usage), false, toolWindow) {
      override fun close() {}

      override fun isAutoScroll() = false

      override fun setAutoScroll(autoScroll: Boolean) {}

      override fun isPreview() = false

      override fun setPreview(preview: Boolean) {}
    }
    Disposer.register(project, panel)

    return panel.builder.treeStructure as SliceTreeStructure
  }

  fun testSimple() {
    val treeStructure = loadTreeStructure()
    val root = treeStructure.rootElement as SliceNode
    val analyzer = GroovySliceProvider.getInstance().createLeafAnalyzer()
    val leaf = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap()).single()
    TestCase.assertEquals(1234567, (leaf as GrLiteral).value)
  }

  fun testGroovyJavaGroovy() {
    val treeStructure = loadTreeStructure { listOf("$it.groovy", "$it.java") }
    val root = treeStructure.rootElement as SliceNode
    val analyzer = GroovySliceProvider.getInstance().createLeafAnalyzer()
    val leaves = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap()).toList()
    UsefulTestCase.assertSize(2, leaves)
    TestCase.assertEquals(123, (leaves[0] as PsiLiteralExpression).value)
    TestCase.assertEquals(456, (leaves[1] as GrLiteral).value)
  }

  fun testJavaGroovy() {
    val treeStructure = loadTreeStructure { listOf("$it.java", "$it.groovy") }
    val root = treeStructure.rootElement as SliceNode
    val analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer()
    val leaves = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap()).toList()
    UsefulTestCase.assertSize(2, leaves)
    TestCase.assertEquals(123, (leaves[0] as PsiLiteralExpression).value)
    TestCase.assertEquals(456, (leaves[1] as GrLiteral).value)
  }
}