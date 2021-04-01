// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.frameworkSupport.script.GroovyScriptBuilder.Companion.groovy
import org.jetbrains.plugins.gradle.importing.buildscript
import org.jetbrains.plugins.gradle.util.*
import org.junit.runners.Parameterized
import java.io.File
import java.util.function.Consumer

abstract class GradleTestRunConfigurationProducerTestCase : GradleImportingTestCase() {

  protected fun getContextByLocation(vararg elements: PsiElement): ConfigurationContext {
    assertTrue(elements.isNotEmpty())
    return object : ConfigurationContext(elements[0]) {
      override fun getDataContext() = SimpleDataContext.getSimpleContext(LangDataKeys.PSI_ELEMENT_ARRAY, elements, super.getDataContext())
      override fun containsMultipleSelection() = elements.size > 1
    }
  }

  protected fun getConfigurationFromContext(context: ConfigurationContext): ConfigurationFromContextImpl {
    val fromContexts = context.configurationsFromContext
    val fromContext = fromContexts?.firstOrNull()
    assertNotNull("Gradle configuration from context not found", fromContext)
    return fromContext as ConfigurationFromContextImpl
  }

  protected inline fun <reified P : GradleTestRunConfigurationProducer> getConfigurationProducer(): P {
    return RunConfigurationProducer.getInstance(P::class.java)
  }

  protected fun assertProducersFromContext(
    element: PsiElement,
    vararg expectedProducerTypes: String
  ) = runReadActionAndWait {
    val context = getContextByLocation(element)
    val actualProducerTypes = context.configurationsFromContext!!
      .map { it as ConfigurationFromContextImpl }
      .map { it.configurationProducer }
      .map { it::class.java.simpleName }
    assertContainsElements(actualProducerTypes, *expectedProducerTypes)
  }

