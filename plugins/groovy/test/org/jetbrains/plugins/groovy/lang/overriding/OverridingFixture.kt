// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OverridingFixture")

package org.jetbrains.plugins.groovy.lang.overriding

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.function.Function

/**
 * Checks the super methods of the methods of the last class in a test data file. It replaces the JUnit 3 base `OverridingTester`.
 * Call the helpers on the EDT.
 */
@TestOnly
class OverridingTestFixture internal constructor(val fixture: JavaCodeInsightTestFixture, private val testName: String) {
  /** Finds the super methods with [findMethod] and compares them with the second part of the test data file of the test. */
  fun doTest(findMethod: Function<PsiMethod, Array<PsiMethod>>) {
    val testFile = PlatformTestUtil.lowercaseFirstLetter(testName, true) + ".test"
    val strings = TestUtils.readInput(fixture.testDataPath + "/" + testFile)
    val psiFile = fixture.addFileToProject("foo.groovy", strings[0]) as GroovyFileBase

    val buffer = StringBuilder()
    for (method in psiFile.typeDefinitions.last().methods) {
      val classes = findMethod.apply(method).map {
        it.containingClass.toString() + ": " + it.containingClass!!.name + "; " + it.hierarchicalMethodSignature.toString()
      }.sorted()
      for (classAsString in classes) {
        buffer.append(classAsString).append("\n")
      }
      buffer.append("\n")
    }
    buffer.append("\n")

    assertEquals(strings[1], buffer.toString().trim())
  }
}

/** Creates an [OverridingTestFixture] on [groovy] for each test. */
@TestOnly
fun overridingFixture(groovy: TestFixture<JavaCodeInsightTestFixture>): TestFixture<OverridingTestFixture> =
  testFixture("overridingFixture") { context ->
    initialized(OverridingTestFixture(groovy.init(), context.testName)) {}
  }
