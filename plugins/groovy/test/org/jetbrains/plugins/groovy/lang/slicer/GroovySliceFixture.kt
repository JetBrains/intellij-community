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
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.slicer.LanguageSlicing
import com.intellij.slicer.SliceAnalysisParams
import com.intellij.slicer.SliceHandler
import com.intellij.slicer.SliceTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * The fixture of the Groovy slicer tests. Create it with [groovySliceFixture].
 * It checks the slice tree of the expression at the caret against the markers of the test files.
 * It replaces the JUnit 3 base `GroovySliceTestCase`.
 */
@TestOnly
class GroovySliceFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val isDataFlowToThis: Boolean,
) {
  /**
   * Configures the test files that [getTestFiles] gives for the test name, and checks the slice tree of the expression at the caret.
   * The slicer and the highlighting run on the EDT, from the configuration to the last check.
   */
  fun doTest(getTestFiles: (baseName: String) -> List<String> = { listOf("$it.groovy") }) {
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        val project = fixture.project
        val psiManager = PsiManager.getInstance(project)
        val psiDocumentManager = PsiDocumentManager.getInstance(project)

        val testFiles = getTestFiles(fixture.getTestName(false))
        fixture.configureByFiles(*testFiles.toTypedArray())
        val rootDir = fixture.tempDirFixture.getFile("")!!

        val documents = testFiles.reversed().map {
          val virtualFile = rootDir.findFileByRelativePath(it)!!
          val psiFile = psiManager.findFile(virtualFile)!!
          psiDocumentManager.getDocument(psiFile)
        }

        val sliceUsageName2Offset = SliceTestUtil.extractSliceOffsetsFromDocuments(documents)

        psiDocumentManager.commitAllDocuments()

        val element = SliceHandler.create(isDataFlowToThis).getExpressionAtCaret(fixture.editor, fixture.file)!!
        val tree = SliceTestUtil.buildTree(element, sliceUsageName2Offset)

        val errors = fixture.doHighlighting(HighlightSeverity.ERROR)
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

/**
 * Creates the [GroovySliceFixture] of one test in the project of [projectFixture].
 * [isDataFlowToThis] selects the backward slice, and the test data of `slicer/backward/` or `slicer/forward/`.
 * The module of [projectFixture] has no JDK, as the JUnit 3 base `DaemonAnalyzerTestCase` of the old test had it.
 */
@TestOnly
fun groovySliceFixture(projectFixture: TestFixture<LightJavaProject>, isDataFlowToThis: Boolean): TestFixture<GroovySliceFixture> =
  testFixture("groovySliceFixture") {
    val fixture = lightJavaCodeInsightFixture(projectFixture).init()
    fixture.testDataPath = TestUtils.getAbsoluteTestDataPath() + "slicer/${if (isDataFlowToThis) "backward" else "forward"}/"
    initialized(GroovySliceFixture(fixture, isDataFlowToThis)) {}
  }
