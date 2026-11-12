// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.groovy.testFramework.GroovyAssertions
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.forbidNestedContext
import org.jetbrains.plugins.groovy.lightGroovyFixture
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.Assertions.assertInstanceOf

/**
 * The fixture of the Groovy type inference tests. Create it with [typeInferenceFixture].
 *
 * A member configures `_.groovy` with the text of the call, and compares the inferred type of an expression with the expected type.
 * The members read the PSI in a read action.
 */
@TestOnly
class TypeInferenceFixture internal constructor(fixture: LightJavaCodeInsightTestFixture) : GroovyResolveFixture(fixture) {
  /**
   * Checks the type of the reference at the caret.
   */
  fun doTest(text: String, type: String?) {
    val file = fixture.configureByText("_.groovy", text)
    ReadAction.runBlocking<RuntimeException> {
      val ref = file.findReferenceAt(fixture.caretOffset) as GrReferenceExpression
      GroovyAssertions.assertType(type, ref.type)
    }
  }

  /**
   * Checks the type of the [clazz] expression around the caret.
   */
  fun doTest(text: String, clazz: Class<out GrExpression>, type: String?) {
    val file = fixture.configureByText("_.groovy", text)
    ReadAction.runBlocking<RuntimeException> {
      val target = PsiTreeUtil.getParentOfType(file.findElementAt(fixture.caretOffset), clazz)
      GroovyAssertions.assertType(type, target!!.type)
    }
  }

  /**
   * Checks the type of the last statement of [text]. The statement must be an expression.
   */
  fun doExprTest(text: String, expectedType: String?) {
    val file = fixture.configureByText("_.groovy", text) as GroovyFile
    ReadAction.runBlocking<RuntimeException> {
      val lastStatement = file.statements.last()
      GroovyAssertions.assertType(expectedType, assertInstanceOf(GrExpression::class.java, lastStatement).type)
    }
  }

  /**
   * Checks the type of the last statement of [text] in a `@CompileStatic` method. The statement must be an expression.
   */
  fun doCSExprTest(text: String, expectedType: String?) {
    val file = fixture.configureByText("_.groovy", "@groovy.transform.CompileStatic\ndef m() {\n  $text}\n") as GroovyFile
    ReadAction.runBlocking<RuntimeException> {
      val lastStatement = file.methods[0].block!!.statements.last()
      GroovyAssertions.assertType(expectedType, assertInstanceOf(GrExpression::class.java, lastStatement).type)
    }
  }

  companion object {
    /**
     * The test data path of the type inference tests.
     */
    @JvmStatic
    fun getInferenceTestDataPath(): String = TestUtils.getAbsoluteTestDataPath() + "resolve/inference/"
  }
}

/**
 * Creates the [TypeInferenceFixture] of a type inference test. Declare it as an instance field.
 *
 * The test data path is [TypeInferenceFixture.getInferenceTestDataPath].
 * Before the test, the fixture adds a stub of `java.math.BigDecimal` and forbids the nested contexts of the type inference.
 * It also copies the test data directory `<test name>` to the project, as [groovyResolveFixture] does.
 */
@TestOnly
fun typeInferenceFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<TypeInferenceFixture> =
  testFixture("typeInferenceFixture") {
    val fixture = lightGroovyFixture(projectFixture, TypeInferenceFixture.getInferenceTestDataPath()).init()
    val inference = TypeInferenceFixture(fixture)
    inference.setUp()
    fixture.addClass("package java.math; public class BigDecimal extends Number implements Comparable<BigDecimal> {}")
    forbidNestedContext(fixture.testRootDisposable)
    initialized(inference) {}
  }
