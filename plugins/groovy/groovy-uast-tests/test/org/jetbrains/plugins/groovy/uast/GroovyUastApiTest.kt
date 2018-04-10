/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.uast

import junit.framework.TestCase
import org.jetbrains.uast.*
import org.jetbrains.uast.test.env.findElementByText
import org.jetbrains.uast.test.env.findElementByTextFromPsi
import org.junit.Test

class GroovyUastApiTest : AbstractGroovyUastTest() {
  override fun check(testName: String, file: UFile) {
  }

  @Test
  fun testUastAnchors() {
    doTest("SimpleClass.groovy") { name, file ->
      val uClass = file.classes.single { it.qualifiedName == "SimpleClass" }
      TestCase.assertEquals("SimpleClass", uClass.uastAnchor?.psi?.text)
      val uMethod = uClass.methods.single { it.name == "bar" }
      TestCase.assertEquals("bar", uMethod.uastAnchor?.psi?.text)
      val uParameter = uMethod.uastParameters.single()
      TestCase.assertEquals("param", uParameter.uastAnchor?.psi?.text)
    }
  }

  @Test
  fun testAnnotationAnchor() {
    doTest("SimpleClass.groovy") { name, file ->
      val uAnnotation = file.findElementByText<UAnnotation>("@java.lang.Deprecated")
      TestCase.assertEquals("Deprecated", (uAnnotation as UAnnotationEx).uastAnchor?.psi?.text)
    }
  }

  @Test
  fun testStringLiteral() {
    doTest("Annotations.groovy") { name, file ->
      file.findElementByTextFromPsi<ULiteralExpression>("\"abc\"").let { literal ->
        TestCase.assertTrue(literal.isStringLiteral())
        TestCase.assertEquals("abc", literal.value)
        TestCase.assertEquals("abc", literal.getValueIfStringLiteral())
      }
      file.findElementByTextFromPsi<ULiteralExpression>("123").let { literal ->
        TestCase.assertFalse(literal.isStringLiteral())
        TestCase.assertEquals(123, literal.value)
        TestCase.assertEquals(null, literal.getValueIfStringLiteral())
      }

    }
  }

}