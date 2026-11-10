// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion

import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class GroovySAMCompletionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
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

    assertTrue(lookupElements != null)
    assertEquals(2, lookupElements!!.size)
    for (element in lookupElements) {
      assertEquals("funWithOverloads", element.lookupString)
      assertTrue(element.`object` is GroovyResolveResult)
    }

  }
}