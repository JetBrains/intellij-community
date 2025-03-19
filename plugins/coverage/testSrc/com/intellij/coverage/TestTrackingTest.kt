// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.openapi.application.readAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.rt.coverage.data.ProjectData
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TestTrackingTest : CoverageIntegrationBaseTest() {

  /**
   * @see com.intellij.coverage.listeners.java.CoverageListener.getData
   */
  @Test
  fun `test ProjectData API compatibility`() {
    val projectData = ProjectData.getProjectData()
    @Suppress("USELESS_IS_CHECK")
    // check that API does not change
    Assert.assertTrue(projectData == null || projectData is ProjectData)
  }

  @Test
  fun `test test-tracking names`(): Unit = runBlocking {
    val suite = loadIJSuite()

    assertEquals("""
      3 foo.bar.BarTest,testMethod3
      5 foo.bar.BarTest,testMethod3
    """.trimIndent(), collectActualTests("foo.bar.BarClass", suite))

    assertEquals("", collectActualTests("foo.bar.UncoveredClass", suite))

    assertEquals("""
      3 foo.FooTest,testMethod1; foo.FooTest,testMethod2
      5 foo.FooTest,testMethod1
      9 foo.FooTest,testMethod2
    """.trimIndent(), collectActualTests("foo.FooClass", suite))
  }

  @Test
  fun `test methods list extraction`() {
    val suite = loadIJSuite()

    val engine = suite.coverageEngine
    val tests = engine.findTestsByNames(arrayOf("foo.FooTest,testMethod1"), myProject)
    // junit framework is not detected correctly
    assertEquals(emptyList<PsiElement>(), tests)
  }


  private suspend fun collectActualTests(fqn: String, suite: CoverageSuitesBundle): String {
    val lines = readAction {
      JavaPsiFacade.getInstance(myProject).findClass(fqn, GlobalSearchScope.projectScope(myProject))!!.containingFile.fileDocument.lineCount
    }
    val engine = suite.coverageEngine
    return (1..lines)
      .mapNotNull { line ->
        engine.getTestsForLine(myProject, suite, fqn, line).sorted().takeIf { it.isNotEmpty() }?.joinToString("; ")?.let { "$line $it" }
      }
      .joinToString("\n")
  }
}
