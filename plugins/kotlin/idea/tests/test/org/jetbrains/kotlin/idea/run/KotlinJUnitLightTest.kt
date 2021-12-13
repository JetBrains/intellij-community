// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.junit2.info.MethodLocation
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties
import com.intellij.execution.lineMarker.RunLineMarkerProvider
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.idea.junit.JunitKotlinTestFrameworkProvider
import org.junit.Assert

class KotlinJUnitLightTest : LightJavaCodeInsightFixtureTestCase() {
    
    override fun setUp() {
        super.setUp()
        myFixture.addClass("package org.junit; public @interface Test {}");
        myFixture.addClass("package junit.framework; public class TestCase {}")
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


    fun testStackTraceParseerAcceptsJavaStacktrace() {
        myFixture.configureByText("tests.kt",
            """class tests : junit.framework.TestCase() {
  fun testMe() {
    doTest {
      assertTrue(false)
    }
  }

  private inline fun doTest(crossinline test: () -> Unit) {
    doTestInner {
      test()
    }
  }

  fun doTestInner(test: () -> Unit): Unit {
    test()
  }
}"""
        )
        val testProxy = SMTestProxy("testMe", false, "java:test://tests/testMe")
        testProxy.setTestFailed(
            "failure", """junit.framework.AssertionFailedError
	at junit.framework.Assert.fail(Assert.java:55)
	at junit.framework.Assert.assertTrue(Assert.java:22)
	at junit.framework.Assert.assertTrue(Assert.java:31)
	at junit.framework.TestCase.assertTrue(TestCase.java:200)
	at tests'$'testMe'$''$'inlined'$'doTest'$'1.invoke(tests.kt:19)
	at tests'$'testMe'$''$'inlined'$'doTest'$'1.invoke(tests.kt:1)
	at tests.doTestInner(tests.kt:16)
	at tests.testMe(tests.kt:19)""", true
        )
        val project = project
        val searchScope = GlobalSearchScope.projectScope(project)
        testProxy.locator = JavaTestLocator.INSTANCE
        val location = testProxy.getLocation(project, searchScope)
        assertInstanceOf(location, MethodLocation::class.java)
        val descriptor =
            testProxy.getDescriptor(location, JUnitConsoleProperties(JUnitConfiguration("p", getProject()), DefaultRunExecutor.getRunExecutorInstance()))
        assertInstanceOf(descriptor, OpenFileDescriptor::class.java)
        val fileDescriptor = descriptor as OpenFileDescriptor
        assertNotNull(fileDescriptor.file)
        assertEquals(49, fileDescriptor.offset)
    }
}