// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.producer

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.junit2.PsiMemberParameterizedLocation
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiMethod
import junit.framework.TestCase
import org.jetbrains.plugins.gradle.execution.test.runner.*
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.settings.TestRunner
import org.jetbrains.plugins.gradle.util.runReadActionAndWait
import org.junit.Test

class GradleTestRunConfigurationProducerTest : GradleTestRunConfigurationProducerTestCase() {

  @Test
  fun `test simple configuration`() {
    val projectData = generateAndImportTemplateProject()
    assertConfigurationFromContext<TestMethodGradleConfigurationProducer>(
      """:test --tests "TestCase.test1"""",
      projectData["project"]["TestCase"]["test1"].element
    )
    assertConfigurationFromContext<TestClassGradleConfigurationProducer>(
      """:test --tests "TestCase"""",
      projectData["project"]["TestCase"].element
    )
    assertConfigurationFromContext<AllInPackageGradleConfigurationProducer>(
      """:test --tests "pkg.*"""",
      runReadActionAndWait { projectData["project"]["pkg.TestCase"].element.containingFile.containingDirectory }
    )
  }

  @Test
  fun `test pattern configuration`() {
    val projectData = generateAndImportTemplateProject()
    assertConfigurationFromContext<PatternGradleConfigurationProducer>(
      """:test --tests "TestCase.test1" --tests "pkg.TestCase.test1" """ +
      """:module:test --tests "ModuleTestCase.test1" --continue""",
      projectData["project"]["TestCase"]["test1"].element,
      projectData["project"]["pkg.TestCase"]["test1"].element,
      projectData["module"]["ModuleTestCase"]["test1"].element
    )
    assertConfigurationFromContext<PatternGradleConfigurationProducer>(
      """:test --tests "TestCase" --tests "pkg.TestCase" """ +
      """:module:test --tests "ModuleTestCase" --continue""",
      projectData["project"]["TestCase"].element,
      projectData["project"]["pkg.TestCase"].element,
      projectData["module"]["ModuleTestCase"].element
    )
    assertConfigurationFromContext<PatternGradleConfigurationProducer>(
      """:test --tests "TestCase.test1" --tests "pkg.TestCase.test1" """ +
      """:module:test --tests "ModuleTestCase" --continue""",
      projectData["project"]["TestCase"]["test1"].element,
      projectData["project"]["pkg.TestCase"]["test1"].element,
      projectData["module"]["ModuleTestCase"].element
    )
  }

  @Test
  fun `test configuration producer properly matches configuration with context`() {
    val projectData = generateAndImportTemplateProject()
    runReadActionAndWait {
      val locations = listOf(projectData["project"]["TestCase"]["test1"].element,
                             projectData["project"]["TestCase"].element,
                             projectData["project"]["pkg.TestCase"].element.containingFile.containingDirectory,
                             projectData["project"]["TestCase"]["test2"].element)

      val contexts = locations.map { getContextByLocation(it) } // with duplicate locations
      val cfgFromCtx = contexts.map { getConfigurationFromContext(it) }
      val configurations = cfgFromCtx.map { it.configuration as GradleRunConfiguration }
      val methodProd = getConfigurationProducer<TestMethodGradleConfigurationProducer>()
      val classProd = getConfigurationProducer<TestClassGradleConfigurationProducer>()
      val packageProd = getConfigurationProducer<AllInPackageGradleConfigurationProducer>()
      val directoryProd = getConfigurationProducer<AllInDirectoryGradleConfigurationProducer>()

      for ((j, configuration) in configurations.withIndex()) {
        for ((k, context) in contexts.withIndex()) {
          val cfgMatchesContext = j == k
          when (context.psiLocation) {
            is PsiMethod -> assertEquals(cfgMatchesContext, methodProd.isConfigurationFromContext(configuration, context))
            is PsiClass -> assertEquals(cfgMatchesContext, classProd.isConfigurationFromContext(configuration, context))
            is PsiDirectory -> assertEquals(cfgMatchesContext, packageProd.isConfigurationFromContext(configuration, context))
          }
        }
      }

      val context = getContextByLocation(*locations.toTypedArray())
      assertFalse(packageProd.isConfigurationFromContext(configurations[2], context))
      assertFalse(directoryProd.isConfigurationFromContext(configurations[2], context))
    }
  }

