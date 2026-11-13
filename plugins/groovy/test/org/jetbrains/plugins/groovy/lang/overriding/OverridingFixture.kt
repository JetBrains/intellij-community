// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.overriding

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import java.util.function.Function
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Checks the super methods of the methods of the last class in a test data file. It replaces the JUnit 3 base `OverridingTester`.
 * The helpers work on the EDT and off it.
 */
@TestOnly
class OverridingFixture internal constructor(val fixture: LightJavaCodeInsightTestFixture) {
  private val testName: String
    get() = fixture.getTestName(false)

  /** Finds the super methods with [findMethod] and compares them with the second part of the test data file of the test. */
  fun doTest(findMethod: Function<PsiMethod, Array<PsiMethod>>) {
    val testFile = fixture.getTestName(true) + ".test"
    val strings = TestUtils.readInput(fixture.testDataPath + "/" + testFile)
    val psiFile = fixture.addFileToProject("foo.groovy", strings[0]) as GroovyFileBase

    val actual = ReadAction.computeBlocking<String, Throwable> {
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
      buffer.toString().trim()
    }

    assertEquals(strings[1], actual)
  }
}

/**
 * Creates an [OverridingFixture] in the project of [projectFixture] for each test.
 * [testDataPath] is the absolute test data path. By default, it is the community root.
 */
@TestOnly
@JvmOverloads
fun overridingFixture(projectFixture: TestFixture<LightJavaProject>, testDataPath: String? = null): TestFixture<OverridingFixture> =
  testFixture("overridingFixture") {
    val fixture = lightJavaCodeInsightFixture(projectFixture).init()
    if (testDataPath != null) {
      fixture.testDataPath = testDataPath
    }
    initialized(OverridingFixture(fixture)) {}
  }
