// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.groovy.testFramework.GroovyAssertions.assertType
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lightGroovyFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
class GroovyForInClauseInferenceTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0)
  }

  private val myFixture by lightGroovyFixture(projectFixture)

  @Test
  fun testInferKnownTypeForValueVariable() {
    myFixture.configureByText("a.groovy",
      """
        for(int value in 1..10) {
          println val<caret>ue
        }
      """.trimIndent()
    )
    runReadActionBlocking {
      val element = myFixture.elementAtCaret as? GrParameter
      assertNotNull(element)
      assertType("int", element!!.typeGroovy)
    }
  }

  @Test
  fun testInferUnknownTypeForValueVariable() {
    myFixture.configureByText("a.groovy",
                            """
        for(var value in [1, 2, 3]) {
          println val<caret>ue
        }
      """.trimIndent()
    )
    runReadActionBlocking {
      val element = myFixture.elementAtCaret as? GrParameter
      assertNotNull(element)
      assertType("java.lang.Integer", element!!.typeGroovy)
    }
  }

  @Test
  fun testInferKnownTypeForValueVariableWithIndexVariable() {
    myFixture.configureByText("a.groovy",
                            """
        for(int idx, int value in [1, 2, 3]) {
          println val<caret>ue
        }
      """.trimIndent()
    )
    runReadActionBlocking {

      val element = myFixture.elementAtCaret as? GrParameter
      assertNotNull(element)
      assertType("int", element!!.typeGroovy)
    }
  }

  @Test
  fun testInferUnknownTypeForValueVariableWithIndexVariable() {
    myFixture.configureByText("a.groovy",
                            """
        for(var idx, var value in [1, 2, 3]) {
          println val<caret>ue
        }
      """.trimIndent()
    )
    runReadActionBlocking {

      val element = myFixture.elementAtCaret as? GrParameter
      assertNotNull(element)
      assertType("java.lang.Integer", element!!.typeGroovy)
    }
  }

  @Test
  fun testInferKnownTypeForIndexVariable() {
    myFixture.configureByText("a.groovy",
      """
        for(Long idx, int value in [1, 2, 3]) {
          println id<caret>x
        }
      """.trimIndent()
    )
    runReadActionBlocking {

      val element = myFixture.elementAtCaret as? GrParameter
      assertNotNull(element)
      assertType("java.lang.Long", element!!.typeGroovy)
    }
  }

  @Test
  fun testInferUnknownTypeForIndexVariable() {
    myFixture.configureByText("a.groovy",
                            """
        for(var idx, var value in [1, 2, 3]) {
          println id<caret>x
        }
      """.trimIndent()
    )
    runReadActionBlocking {

      val element = myFixture.elementAtCaret as? GrParameter
      assertNotNull(element)
      assertType("int", element!!.typeGroovy)
    }
  }
}