  @Test
  fun `test configuration escaping`() {
    val projectData = generateAndImportTemplateProject()
    assertConfigurationFromContext<TestMethodGradleConfigurationProducer>(
      """':my module:test' --tests "MyModuleTestCase.test1"""",
      projectData["my module"]["MyModuleTestCase"]["test1"].element
    )
    assertConfigurationFromContext<TestClassGradleConfigurationProducer>(
      """':my module:test' --tests "MyModuleTestCase"""",
      projectData["my module"]["MyModuleTestCase"].element
    )
    assertConfigurationFromContext<PatternGradleConfigurationProducer>(
      """':my module:test' --tests "MyModuleTestCase.test1" --tests "MyModuleTestCase.test2"""",
      projectData["my module"]["MyModuleTestCase"]["test1"].element,
      projectData["my module"]["MyModuleTestCase"]["test2"].element
    )
    assertConfigurationFromContext<TestMethodGradleConfigurationProducer>(
      """:test --tests "GroovyTestCase.Don\'t use single quo\*tes"""",
      projectData["project"]["GroovyTestCase"]["""Don\'t use single quo\"tes"""].element
    )
    assertConfigurationFromContext<PatternGradleConfigurationProducer>(
      """:test --tests "GroovyTestCase.Don\'t use single quo\*tes" --tests "GroovyTestCase.test2"""",
      projectData["project"]["GroovyTestCase"]["""Don\'t use single quo\"tes"""].element,
      projectData["project"]["GroovyTestCase"]["test2"].element
    )
  }

  @Test
  fun `test configuration different tasks in single module`() {
    currentExternalProjectSettings.isResolveModulePerSourceSet = false
    val projectData = generateAndImportTemplateProject()
    assertConfigurationFromContext<PatternGradleConfigurationProducer>(
      """:test --tests "TestCase" :autoTest --tests "AutomationTestCase" --continue""",
      projectData["project"]["TestCase"].element,
      projectData["project"]["AutomationTestCase"].element,
      testTasksFilter = { it in setOf("test", "autoTest") }
    )
  }

  @Test
  fun `test configuration tests for directory`() {
    val projectData = generateAndImportTemplateProject()
    assertConfigurationFromContext<AllInDirectoryGradleConfigurationProducer>(
      """:autoTest :automationTest :test --continue""",
      projectData["project"].root
    )
    assertConfigurationFromContext<AllInDirectoryGradleConfigurationProducer>(
      """:test""",
      projectData["project"].root.subDirectory("src")
    )
    assertConfigurationFromContext<AllInDirectoryGradleConfigurationProducer>(
      """:test""",
      projectData["project"].root.subDirectory("src", "test")
    )
    assertConfigurationFromContext<AllInDirectoryGradleConfigurationProducer>(
      """:test""",
      projectData["project"].root.subDirectory("src", "test", "java")
    )
    assertConfigurationFromContext<AllInPackageGradleConfigurationProducer>(
      """:test --tests "pkg.*"""",
      projectData["project"].root.subDirectory("src", "test", "java", "pkg")
    )
    assertConfigurationFromContext<AllInDirectoryGradleConfigurationProducer>(
      """:autoTest :automationTest --continue""",
      projectData["project"].root.subDirectory("automation")
    )
    assertConfigurationFromContext<AllInDirectoryGradleConfigurationProducer>(
      """:module:test""",
      projectData["module"].root
    )
  }

