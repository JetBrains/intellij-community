// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.execution.PsiLocation
import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.JavaRunConfigurationModule
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.junit2.info.MethodLocation
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties
import com.intellij.execution.lineMarker.RunLineMarkerProvider
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.junit.JunitKotlinTestFrameworkProvider
import org.jetbrains.kotlin.psi.KtFunction
import org.junit.Assert

class KotlinJUnitLightTest : LightJavaCodeInsightFixtureTestCase() {
    private val tempSettings: MutableSet<RunnerAndConfigurationSettings> = HashSet()

    @Throws(Exception::class)
    override fun tearDown() {
        try {
            val runManager = getInstance(project)
            for (setting in tempSettings) {
                runManager.removeConfiguration(setting)
            }
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }
    
    override fun setUp() {
        super.setUp()
        myFixture.addClass("package junit.framework; public class TestCase {}")
        myFixture.addClass("package org.junit; public @interface Test {}")
        myFixture.addClass("package org.junit.platform.commons.annotation; public @interface Testable{}")
        myFixture.addClass("package org.junit.jupiter.api; import org.junit.platform.commons.annotation.Testable; @Testable public @interface Test {}")
        myFixture.addClass("package org.junit.jupiter.api; public @interface Nested {}")
        myFixture.addClass("package org.junit.jupiter.api; public @interface BeforeEach {}")
    }

    fun testAvailableInsideAnonymous() {
        doTestMethodConfiguration(
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

    fun testNoAvailableInsideObject() {
        myFixture.configureByText(
            "tests.kt",
            """
                      import org.junit.Test
                      object tests {
                          @Test
                          fun foo() {
                              <caret>
                          }
                      }
                    """.trimIndent()
        )
        assertEquals(ThreeState.UNSURE, RunLineMarkerProvider.hadAnythingRunnable(myFixture.file.virtualFile))
        assertEquals(0, myFixture.findGuttersAtCaret().size)
        val gutters = myFixture.findAllGutters()
        assertEquals(0, gutters.size)
        assertEquals(ThreeState.NO, RunLineMarkerProvider.hadAnythingRunnable(myFixture.file.virtualFile))
    }

    fun testBackticksInNames() {
        doTestMethodConfiguration(
            """
            import org.junit.jupiter.api.Test
            import org.junit.jupiter.api.Nested
            class `tests with spaces` {
                  class `nested with spaces` {
                      @Test
                      fun `with spaces`() {
                          <caret>
                      }
                  }
            }
            """
        )
    }

    private fun doTestMethodConfiguration(fileText: String, checkConfiguration: Boolean = true) {
        val file = myFixture.configureByText(
            "tests.kt", fileText.trimIndent()
        )!!

        val element = file.findElementAt(myFixture.caretOffset)!!

        val location = PsiLocation(element)
        val context = ConfigurationContext.createEmptyContextForLocation(location)
        val contexts = context.configurationsFromContext
        assertEquals(1, contexts?.size ?: 0)
        val fromContext = contexts?.get(0)
        assert(fromContext?.configuration is JUnitConfiguration)
        val configuration = fromContext?.configuration as JUnitConfiguration
        val testObject = configuration.persistentData.TEST_OBJECT
        assert(testObject == JUnitConfiguration.TEST_METHOD) {
            "method should be suggested to run, but $testObject was used instead"
        }

        Assert.assertNotNull(JunitKotlinTestFrameworkProvider.getInstance().getJavaTestEntity(element, checkMethod = true))
        if (checkConfiguration) {
            configuration.workingDirectory = FileUtil.getTempDirectory()
            configuration.checkConfiguration()
        }
    }


    fun testPatternConfiguration() {
        doTestMethodConfiguration(
            """
            import junit.framework.TestCase
            abstract class Test : TestCase() {
              fun te<caret>st1() {}
              fun test2() {}
            
              class TestX : Test()
              class TestY : Test()
            }
        """, false
        )
    }

    fun testIsConfiguredPattern() {
        val file = myFixture.configureByText(
            "tests.kt", """
                import org.junit.Test
                class TestOne {
                <caret>
                    @Test fun testOneA() {}
                    @Test fun testOneB() {}
                }
                
                class TestTwo {
                    @Test fun testTwo() {}
                }
        """)
        val manager = getInstance(project)
        val test = JUnitConfiguration("patterns", project)
        test.bePatternConfiguration(((file as PsiClassOwner).classes).toList(), null)
        val settings = RunnerAndConfigurationSettingsImpl(manager as RunManagerImpl, test)
        manager.addConfiguration(settings)
        tempSettings.add(settings)
        
        val element = file.findElementAt(myFixture.caretOffset)!!

        val location = PsiLocation(element)
        val context = ConfigurationContext.createEmptyContextForLocation(location)
        val contexts = context.configurationsFromContext
        Assert.assertEquals(1, contexts!!.size)
        val fromContext = contexts[0]
        assert(fromContext.configuration is JUnitConfiguration)
        val testObject = (fromContext.configuration as JUnitConfiguration).persistentData.TEST_OBJECT
        assert(testObject == JUnitConfiguration.TEST_CLASS) {
            "method should be suggested to run, but $testObject was used instead"
        }
    }

    fun testTestClassWithMain() {
        doTestClassWithMain(null)
    }

    fun testTestClassWithMainTestConfigurationExists() {
        doTestClassWithMain {
            val manager = getInstance(project)
            val test = KotlinRunConfiguration("ATestKt", JavaRunConfigurationModule(project, true), KotlinRunConfigurationType.instance)
            test.runClass = "ATestKt"
            val settings = RunnerAndConfigurationSettingsImpl((manager as RunManagerImpl), test)
            manager.addConfiguration(settings)
            tempSettings.add(settings)
        }
    }
    
    private fun doTestClassWithMain(setupExisting: Runnable?) {
        myFixture.configureByText(
            "ATest.kt", """import org.junit.Test
class AT<caret>est {
    @Test fun t(){}
}
fun main(args: Array<String>) {}
"""
        )
        setupExisting?.run()
        val marks = myFixture.findGuttersAtCaret()
        assertEquals(1, marks.size)
        val mark = marks[0] as GutterIconRenderer
        val group = mark.popupMenuActions as ActionGroup
        assertNotNull(group)
        val presentations = PresentationFactory()
        val dataContext = TestActionEvent.createTestEvent().dataContext
        val children = Utils.expandActionGroup(
            group, presentations, dataContext, ActionPlaces.EDITOR_GUTTER_POPUP, ActionUiKind.POPUP)
        val list = children.filter {
            presentations.getPresentation(it).text.run {
                startsWith("Run '") && endsWith("'")
            }
        }
        assertEquals(list.toString(), 1, list.size)
        assertEquals("Run 'ATest'", presentations.getPresentation(list[0]).text)
        myFixture.testAction(list[0])
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        val selectedConfiguration = getInstance(project).selectedConfiguration
        tempSettings.add(selectedConfiguration!!)
        assertEquals("ATest", selectedConfiguration.name)
    }


    fun testStackTraceParserAcceptsJavaStacktrace() {
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

    fun `test unused beforeAll`() {
        myFixture.addClass("package org.junit.jupiter.api; public @interface BeforeAll{}")
        myFixture.addClass("package kotlin.jvm; public @interface JvmStatic{}")
        myFixture.configureByText("Demo.kt", """
import org.junit.jupiter.api.BeforeAll

class DemoTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUpMock<caret>Server() {
            println("Before")
        }
    }
}""")
        val ktFunction = PsiTreeUtil.getParentOfType(myFixture.elementAtCaret, KtFunction::class.java, false)
        assertTrue(UnusedDeclarationInspection().isEntryPoint(ktFunction!!.toLightMethods()[0]))
    }

    fun testMethodWithTestAnnotation() {
        val file = myFixture.configureByText(
            "MyTest.kt", """
            class MyTest {
                @org.junit.jupiter.api.Test
                @Retention(AnnotationRetention.RUNTIME)
                annotation class TestAnnotation

                @TestAnnotation
                fun `dispatch <caret>thread`() {}
            }
            """.trimIndent()
        )

        val gutters = myFixture.findGuttersAtCaret()
        Assert.assertTrue("Test method with meta-annotation should have a gutter icon", gutters.isNotEmpty())

        val element = file.findElementAt(myFixture.caretOffset)!!
        val location = PsiLocation(element)
        val context = ConfigurationContext.createEmptyContextForLocation(location)
        val contexts = context.configurationsFromContext

        Assert.assertEquals(1, contexts?.size ?: 0)
        val fromContext = contexts?.get(0)
        Assert.assertTrue(fromContext?.configuration is JUnitConfiguration)
        val configuration = fromContext?.configuration as JUnitConfiguration

        Assert.assertEquals("MyTest.dispatch thread", configuration.name)

        val testObject = configuration.persistentData.TEST_OBJECT
        Assert.assertEquals(
            "Method should be suggested to run, but $testObject was used instead",
            JUnitConfiguration.TEST_METHOD,
            testObject
        )

        Assert.assertNotNull(JunitKotlinTestFrameworkProvider.getInstance().getJavaTestEntity(element, checkMethod = true))
    }

    fun testMethodWithTestAnnotationAndBeforeEach() {
        myFixture.addClass("""
            package c;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;

            @org.junit.jupiter.api.Test
            @Retention(RetentionPolicy.RUNTIME)
            public @interface TestAnnotation {
            }
        """)

        val file = myFixture.configureByText(
            "MyTest.kt", """

            import c.TestAnnotation
            import org.junit.jupiter.api.BeforeEach

            class MyTest {          
                @BeforeEach
                fun cleanEDTQueue() {}

                @TestAnnotation
                fun `dispatch <caret>thread`() {}
            }
            """.trimIndent()
        )

        val gutters = myFixture.findGuttersAtCaret()
        Assert.assertTrue("Test method with meta-annotation should have a gutter icon", gutters.isNotEmpty())

        val element = file.findElementAt(myFixture.caretOffset)!!
        val location = PsiLocation(element)
        val context = ConfigurationContext.createEmptyContextForLocation(location)
        val contexts = context.configurationsFromContext

        Assert.assertEquals(1, contexts?.size ?: 0)
        val fromContext = contexts?.get(0)
        Assert.assertTrue(fromContext?.configuration is JUnitConfiguration)
        val configuration = fromContext?.configuration as JUnitConfiguration

        Assert.assertEquals("MyTest.dispatch thread", configuration.name)

        val testObject = configuration.persistentData.TEST_OBJECT
        Assert.assertEquals(
            "Method should be suggested to run, but $testObject was used instead",
            JUnitConfiguration.TEST_METHOD,
            testObject
        )
        Assert.assertNotNull(JunitKotlinTestFrameworkProvider.getInstance().getJavaTestEntity(element, checkMethod = true))
    }
}