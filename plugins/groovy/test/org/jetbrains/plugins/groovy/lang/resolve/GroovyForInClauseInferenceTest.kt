// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter

class GroovyForInClauseInferenceTest : LightGroovyTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

  fun testInferKnownTypeForValueVariable() {
    fixture.configureByText("a.groovy",
      """
        for(int value in 1..10) {
          println val<caret>ue
        }
      """.trimIndent()
    )
    val element = fixture.elementAtCaret as? GrParameter
    assertNotNull(element)
    assertType("int", element!!.typeGroovy)
  }

  fun testInferUnknownTypeForValueVariable() {
    fixture.configureByText("a.groovy",
                            """
        for(var value in [1, 2, 3]) {
          println val<caret>ue
        }
      """.trimIndent()
    )
    val element = fixture.elementAtCaret as? GrParameter
    assertNotNull(element)
    assertType("java.lang.Integer", element!!.typeGroovy)
  }


  fun testInferKnownTypeForValueVariableWithIndexVariable() {
    fixture.configureByText("a.groovy",
                            """
        for(int idx, int value in [1, 2, 3]) {
          println val<caret>ue
        }
      """.trimIndent()
    )

    val element = fixture.elementAtCaret as? GrParameter
    assertNotNull(element)
    assertType("int", element!!.typeGroovy)
  }

  fun testInferUnknownTypeForValueVariableWithIndexVariable() {
    fixture.configureByText("a.groovy",
                            """
        for(var idx, var value in [1, 2, 3]) {
          println val<caret>ue
        }
      """.trimIndent()
    )

    val element = fixture.elementAtCaret as? GrParameter
    assertNotNull(element)
    assertType("java.lang.Integer", element!!.typeGroovy)
  }

  fun testInferKnownTypeForIndexVariable() {
    fixture.configureByText("a.groovy",
      """
        for(Long idx, int value in [1, 2, 3]) {
          println id<caret>x
        }
      """.trimIndent()
    )

    val element = fixture.elementAtCaret as? GrParameter
    assertNotNull(element)
    assertType("java.lang.Long", element!!.typeGroovy)
  }

  fun testInferUnknownTypeForIndexVariable() {
    fixture.configureByText("a.groovy",
                            """
        for(var idx, var value in [1, 2, 3]) {
          println id<caret>x
        }
      """.trimIndent()
    )

    val element = fixture.elementAtCaret as? GrParameter
    assertNotNull(element)
    assertType("int", element!!.typeGroovy)
  }
}