// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.GrArrayInitializer

class GroovyArrayInitializerTypeInferenceTest : LightGroovyTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

  fun testOuterArrayInitializer() {
    fixture.configureByText("A.groovy", """
     def a = new String[][]{<caret> {}, {"foo"}}
    """)
    val brace = myFixture.file.findElementAt(fixture.caretOffset)
    assertNotNull(brace)
    val arrayInitializer =  brace?.parent as? GrArrayInitializer
    assertNotNull(arrayInitializer)
    assertType("java.lang.String[][]", arrayInitializer!!.type)
  }

  fun testNestedArrayInitializer() {
    fixture.configureByText("A.groovy", """
     def a = new String[][]{{<caret>}, {"foo"}}
    """)
    val brace = myFixture.file.findElementAt(fixture.caretOffset)
    assertNotNull(brace)
    val arrayInitializer =  brace?.parent as? GrArrayInitializer
    assertNotNull(arrayInitializer)
    assertType("java.lang.String[]", arrayInitializer!!.type)
  }
}