// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult

class GroovySAMCompletionTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  fun testContainsOverloadsWithClosure() {
    @Language("Groovy") val fileText = """
      interface SAM {
          void foo();
      }
      
      void funWithOverloads(SAM sam) {
          println "first"
      }
      
      void funWithOverloads(Closure cl) {
          println "second"
      }
      
      def main() {
          funWithOver<caret>
      }
    """.trimIndent()

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText)
    myFixture.completeBasic()
    val lookupElements = myFixture.lookupElements

    TestCase.assertTrue(lookupElements != null)
    TestCase.assertEquals(2, lookupElements!!.size)
    for (element in lookupElements) {
      TestCase.assertEquals("funWithOverloads", element.lookupString)
      TestCase.assertTrue(element.`object` is GroovyResolveResult)
    }

  }
}