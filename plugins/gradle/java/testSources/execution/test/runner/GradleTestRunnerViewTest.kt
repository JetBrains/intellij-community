// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTRunnerNodeDescriptor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ThrowableRunnable
import com.intellij.util.asSafely
import com.intellij.util.ui.tree.TreeUtil
import groovy.json.StringEscapeUtils.escapeJava
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.testFramework.util.buildscript
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test
import javax.swing.tree.DefaultMutableTreeNode

class GradleTestRunnerViewTest : GradleImportingTestCase() {

  private lateinit var buildViewTestFixture: BuildViewTestFixture

  override fun setUp() {
    super.setUp()
    buildViewTestFixture = BuildViewTestFixture(myProject)
    buildViewTestFixture.setUp()
    Registry.get("gradle.testLauncherAPI.enabled").setValue(true)
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { Registry.get("gradle.testLauncherAPI.enabled").setValue(false) },
      ThrowableRunnable { if (::buildViewTestFixture.isInitialized) buildViewTestFixture.tearDown() },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  @TargetVersions("5.0+")
  @Test
  fun `test grouping events of the same suite comes from different tasks`() {
    createProjectSubFile("src/test/java/my/pack/AppTest.java",
                         "package my.pack;\n" +
                         "import org.junit.Test;\n" +
                         "import static org.junit.Assert.fail;\n" +
                         "public class AppTest {\n" +
                         "    @Test\n" +
                         "    public void test() {\n" +
                         "        String prop = System.getProperty(\"prop\");\n" +
                         "        if (prop != null) {\n" +
                         "            fail(prop);\n" +
                         "        }\n" +
                         "    }\n" +
                         "}\n")

    importProject(buildscript {
      withJavaPlugin()
      withJUnit4()
      withTask("additionalTest", "Test") {
        assign("testClassesDirs", code("sourceSets.test.output.classesDirs"))
        assign("classpath", code("sourceSets.test.runtimeClasspath"))
        plusAssign("jvmArgs", "-Dprop='integ test error'")
      }
    })

    val treeStringPresentation = runTasksAndGetTestRunnerTree(listOf("clean", "test", "additionalTest"))

    assertEquals("-[root]\n" +
                 " -my.pack.AppTest\n" +
                 "  test\n" +
                 "  test",
                 treeStringPresentation.trim())

    buildViewTestFixture.assertBuildViewTreeEquals {
      assertThat(it)
        .startsWith("-\n" +
                    " -failed")
        .containsOnlyOnce("  -:additionalTest\n" +
                          "   There were failing tests. See the report at: ")
    }
  }

  @TargetVersions("2.14+")
  @Test
  fun `test console empty lines and output without eol at the end`() {
    val testOutputText = "test \noutput\n\ntext"
    createProjectSubFile("src/test/java/my/pack/AppTest.java",
                         """package my.pack;
                            import org.junit.Test;
                            import static org.junit.Assert.fail;
                            public class AppTest {
                                @Test
                                public void test() {
                                    System.out.println("${escapeJava(testOutputText)}");
                                }
                            }""".trimIndent())

    val scriptOutputText = "script \noutput\n\ntext\n"
    val scriptOutputTextWOEol = "text w/o eol"
    importProject(createBuildScriptBuilder()
                    .withJavaPlugin()
                    .withJUnit4()
                    .addBuildScriptPrefix("print(\"${escapeJava(scriptOutputText)}\")")
                    .addPostfix("print(\"${escapeJava(scriptOutputTextWOEol)}\")")
                    .generate())

    var testsExecutionConsole: GradleTestsExecutionConsole? = null
    maskExtensions(ExternalSystemExecutionConsoleManager.EP_NAME,
                   listOf(object : GradleTestsExecutionConsoleManager() {
                     override fun attachExecutionConsole(project: Project,
                                                         task: ExternalSystemTask,
                                                         env: ExecutionEnvironment?,
                                                         processHandler: ProcessHandler?): GradleTestsExecutionConsole? {
                       testsExecutionConsole = super.attachExecutionConsole(project, task, env, processHandler)
                       return testsExecutionConsole
                     }
                   }),
                   testRootDisposable)

    val settings = ExternalSystemTaskExecutionSettings().apply {
      externalProjectPath = projectPath
      taskNames = listOf("clean", "test")
      scriptParameters = "--quiet"
      externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }

    ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID,
                               myProject, GradleConstants.SYSTEM_ID, null,
                               ProgressExecutionMode.NO_PROGRESS_SYNC)

    val treeStringPresentation = runInEdtAndGet {
      val tree = testsExecutionConsole!!.resultsViewer.treeView!!
      TestConsoleProperties.HIDE_PASSED_TESTS.set(testsExecutionConsole!!.properties, false)
      PlatformTestUtil.expandAll(tree)
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.waitWhileBusy(tree)

      val testsRootNode = TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode) {
        val userObject = it.userObject
        userObject is SMTRunnerNodeDescriptor && userObject.element == testsExecutionConsole!!.resultsViewer.testsRootNode
      }
      TreeUtil.selectNode(tree, testsRootNode)
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.waitWhileBusy(tree)
      return@runInEdtAndGet PlatformTestUtil.print(tree, true)
    }