  @Test
  fun `test producer choosing per run`() {
    currentExternalProjectSettings.isResolveModulePerSourceSet = false
    currentExternalProjectSettings.testRunner = TestRunner.CHOOSE_PER_TEST
    val projectData = generateAndImportTemplateProject()
    assertProducersFromContext(
      projectData["project"].root,
      "AllInPackageConfigurationProducer",
      "AllInDirectoryGradleConfigurationProducer"
    )
    assertProducersFromContext(
      projectData["project"].root.subDirectory("src"),
      "AllInDirectoryGradleConfigurationProducer"
    )
    assertProducersFromContext(
      projectData["project"].root.subDirectory("src", "test"),
      "AllInDirectoryGradleConfigurationProducer"
    )
    assertProducersFromContext(
      projectData["project"].root.subDirectory("src", "test", "java"),
      "AbstractAllInDirectoryConfigurationProducer",
      "AllInDirectoryGradleConfigurationProducer"
    )
    assertProducersFromContext(
      projectData["project"].root.subDirectory("src", "test", "java", "pkg"),
      "AbstractAllInDirectoryConfigurationProducer",
      "AllInPackageGradleConfigurationProducer"
    )
    assertProducersFromContext(
      projectData["project"].root.subDirectory("automation"),
      "AbstractAllInDirectoryConfigurationProducer",
      "AllInDirectoryGradleConfigurationProducer"
    )
    assertProducersFromContext(
      projectData["module"].root,
      "AllInPackageConfigurationProducer",
      "AllInDirectoryGradleConfigurationProducer"
    )
  }

  @Test
  fun `test execution action children`() {
    val projectData = generateAndImportTemplateProject()
    assertGutterRunActionsSize(projectData["project"]["TestCase"].element, 0)
    assertGutterRunActionsSize(projectData["project"]["TestCase"]["test1"].element, 0)
    assertGutterRunActionsSize(projectData["project"]["org.example.TestCaseWithMain"].element, 0)
    assertGutterRunActionsSize(projectData["project"]["org.example.TestCaseWithMain"]["test2"].element, 0)
  }

  @Test
  fun `test execution action children in choose per test mode`() {
    currentExternalProjectSettings.testRunner = TestRunner.CHOOSE_PER_TEST
    val projectData = generateAndImportTemplateProject()
    assertGutterRunActionsSize(projectData["project"]["TestCase"].element, 2)
    assertGutterRunActionsSize(projectData["project"]["TestCase"]["test1"].element, 2)
    //assertGutterRunActionsSize(projectData["project"]["org.example.TestCaseWithMain"].element, 2)
    assertGutterRunActionsSize(projectData["project"]["org.example.TestCaseWithMain"]["test2"].element, 2)
  }

  @Test
  fun `test multiple selected abstract tests`() {
    val projectData = generateAndImportTemplateProject()
    runReadActionAndWait {
      val producer = getConfigurationProducer<PatternGradleConfigurationProducer>()
      val testClass = projectData["project"]["TestCase"].element
      val abstractTestClass = projectData["project"]["org.example.AbstractTestCase"].element
      val abstractTestMethod = projectData["project"]["org.example.AbstractTestCase"]["test"].element
      val templateConfiguration = producer.createTemplateConfiguration()
      getContextByLocation(testClass, abstractTestClass).let {
        assertTrue(producer.setupConfigurationFromContext(templateConfiguration, it, Ref(it.psiLocation)))
      }
      getContextByLocation(abstractTestClass, abstractTestMethod).let {
        assertFalse(producer.setupConfigurationFromContext(templateConfiguration, it, Ref(it.psiLocation)))
      }
      getContextByLocation(abstractTestClass, abstractTestClass).let {
        assertFalse(producer.setupConfigurationFromContext(templateConfiguration, it, Ref(it.psiLocation)))
      }
      getContextByLocation(abstractTestMethod, abstractTestMethod).let {
        assertFalse(producer.setupConfigurationFromContext(templateConfiguration, it, Ref(it.psiLocation)))
      }
    }
  }

  @Test
  fun `test template-defined arguments are kept`() {
    val projectData = generateAndImportTemplateProject()
    val gradleRCTemplate = RunManager.getInstance(myProject).getConfigurationTemplate(
      GradleExternalTaskConfigurationType.getInstance().factory).configuration as? GradleRunConfiguration

    gradleRCTemplate?.settings?.scriptParameters = "-DmyKey=myVal --debug"

    try {
      assertConfigurationFromContext<TestMethodGradleConfigurationProducer>(
        """:test --tests "TestCase.test1" -DmyKey=myVal --debug""",
        projectData["project"]["TestCase"]["test1"].element
      )
      assertConfigurationFromContext<TestClassGradleConfigurationProducer>(
        """:test --tests "TestCase" -DmyKey=myVal --debug""",
        projectData["project"]["TestCase"].element
      )
      assertConfigurationFromContext<AllInPackageGradleConfigurationProducer>(
        """:test --tests "pkg.*" -DmyKey=myVal --debug""",
        runReadActionAndWait { projectData["project"]["pkg.TestCase"].element.containingFile.containingDirectory }
      )
    } finally {
      gradleRCTemplate?.settings?.scriptParameters = ""
    }
  }

