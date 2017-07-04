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
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.slicer.*
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.containers.IntArrayList
import gnu.trove.TIntObjectHashMap
import org.jetbrains.plugins.groovy.util.TestUtils

abstract class GroovySliceTestCase(private val isDataFlowToThis: Boolean) : DaemonAnalyzerTestCase() {
  override fun getTestProjectJdk() = null

  private val testDataDir: String
    get() = if (isDataFlowToThis) "backward" else "forward"

  override fun getTestDataPath() = TestUtils.getAbsoluteTestDataPath() + "slicer/$testDataDir/"

  protected fun doTest(getTestFiles: (baseName: String) -> List<String> = { listOf("$it.groovy") }) {
    val psiManager = PsiManager.getInstance(project)
    val psiDocumentManager = PsiDocumentManager.getInstance(project)

    val testFiles = getTestFiles(getTestName(false))
    val rootDir = configureByFiles(null, *testFiles.toTypedArray())

    val documents = testFiles.reversed().map {
      val virtualFile = rootDir.findFileByRelativePath(it)!!
      val psiFile = psiManager.findFile(virtualFile)!!
      psiDocumentManager.getDocument(psiFile)
    }

    val sliceUsageName2Offset = SliceTestUtil.extractSliceOffsetsFromDocuments(documents)

    psiDocumentManager.commitAllDocuments()

    val element = SliceHandler(isDataFlowToThis).getExpressionAtCaret(editor, file)!!
    val flownOffsets = TIntObjectHashMap<IntArrayList>()
    SliceTestUtil.calcRealOffsets(element, sliceUsageName2Offset, flownOffsets)

    val errors = highlightErrors()
    UsefulTestCase.assertEmpty(errors)

    val params = SliceAnalysisParams().apply {
      scope = AnalysisScope(project)
      dataFlowToThis = isDataFlowToThis
    }
    val usage = LanguageSlicing.getProvider(element)!!.createRootUsage(element, params)
    SliceTestUtil.checkUsages(usage, flownOffsets)
  }
}