    assertEquals("-[[root]]\n" +
                 " -my.pack.AppTest\n" +
                 "  test",
                 treeStringPresentation.trim())

    val console = testsExecutionConsole!!.console as ConsoleViewImpl
    val consoleText = runInEdtAndGet {
      console.flushDeferredText()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      console.text
    }

    val consoleTextWithoutFirstTestingGreetingsLine: String
    if (consoleText.startsWith("Testing started at ")) {
      consoleTextWithoutFirstTestingGreetingsLine = consoleText.substringAfter("\n")
    } else {
      consoleTextWithoutFirstTestingGreetingsLine = consoleText
    }
    assertThat(consoleTextWithoutFirstTestingGreetingsLine).contains(testOutputText)
    val expectedText = if (SystemInfo.isWindows) {
      scriptOutputText + scriptOutputTextWOEol + "\n"
    } else {
      "script \n" +
      "output\n" +
      "text\n" +
      "text w/o eol\n"
    }
    assertEquals(expectedText, consoleTextWithoutFirstTestingGreetingsLine.substringBefore(testOutputText))
  }

  @Test
  fun `test build tw output for Gradle test runner execution`() {
    createProjectSubFile("src/test/java/my/pack/AppTest.java",
                         "package my.pack;\n" +
                         "import org.junit.Test;\n" +
                         "public class AppTest {\n" +
                         "    @Test\n" +
                         "    public void test() {}\n" +
                         "}\n")

    importProject(createBuildScriptBuilder()
                    .withJavaPlugin()
                    .withJUnit4()
                    .generate())

    val testRunnerTree = runTasksAndGetTestRunnerTree(listOf("clean", "test"))

    assertEquals("-[root]\n" +
                 " -my.pack.AppTest\n" +
                 "  test",
                 testRunnerTree.trim())

    buildViewTestFixture.assertBuildViewTreeEquals(
      """
      -
       -successful
        :clean
        :compileJava
        :processResources
        :classes
        :compileTestJava
        :processTestResources
        :testClasses
        :test
      """.trimIndent()
    )
  }

  @TargetVersions("5.6+")
  @Test
  fun `navigation for unrolled spock 2 tests`() {
    createProjectSubFile("src/test/groovy/HelloSpockSpec.groovy", """
      import spock.lang.Specification

      class HelloSpockSpec extends Specification {

          def "length of #name is #length"() {
              expect:
              name.size() != length

              where:
              name     | length
              "Spock"  | 5
          }
      }
    """.trimIndent())

    importProject {
      withGroovyPlugin("3.0.0")

      addTestImplementationDependency(call("platform", "org.spockframework:spock-bom:2.1-groovy-3.0"))
      addTestImplementationDependency("org.spockframework:spock-core:2.1-groovy-3.0")

      withJUnit5()
    }

    val console = getTestExecutionConsole(listOf("clean", "test"))
    val root = console.resultsViewer.root
    val classChild = root.children.single()
    assertEquals("HelloSpockSpec", classChild.name)

    fun AbstractTestProxy.resolveToMethod() : PsiMethod = getLocation(myProject, GlobalSearchScope.allScope(myProject)).psiElement.asSafely<PsiMethod>()!!

    runReadAction {
      val testNodeChild = classChild.children.single()
      assertEquals("length of #name is #length", testNodeChild.resolveToMethod().name)
      val failedTestChild = testNodeChild.children.single()
      assertEquals("length of #name is #length", failedTestChild.resolveToMethod().name)
    }

  }

  private fun getTestExecutionConsole(tasks: List<String>): GradleTestsExecutionConsole {
    var testsExecutionConsole: GradleTestsExecutionConsole? = null
    maskExtensions(ExternalSystemExecutionConsoleManager.EP_NAME,
                   listOf(object : GradleTestsExecutionConsoleManager() {
                     override fun attachExecutionConsole(project: Project,
                                                         task: ExternalSystemTask,
                                                         env: ExecutionEnvironment?,
                                                         processHandler: ProcessHandler?): GradleTestsExecutionConsole? {
                       testsExecutionConsole = super.attachExecutionConsole(project, task, env, processHandler)
                       return testsExecutionConsole
                     }
                   }),
                   testRootDisposable)

    val settings = ExternalSystemTaskExecutionSettings().apply {
      externalProjectPath = projectPath
      taskNames = tasks
      externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }

    ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID,
                               myProject, GradleConstants.SYSTEM_ID, null,
                               ProgressExecutionMode.NO_PROGRESS_SYNC)

    return testsExecutionConsole!!
  }

  private fun runTasksAndGetTestRunnerTree(tasks: List<String>): String {
    val console = getTestExecutionConsole(tasks)
    return runInEdtAndGet {
      val tree = console.resultsViewer.treeView!!
      TestConsoleProperties.HIDE_PASSED_TESTS.set(console.properties, false)
      PlatformTestUtil.expandAll(tree)
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.waitWhileBusy(tree)
      return@runInEdtAndGet PlatformTestUtil.print(tree, false)
    }
  }
}
