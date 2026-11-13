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
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.slicer.LanguageSlicing
import com.intellij.slicer.SliceAnalysisParams
import com.intellij.slicer.SliceHandler
import com.intellij.slicer.SliceTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testNameFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.groovy.util.groovyFixture
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Checks the slice tree of the expression at the caret against the markers of the test files.
 * The module has no JDK, as the JUnit 3 base `DaemonAnalyzerTestCase` of the old test had it.
 */
@TestApplication
abstract class GroovySliceTestCase(projectFixture: TestFixture<Project>, private val isDataFlowToThis: Boolean) {
  private val myFixture by groovyFixture(projectFixture, LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR,
                                         "slicer/${if (isDataFlowToThis) "backward" else "forward"}/")
  private val testName by testNameFixture(false)

  protected fun doTest(getTestFiles: (baseName: String) -> List<String> = { listOf("$it.groovy") }) {
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        val project = myFixture.project
        val psiManager = PsiManager.getInstance(project)
        val psiDocumentManager = PsiDocumentManager.getInstance(project)

        val testFiles = getTestFiles(testName)
        myFixture.configureByFiles(*testFiles.toTypedArray())
        val rootDir = myFixture.tempDirFixture.getFile("")!!

        val documents = testFiles.reversed().map {
          val virtualFile = rootDir.findFileByRelativePath(it)!!
          val psiFile = psiManager.findFile(virtualFile)!!
          psiDocumentManager.getDocument(psiFile)
        }

        val sliceUsageName2Offset = SliceTestUtil.extractSliceOffsetsFromDocuments(documents)

        psiDocumentManager.commitAllDocuments()

        val element = SliceHandler.create(isDataFlowToThis).getExpressionAtCaret(myFixture.editor, myFixture.file)!!
        val tree = SliceTestUtil.buildTree(element, sliceUsageName2Offset)

        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
        assertTrue(errors.isEmpty(), errors.toString())

        val params = SliceAnalysisParams().apply {
          scope = AnalysisScope(project)
          dataFlowToThis = isDataFlowToThis
        }
        val usage = LanguageSlicing.getProvider(element)!!.createRootUsage(element, params)
        SliceTestUtil.checkUsages(usage, tree)
      }
    }
  }
}
