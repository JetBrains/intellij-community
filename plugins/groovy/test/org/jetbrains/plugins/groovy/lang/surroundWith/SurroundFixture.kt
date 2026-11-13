// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.surroundWith

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.application.EDT
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

/**
 * Checks a [Surrounder] in [fixture]. It replaces the JUnit 3 base `SurroundTestCase`.
 * The helpers work on the EDT and off it. The surrounder runs on the EDT.
 */
@TestOnly
class SurroundFixture internal constructor(val fixture: LightJavaCodeInsightTestFixture) {
  private val testName: String
    get() = fixture.getTestName(false)

  /** Checks [surrounder] with the two parts of the test data file of the test. */
  fun doTest(surrounder: Surrounder) {
    val data = TestUtils.readInput(fixture.testDataPath + "/" + fixture.getTestName(true) + ".test")
    doTest(surrounder, data.getOrNull(0), data.getOrNull(1))
  }

  fun doTest(surrounder: Surrounder, textBefore: String?, textAfter: String?) {
    fixture.configureByText("a.groovy", textBefore!!)
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        SurroundWithHandler.invoke(fixture.project, fixture.editor, fixture.file, surrounder)
      }
    }
    fixture.checkResult(textAfter!!)
  }
}

/**
 * Creates a [SurroundFixture] in the project of [projectFixture] for each test.
 * [testDataPath] is the absolute test data path. By default, it is the community root.
 */
@TestOnly
@JvmOverloads
fun surroundFixture(projectFixture: TestFixture<LightJavaProject>, testDataPath: String? = null): TestFixture<SurroundFixture> =
  testFixture("surroundFixture") {
    val fixture = lightJavaCodeInsightFixture(projectFixture).init()
    if (testDataPath != null) {
      fixture.testDataPath = testDataPath
    }
    initialized(SurroundFixture(fixture)) {}
  }
