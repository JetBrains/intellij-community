// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.producer

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemRunConfigurationProducer
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemTaskLocation
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.testFramework.TestActionEvent
import com.intellij.testIntegration.TestRunLineMarkerProvider
import com.intellij.util.LocalTimeCounter
import org.jetbrains.plugins.gradle.execution.GradleRunConfigurationProducerTestCase
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer
import org.jetbrains.plugins.gradle.execution.test.runner.TestName
import org.jetbrains.plugins.gradle.execution.test.runner.TestTasksChooser
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.findChildByType
import org.jetbrains.plugins.gradle.util.runReadActionAndWait
import org.junit.runners.Parameterized
import java.io.File

abstract class GradleTestRunConfigurationProducerTestCase : GradleRunConfigurationProducerTestCase() {

  private fun getGutterTestRunActionsByLocation(element: PsiNameIdentifierOwner) = runReadActionAndWait {
    val identifier = element.identifyingElement!!
    val info = TestRunLineMarkerProvider().getInfo(identifier)!!
    val runAction = info.actions.first() as ExecutorAction
    val context = getContextByLocation(element)
    runAction.getChildren(TestActionEvent.createTestEvent(context.dataContext))
  }

  protected fun assertGutterRunActionsSize(element: PsiNameIdentifierOwner, expectedSize: Int) {
    val actions = getGutterTestRunActionsByLocation(element)
    assertEquals(expectedSize, actions.size)
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
  ) {
    verifyRunConfigurationProducer<P>(expectedSettings, *elements) {
      setTestTasksChooser(testTasksFilter)
    }
  }

  protected inline fun <reified P : GradleTestRunConfigurationProducer> assertConfigurationFromContext(
    expectedSettings: String,
    noinline context: () -> ConfigurationContext,
    noinline testTasksFilter: (TestName) -> Boolean = { true }
  ) {
    verifyRunConfigurationProducer<P>(expectedSettings, context) {
      setTestTasksChooser(testTasksFilter)
    }
  }

  protected fun assertConfigurationForTask(expectedSettings: String, taskName: String, element: PsiElement) = runReadActionAndWait {
    val taskData = TaskData(GradleConstants.SYSTEM_ID, taskName, projectPath, null)
    val taskInfo = ExternalSystemActionUtil.buildTaskInfo(taskData)
    val taskLocation = ExternalSystemTaskLocation(myProject, element, taskInfo)
    val context = ConfigurationContext.createEmptyContextForLocation(taskLocation)
    val configurationFromContext = getConfigurationFromContext(context)
    assertInstanceOf(configurationFromContext.configurationProducer, AbstractExternalSystemRunConfigurationProducer::class.java)
    val runConfiguration = configurationFromContext.configuration as GradleRunConfiguration
    assertEquals(expectedSettings, runConfiguration.settings.toString())
  }

  fun GradleTestRunConfigurationProducer.setTestTasksChooser(testTasksFilter: (TestName) -> Boolean) {
    setTestTasksChooser(object : TestTasksChooser() {
      override fun <T> chooseTestTasks(project: Project,
                                       context: DataContext,
                                       testTasks: Map<TestName, T>,
                                       consumer: (List<T>) -> Unit) {
        consumer(testTasks.filterKeys(testTasksFilter).values.toList())
      }
    })
  }

  protected fun GradleTestRunConfigurationProducer.createTemplateConfiguration(): GradleRunConfiguration {
    return configurationFactory.createTemplateConfiguration(myProject) as GradleRunConfiguration
  }

