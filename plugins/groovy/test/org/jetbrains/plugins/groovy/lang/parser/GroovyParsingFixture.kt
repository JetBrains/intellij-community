// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * The fixture of the Groovy parser tests. Create it with [groovyParsingFixture].
 *
 * A test data file holds the Groovy text, the `-----` line, and the expected PSI tree.
 */
@TestOnly
class GroovyParsingFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Checks the test data file of the test. A `$` in the test name is a directory separator.
   */
  fun doTest() {
    doTest(getTestName(true).replace("$", "/") + ".test")
  }

  fun doTest(fileName: String) {
    val input = TestUtils.readInput(fixture.testDataPath + "/" + fileName).firstOrNull()
    checkParsing(input, fileName)
  }

  /**
   * Parses [input] and compares the input and its PSI tree with the test data file [path].
   */
  fun checkParsing(input: String?, path: String) {
    val psiTree = psiTree(input)
    fixture.configureByText("test.txt", input + "\n-----\n" + psiTree.trim())
    fixture.checkResultByFile(path, false)
  }


  private fun psiTree(input: String?): String = ReadAction.computeBlocking<String, RuntimeException> {
    DebugUtil.psiToString(TestUtils.createPseudoPhysicalGroovyFile(fixture.project, input), true)
  }
}

/**
 * Creates a [GroovyParsingFixture] for one test. Declare it as an instance field.
 * [testDataPath] is the absolute directory of the test data files.
 */
@TestOnly
fun groovyParsingFixture(projectFixture: TestFixture<LightJavaProject>, testDataPath: String): TestFixture<GroovyParsingFixture> =
  testFixture("groovyParsingFixture") {
    val fixture = lightJavaCodeInsightFixture(projectFixture).init()
    fixture.testDataPath = testDataPath
    initialized(GroovyParsingFixture(fixture)) {}
  }
