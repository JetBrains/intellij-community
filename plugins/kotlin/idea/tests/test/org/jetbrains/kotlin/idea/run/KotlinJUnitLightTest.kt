// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.lineMarker.RunLineMarkerProvider
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.idea.junit.JunitKotlinTestFrameworkProvider
import org.junit.Assert

class KotlinJUnitLightTest : LightJavaCodeInsightFixtureTestCase() {
    
    override fun setUp() {
        super.setUp()
        myFixture.addClass("package org.junit; public @interface Test {}");
    }

    fun testAvailableInsideAnonymous() {
        doTestObjectDeclaration(
            """
                      import org.junit.Test
                      class tests {
                          @Test
                          fun foo() {
                              val c  = object {
                                  fun bar() = sequence<Int> {
                                     <caret>
                                  }
                              }
                          }
                      }
                    """
        )
    }

    fun testAvailableInsideObject() {
        doTestObjectDeclaration(
            """
                      import org.junit.Test
                      object tests {
                          @Test
                          fun foo() {
                              <caret>
                          }
                      }
                    """
        )
        assertEquals(ThreeState.UNSURE, RunLineMarkerProvider.hadAnythingRunnable(myFixture.file.virtualFile))
        assertEquals(0, myFixture.findGuttersAtCaret().size)
        val gutters = myFixture.findAllGutters()
        assertEquals(2, gutters.size)
        assertEquals(ThreeState.YES, RunLineMarkerProvider.hadAnythingRunnable(myFixture.file.virtualFile))
    }

    private fun doTestObjectDeclaration(fileText: String) {
        val file = myFixture.configureByText(
            "tests.kt", fileText.trimIndent()
        )!!

        val element = file.findElementAt(myFixture.caretOffset)!!

        val location = PsiLocation(element)
        val context = ConfigurationContext.createEmptyContextForLocation(location)
        val contexts = context.configurationsFromContext
        Assert.assertEquals(1, contexts!!.size)
        val fromContext = contexts[0]
        assert(fromContext.configuration is JUnitConfiguration)
        val testObject = (fromContext.configuration as JUnitConfiguration).persistentData.TEST_OBJECT
        assert(testObject == JUnitConfiguration.TEST_METHOD) {
            "method should be suggested to run, but $testObject was used instead"
        }

        Assert.assertNotNull(JunitKotlinTestFrameworkProvider.getJavaTestEntity(element, checkMethod = true))
    }
}