  protected fun createAndAddRunConfiguration(commandLine: String, vmOptions: String? = null): GradleRunConfiguration {
    val runManager = RunManager.getInstance(myProject)

    val name = "configuration (${LocalTimeCounter.currentTime()})"
    val configuration = runManager.createConfiguration(name, GradleExternalTaskConfigurationType::class.java)

    val runConfiguration = configuration.configuration as GradleRunConfiguration
    runConfiguration.settings.externalProjectPath = projectPath
    runConfiguration.rawCommandLine = commandLine
    if (vmOptions != null) {
      runConfiguration.settings.vmOptions = vmOptions
    }

    runManager.addConfiguration(configuration)
    runManager.selectedConfiguration = configuration

    return runConfiguration
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
    val testCaseWithMainFile = createProjectSubFile("src/test/java/org/example/TestCaseWithMain.java", """
      package org.example;
      import org.junit.Test;
      import org.example.AbstractTestCase;
      public class TestCaseWithMain extends AbstractTestCase {
        @Test public void test1() {}
        @Test public void test2() {}
        @Test public void test3() {}
        public static void main(String[] args) {}
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
    createSettingsFile {
      setProjectName("project")
      include("module")
      include("my module")
    }
    createBuildFile {
      withJavaPlugin()
      withIdeaPlugin()
      withJUnit4()
      withGroovyPlugin()
      withPrefix {
        call("sourceSets") {
          code("automation.java.srcDirs += 'automation'")
          code("automation.runtimeClasspath += sourceSets.test.runtimeClasspath")
          code("automation.compileClasspath += sourceSets.test.compileClasspath")
        }
      }
      addImplementationDependency(project(":", "tests"), "automation")
      withTask("autoTest", "Test") {
        code("classpath = sourceSets.automation.runtimeClasspath")
        code("testClassesDirs = sourceSets.automation.output.classesDirs")
      }
      withTask("automationTest", "Test") {
        code("classpath = sourceSets.automation.runtimeClasspath")
        code("testClassesDirs = sourceSets.automation.output.classesDirs")
      }
      withTask("testJar", "Jar") {
        code("dependsOn testClasses")
        when {
          isGradleAtLeast("9.0") -> assign("archiveBaseName", "test-${'$'}{project.base.archivesName}")
          isGradleAtLeast("8.0") -> assign("archiveBaseName", "test-${'$'}{project.archivesBaseName}")
          else -> assign("baseName", "test-${'$'}{project.archivesBaseName}")
        }
        code("from sourceSets.test.output")
      }
      withPostfix {
        call("configurations") {
          code("tests")
        }
        call("artifacts") {
          code("tests testJar")
        }
        call("idea.module") {
          if (isGradleAtLeast("9.0")) {
            code("testSources.from(files('automation'))")
          } else {
            code("testSourceDirs += file('automation')")
          }
        }
      }
    }
    createBuildFile("module") {
      withJavaPlugin()
      withJUnit4()
      addTestImplementationDependency(project(":", "tests"))
    }
    createBuildFile("my module") {
      withJavaPlugin()
      withJUnit4()
      withGroovyPlugin()
      addTestImplementationDependency(project(":", "tests"))
    }
    importProject()
    assertModulesContains("project", "project.module", "project.my_module")
    val automationTestCase = extractClassData(automationTestCaseFile)
    val testCase = extractClassData(testCaseFile)
    val testCaseWithMain = extractClassData(testCaseWithMainFile)
    val abstractTestCase = extractClassData(abstractTestCaseFile)
    val moduleTestCase = extractClassData(moduleTestCaseFile)
    val packageTestCase = extractClassData(packageTestCaseFile)
    val groovyTestCase = extractClassData(groovyTestCaseFile)
    val myModuleTestCase = extractClassData(myModuleTestCaseFile)
    val projectDir = findPsiDirectory(".")
    val moduleDir = findPsiDirectory("module")
    val myModuleDir = findPsiDirectory("my module")
    return ProjectData(
      ModuleData("project", projectDir, testCase, testCaseWithMain, packageTestCase, automationTestCase, abstractTestCase, groovyTestCase),
      ModuleData("module", moduleDir, moduleTestCase),
      ModuleData("my module", myModuleDir, myModuleTestCase)
    )
  }

  protected fun findPsiDirectory(relativePath: String) = runReadActionAndWait {
    val virtualFile = VfsUtil.findFileByIoFile(File(projectPath, relativePath), false)!!
    val psiManager = PsiManager.getInstance(myProject)
    psiManager.findDirectory(virtualFile)!!
  }

  protected open fun extractClassData(file: VirtualFile) = runReadActionAndWait {
    val psiManager = PsiManager.getInstance(myProject)
    val psiFile = psiManager.findFile(file)!!
    val psiClass = psiFile.findChildByType<PsiClass>()
    val psiMethods = psiClass.methods
    val methods = psiMethods.map { MethodData(it.name, it) }
    ClassData(psiClass.qualifiedName!!, psiClass, methods)
  }

  protected open class Mapping<D>(val data: Map<String, D>) {
    operator fun get(key: String): D = data.getValue(key)
  }

  protected class ProjectData(
    vararg modules: ModuleData
  ) : Mapping<ModuleData>(modules.associateBy { it.name })

  protected class ModuleData(
    val name: String,
    val root: PsiDirectory,
    vararg classes: ClassData
  ) : Mapping<ClassData>(classes.associateBy { it.name })

  protected class ClassData(
    val name: String,
    val element: PsiClass,
    methods: List<MethodData>
  ) : Mapping<MethodData>(methods.associateBy { it.name })

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