  @Test
  fun `test configuration from concrete class with inherited test`() {
    val projectData = generateAndImportTemplateProject()
    assertConfigurationFromContext<TestMethodGradleConfigurationProducer>(
      """:test --tests "TestCase.test"""", {ConfigurationContext.createEmptyContextForLocation(
      PsiMemberParameterizedLocation(myProject, projectData["project"]["org.example.AbstractTestCase"]["test"].element,
                                     projectData["project"]["TestCase"].element, "")
      )}
    )
  }

  @Test
  fun `test run configuration command line partition`() {
    createAndAddRunConfiguration("task1 task2").apply {
      assertSameElements(settings.taskNames, "task1", "task2")
      assertEmpty(settings.scriptParameters)
      assertEmpty(settings.vmOptions)
    }
    createAndAddRunConfiguration("task1 task2 --debug").apply {
      assertSameElements(settings.taskNames, "task1", "task2")
      assertEquals(settings.scriptParameters, "--debug")
      assertEmpty(settings.vmOptions)
    }
    createAndAddRunConfiguration("task1 task2 --info").apply {
      assertSameElements(settings.taskNames, "task1", "task2")
      assertEquals(settings.scriptParameters, "--info")
      assertEmpty(settings.vmOptions)
    }
    createAndAddRunConfiguration("--info -PmyKey=myVal -DmyKey=myVal").apply {
      assertEmpty(settings.taskNames)
      assertEquals(settings.scriptParameters, "--info -PmyKey=myVal -DmyKey=myVal")
      assertEmpty(settings.vmOptions)
    }
    createAndAddRunConfiguration("my-Doc -DmyKey=myVal", vmOptions = "-ea -lorem").apply {
      assertSameElements(settings.taskNames, "my-Doc")
      assertEquals(settings.scriptParameters, "-DmyKey=myVal")
      assertEquals(settings.vmOptions, "-ea -lorem")
    }
    createAndAddRunConfiguration("""test1 --tests Test test2 --tests="My test --debug" --continue --info""").apply {
      assertSameElements(settings.taskNames, "test1", "--tests", "Test", "test2", """--tests="My test --debug"""")
      assertEquals(settings.scriptParameters, "--continue --info")
      assertEmpty(settings.vmOptions)
    }
    createAndAddRunConfiguration("--info task --ipsum --stacktrace --'dolor sit amet'").apply {
      assertSameElements(settings.taskNames, "task", "--ipsum", "--'dolor sit amet'")
      assertEquals(settings.scriptParameters, "--info --stacktrace")
      assertEmpty(settings.vmOptions)
    }
    createAndAddRunConfiguration("build -x test").apply {
      assertSameElements(settings.taskNames, "build")
      assertEquals(settings.scriptParameters, "-x test")
      assertEmpty(settings.vmOptions)
    }
  }

  @Test
  fun `test reusing configuration with options`() {
    val projectData = generateAndImportTemplateProject()
    val projectElement = projectData["project"].root
    createAndAddRunConfiguration("build test --info -PmyKey=myVal -DmyKey=myVal")
    createAndAddRunConfiguration("build --info -PmyKey=myVal -DmyKey=myVal")
    createAndAddRunConfiguration("test --debug -PmyKey=myVal -DmyKey=myVal")
    assertConfigurationForTask("build --info -PmyKey=myVal -DmyKey=myVal", "build", projectElement)
    assertConfigurationForTask("test --debug -PmyKey=myVal -DmyKey=myVal", "test", projectElement)
  }

  @Test
  fun `test configurations are not from context`() {
    val projectData = generateAndImportTemplateProject()
    createAndAddRunConfiguration("""build :test --tests "TestCase"""").let { configuration ->
      runReadActionAndWait {
        val context = getContextByLocation(projectData["project"]["TestCase"].element)
        val producer = getConfigurationProducer<TestClassGradleConfigurationProducer>()
        assertFalse(producer.isConfigurationFromContext(configuration, context))
      }
    }
    createAndAddRunConfiguration("""a b c d e f :test --tests "TestCase"""").let { configuration ->
      runReadActionAndWait {
        val context = getContextByLocation(projectData["project"]["TestCase"].element)
        val producer = getConfigurationProducer<TestClassGradleConfigurationProducer>()
        assertFalse(producer.isConfigurationFromContext(configuration, context))
      }
    }
    createAndAddRunConfiguration("""build :test --tests "TestCase.test1"""").let { configuration ->
      runReadActionAndWait {
        val context = getContextByLocation(projectData["project"]["TestCase"]["test1"].element)
        val producer = getConfigurationProducer<TestMethodGradleConfigurationProducer>()
        assertFalse(producer.isConfigurationFromContext(configuration, context))
      }
    }
    createAndAddRunConfiguration("""a b c d e f :test --tests "TestCase.test1"""").let { configuration ->
      runReadActionAndWait {
        val context = getContextByLocation(projectData["project"]["TestCase"]["test1"].element)
        val producer = getConfigurationProducer<TestMethodGradleConfigurationProducer>()
        assertFalse(producer.isConfigurationFromContext(configuration, context))
      }
    }
  }

  @Test
  fun `test configurations not from context do not force test run`() {
    generateAndImportTemplateProject()
    createAndAddRunConfiguration("test verify").let { configuration ->
      TestCase.assertFalse(configuration.isRunAsTest)
    }
  }

  @Test
  fun `test cannot create configurationFromContext when no test sources are available`() {
    val projectData = generateAndImportTemplateProject()

    createProjectSubFile("src/java/org/example/application/ExampleMain.java", """
      package org.example.application;
      public class ExampleMain {
        public static void main(String[] args) {}
      }
    """.trimIndent())

    runReadActionAndWait {
      val contextFromMain = getContextByLocation(projectData["project"].root.subDirectory("src", "java"))
      // Verify that there is no configurationFromContext available when checking from /src/java/ directory and this is because
      // there are no test sources under /src/java.
      val fromMainContext = contextFromMain.configurationsFromContext?.firstOrNull()
      TestCase.assertTrue(fromMainContext == null)

      val contextFromTest = getContextByLocation(projectData["project"].root.subDirectory("src", "test"))
      // Verify that there is a configurationFromContext available when checking from /src/test/ as it contains test sources.
      val fromTestContext = contextFromTest.configurationsFromContext?.firstOrNull()
      TestCase.assertTrue(fromTestContext != null)
    }
  }

  @Test
  fun `test class and method producers require different contexts`() {
    val projectData = generateAndImportTemplateProject()

    runReadActionAndWait {
      val classContext = getContextByLocation(projectData["project"]["TestCase"].element)
      val methodContext = getContextByLocation(projectData["project"]["TestCase"]["test1"].element)

      val classConfigProducer = getConfigurationProducer<TestClassGradleConfigurationProducer>()
      val methodConfigProducer = getConfigurationProducer<TestMethodGradleConfigurationProducer>()

      val classConfiguration = classConfigProducer.createConfigurationFromContext(classContext)?.configuration as GradleRunConfiguration
      val methodConfiguration = methodConfigProducer.createConfigurationFromContext(methodContext)?.configuration as GradleRunConfiguration

      assertTrue(classConfigProducer.isConfigurationFromContext(classConfiguration, classContext))
      assertTrue(methodConfigProducer.isConfigurationFromContext(methodConfiguration, methodContext))

      assertNull(classConfigProducer.createConfigurationFromContext(methodContext))
      assertNull(methodConfigProducer.createConfigurationFromContext(classContext))

      assertFalse(classConfigProducer.isConfigurationFromContext(classConfiguration, methodContext))
      assertFalse(methodConfigProducer.isConfigurationFromContext(methodConfiguration, classContext))
    }
  }
}
