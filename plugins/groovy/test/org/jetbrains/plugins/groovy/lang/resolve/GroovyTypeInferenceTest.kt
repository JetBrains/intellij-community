// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.groovy.testFramework.GroovyAssertions.assertType
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.GrArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAsyncExpression
import org.jetbrains.plugins.groovy.lightGroovyFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
class GroovyTypeInferenceTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_6_0)
  }

  private val myFixture by lightGroovyFixture(projectFixture)

  @Test
  fun testOuterArrayInitializer() {
    myFixture.configureByText("A.groovy", "def a = new String[][]{<caret> {}, {\"foo\"}}")
    runReadActionBlocking {
      val brace = myFixture.file.findElementAt(myFixture.caretOffset)
      assertNotNull(brace)
      val arrayInitializer =  brace?.parent as? GrArrayInitializer
      assertNotNull(arrayInitializer)
      assertType("java.lang.String[][]", arrayInitializer!!.type)
    }
  }

  @Test
  fun testNestedArrayInitializer() {
    myFixture.configureByText("A.groovy", "def a = new String[][]{{<caret>}, {\"foo\"}}")
    runReadActionBlocking {
      val brace = myFixture.file.findElementAt(myFixture.caretOffset)
      assertNotNull(brace)
      val arrayInitializer =  brace?.parent as? GrArrayInitializer
      assertNotNull(arrayInitializer)
      assertType("java.lang.String[]", arrayInitializer!!.type)
    }
  }

  @Test
  fun testAsyncExpression() {
    myFixture.configureByText("A.groovy", "async { return 1 }")
    runReadActionBlocking {
      val asyncExpression = myFixture.file.findElementAt(0)!!.parent as GrAsyncExpression
      assertType("groovy.concurrent.Awaitable<java.lang.Integer>", asyncExpression.type)
    }
  }

  @Test
  fun testAsyncGenerator() {
    myFixture.configureByText("A.groovy",
                              """async {
                                   for(int i = 0; i < 10; i++) {
                                      yield return i
                                   }
                                 }""".trimMargin())
    runReadActionBlocking {
      val asyncExpression = myFixture.file.findElementAt(0)!!.parent as GrAsyncExpression
      assertType("java.lang.Iterable<java.lang.Integer>", asyncExpression.type)
    }
  }
}