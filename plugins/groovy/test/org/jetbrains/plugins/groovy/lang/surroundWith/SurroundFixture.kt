// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SurroundFixture")

package org.jetbrains.plugins.groovy.lang.surroundWith

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.util.TestUtils
import org.jetbrains.plugins.groovy.util.configureByExactName

/**
 * Checks a [Surrounder] in [fixture]. It replaces the JUnit 3 base `SurroundTestCase`.
 * Call the helpers on the EDT.
 */
@TestOnly
class SurroundTestFixture internal constructor(val fixture: JavaCodeInsightTestFixture, private val testName: String) {
  /** Checks [surrounder] with the two parts of the test data file of the test. */
  fun doTest(surrounder: Surrounder) {
    val data = TestUtils.readInput(fixture.testDataPath + "/" + PlatformTestUtil.lowercaseFirstLetter(testName, true) + ".test")
    doTest(surrounder, data.getOrNull(0), data.getOrNull(1))
  }

  fun doTest(surrounder: Surrounder, textBefore: String?, textAfter: String?) {
    configureByExactName(fixture, "a.groovy", textBefore!!)
    SurroundWithHandler.invoke(fixture.project, fixture.editor, fixture.file, surrounder)
    fixture.checkResult(textAfter!!)
  }
}

/** Creates a [SurroundTestFixture] on [groovy] for each test. */
@TestOnly
fun surroundFixture(groovy: TestFixture<JavaCodeInsightTestFixture>): TestFixture<SurroundTestFixture> =
  testFixture("surroundFixture") { context ->
    initialized(SurroundTestFixture(groovy.init(), context.testName)) {}
  }
