/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.uast

import com.intellij.platform.uast.testFramework.env.findElementByText
import com.intellij.platform.uast.testFramework.env.findElementByTextFromPsi
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAnnotationEx
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.getValueIfStringLiteral
import org.jetbrains.uast.isStringLiteral
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class GroovyUastApiTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val uast by groovyUastFixture(projectFixture)

  @Test
  fun testUastAnchors() {
    uast.doTest("SimpleClass.groovy") { _, file ->
      val uClass = file.classes.single { it.qualifiedName == "SimpleClass" }
      assertEquals("SimpleClass", uClass.uastAnchor?.psi?.text)
      val uMethod = uClass.methods.single { it.name == "bar" }
      assertEquals("bar", uMethod.uastAnchor?.psi?.text)
      val uParameter = uMethod.uastParameters.single()
      assertEquals("param", uParameter.uastAnchor?.psi?.text)
    }
  }

  @Test
  fun testAnnotationAnchor() {
    uast.doTest("SimpleClass.groovy") { _, file ->
      val uAnnotation = file.findElementByText<UAnnotation>("@java.lang.Deprecated")
      assertEquals("Deprecated", (uAnnotation as UAnnotationEx).uastAnchor?.psi?.text)
    }
  }

  @Test
  fun testStringLiteral() {
    uast.doTest("Annotations.groovy") { _, file ->
      file.findElementByTextFromPsi<ULiteralExpression>("\"abc\"").let { literal ->
        assertTrue(literal.isStringLiteral())
        assertEquals("abc", literal.value)
        assertEquals("abc", literal.getValueIfStringLiteral())
      }
      file.findElementByTextFromPsi<ULiteralExpression>("123").let { literal ->
        assertFalse(literal.isStringLiteral())
        assertEquals(123, literal.value)
        assertEquals(null, literal.getValueIfStringLiteral())
      }

    }
  }

}