  protected inline fun <reified P : GradleTestRunConfigurationProducer> assertConfigurationFromContext(
    expectedSettings: String,
    vararg elements: PsiElement,
    noinline testTasksFilter: (TestName) -> Boolean = { true }
  ) = runReadActionAndWait {
    val context = getContextByLocation(*elements)
    val configurationFromContext = getConfigurationFromContext(context)
    val producer = configurationFromContext.configurationProducer as P
    producer.setTestTasksChooser(testTasksFilter)
    val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
    assertTrue(producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
    if (producer !is PatternGradleConfigurationProducer) {
      assertTrue(producer.isConfigurationFromContext(configuration, context))
    }
    producer.onFirstRun(configurationFromContext, context, Runnable {})
    assertEquals(expectedSettings, configuration.settings.toString().trim())
  }

  protected fun GradleTestRunConfigurationProducer.setTestTasksChooser(testTasksFilter: (TestName) -> Boolean) {
    testTasksChooser = object : TestTasksChooser() {
      override fun chooseTestTasks(project: Project,
                                   context: DataContext,
                                   testTasks: Map<TestName, Map<SourcePath, TasksToRun>>,
                                   consumer: Consumer<List<Map<SourcePath, TestTasks>>>) {
        consumer.accept(testTasks.filterKeys(testTasksFilter).values.toList())
      }
    }
  }

  protected fun GradleTestRunConfigurationProducer.createTemplateConfiguration(): ExternalSystemRunConfiguration {
    return configurationFactory.createTemplateConfiguration(myProject) as ExternalSystemRunConfiguration
  }

  protected fun generateAndImportTemplateProject(): ProjectData {
    val testCaseFile = createProjectSubFile("src/test/java/TestCase.java", """
      import org.junit.Test;
      import org.example.AbstractTestCase;
      public class TestCase extends AbstractTestCase {
        @Test public void test1() {}
        @Test public void test2() {}
        @Test public void test3() {}
      }
    """.trimIndent())
    val packageTestCaseFile = createProjectSubFile("src/test/java/pkg/TestCase.java", """
      package pkg;
      import org.junit.Test;
      import org.example.AbstractTestCase;
      public class TestCase extends AbstractTestCase {
        @Test public void test1() {}
        @Test public void test2() {}
        @Test public void test3() {}
      }
    """.trimIndent())
    val automationTestCaseFile = createProjectSubFile("automation/AutomationTestCase.java", """
      import org.junit.Test;
      import org.example.AbstractTestCase;
      public class AutomationTestCase extends AbstractTestCase {
        @Test public void test1() {}
        @Test public void test2() {}
        @Test public void test3() {}
      }
    """.trimIndent())
    val abstractTestCaseFile = createProjectSubFile("src/test/java/org/example/AbstractTestCase.java", """
      package org.example;
      import org.junit.Test;
      public abstract class AbstractTestCase {
        @Test public void test() {}
      }
    """.trimIndent())
    val moduleTestCaseFile = createProjectSubFile("module/src/test/java/ModuleTestCase.java", """      
      import org.junit.Test;
      import org.example.AbstractTestCase;
      public class ModuleTestCase extends AbstractTestCase {
        @Test public void test1() {}
        @Test public void test2() {}
        @Test public void test3() {}
      }
    """.trimIndent())
    val groovyTestCaseFile = createProjectSubFile("src/test/groovy/GroovyTestCase.groovy", """
      import org.junit.Test;
      import org.example.AbstractTestCase;
      public class GroovyTestCase extends AbstractTestCase {
        @Test public void 'Don\\\'t use single quo\\"tes'() {}
        @Test public void test1() {}
        @Test public void test2() {}
        @Test public void test3() {}
      }
    """.trimIndent())
    val myModuleTestCaseFile = createProjectSubFile("my module/src/test/groovy/MyModuleTestCase.groovy", """
      import org.junit.Test;
      import org.example.AbstractTestCase;
      public class MyModuleTestCase extends AbstractTestCase {
        @Test public void test1() {}
        @Test public void test2() {}
        @Test public void test3() {}
      }
    """.trimIndent())
    createProjectSubFile("settings.gradle", groovy {
      code("rootProject.name = 'project'")
      call("include", "'module'", "'my module'")
    })
    createProjectSubFile("build.gradle", buildscript {
      withJavaPlugin()
      withIdeaPlugin()
      withJUnit4()
      withGroovyPlugin()
      withPrefix {
        block("sourceSets") {
          code("automation.java.srcDirs += 'automation'")
          code("automation.runtimeClasspath += sourceSets.test.runtimeClasspath")
          code("automation.compileClasspath += sourceSets.test.compileClasspath")
        }
      }
      addImplementationDependency("project(path: ':', configuration: 'tests')", sourceSet = "automation")
      withTask("autoTest", "Test") {
        code("classpath = sourceSets.automation.runtimeClasspath")
        code("testClassesDirs = sourceSets.automation.output.classesDirs")
      }
      withTask("automationTest", "Test") {
        code("classpath = sourceSets.automation.runtimeClasspath")
        code("testClassesDirs = sourceSets.automation.output.classesDirs")
      }
      withTask("testJar", "Jar") {
        call("dependsOn", "testClasses")
        code("baseName = \"test-${'$'}{project.archivesBaseName}\"")
        call("from", "sourceSets.test.output")
      }
      withPostfix {
        block("configurations") {
          code("tests")
        }
        block("artifacts") {
          call("tests", "testJar")
        }
        block("idea.module") {
          code("testSourceDirs += file('automation')")
        }
      }
    })
    createProjectSubFile("module/build.gradle", buildscript {
      withJavaPlugin()
      withJUnit4()
      addTestImplementationDependency("project(path: ':', configuration: 'tests')")
    })
    createProjectSubFile("my module/build.gradle", buildscript {
      withJavaPlugin()
      withJUnit4()
      withGroovyPlugin()
      addTestImplementationDependency("project(path: ':', configuration: 'tests')")
    })
    importProject()
    assertModulesContains("project", "project.module", "project.my_module")
    val automationTestCase = extractJavaClassData(automationTestCaseFile)
    val testCase = extractJavaClassData(testCaseFile)
    val abstractTestCase = extractJavaClassData(abstractTestCaseFile)
    val moduleTestCase = extractJavaClassData(moduleTestCaseFile)
    val packageTestCase = extractJavaClassData(packageTestCaseFile)
    val groovyTestCase = extractGroovyClassData(groovyTestCaseFile)
    val myModuleTestCase = extractGroovyClassData(myModuleTestCaseFile)
    val projectDir = findPsiDirectory(".")
    val moduleDir = findPsiDirectory("module")
    val myModuleDir = findPsiDirectory("my module")
    return ProjectData(
      ModuleData("project", projectDir, testCase, packageTestCase, automationTestCase, abstractTestCase, groovyTestCase),
      ModuleData("module", moduleDir, moduleTestCase),
      ModuleData("my module", myModuleDir, myModuleTestCase)
    )
  }

  private fun findPsiDirectory(relativePath: String) = runReadActionAndWait {
    val virtualFile = VfsUtil.findFileByIoFile(File(projectPath, relativePath), false)!!
    val psiManager = PsiManager.getInstance(myProject)
    psiManager.findDirectory(virtualFile)!!
  }

  private fun extractJavaClassData(file: VirtualFile) = runReadActionAndWait {
    val psiManager = PsiManager.getInstance(myProject)
    val psiFile = psiManager.findFile(file)!!
    val psiClass = psiFile.findChildByType<PsiClass>()
    val psiMethods = psiClass.findChildrenByType<PsiMethod>()
    val methods = psiMethods.map { MethodData(it.name, it) }
    ClassData(psiClass.qualifiedName!!, psiClass, methods)
  }

  private fun extractGroovyClassData(file: VirtualFile) = runReadActionAndWait {
    val psiManager = PsiManager.getInstance(myProject)
    val psiFile = psiManager.findFile(file)!!
    val psiClass = psiFile.findChildByType<PsiClass>()
    val classBody = psiClass.findChildByElementType("CLASS_BODY")
    val psiMethods = classBody.findChildrenByType<PsiMethod>()
    val methods = psiMethods.map { MethodData(it.name, it) }
    ClassData(psiClass.qualifiedName!!, psiClass, methods)
  }


  protected open class Mapping<D>(val data: Map<String, D>) {
    operator fun get(key: String): D = data.getValue(key)
  }

  protected class ProjectData(
    vararg modules: ModuleData
  ) : Mapping<ModuleData>(modules.map { it.name to it }.toMap())

  protected class ModuleData(
    val name: String,
    val root: PsiDirectory,
    vararg classes: ClassData
  ) : Mapping<ClassData>(classes.map { it.name to it }.toMap())

  protected class ClassData(
    val name: String,
    val element: PsiClass,
    methods: List<MethodData>
  ) : Mapping<MethodData>(methods.map { it.name to it }.toMap())

  protected class MethodData(
    val name: String,
    val element: PsiMethod
  )

  protected fun PsiDirectory.subDirectory(vararg names: String) = runReadActionAndWait {
    var directory = this
    for (name in names) {
      directory = directory.findSubdirectory(name)!!
    }
    directory
  }

  companion object {
    /**
     * It's sufficient to run the test against one gradle version
     */
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = arrayListOf(arrayOf(BASE_GRADLE_VERSION))
  }
}