// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test

import com.intellij.openapi.externalSystem.model.task.*
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.runAll
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Condition
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder.Companion.buildscript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.tooling.util.GradleVersionComparator
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test

open class GradleJavaTestEventsIntegrationTest: GradleImportingTestCase() {

  override fun setUp() {
    super.setUp()
    if (testLauncherAPISupported()) {
      Registry.get("gradle.testLauncherAPI.enabled").setValue(true, testRootDisposable)
    }
  }

  @Test
  @TargetVersions("!6.9")
  fun test() {
    val gradleSupportsJunitPlatform = isGradleNewerOrSameAs("4.6")
    createProjectSubFile("src/main/java/my/pack/AClass.java",
                         "package my.pack;\n" +
                         "public class AClass {\n" +
                         "  public int method() { return 42; }" +
                         "}")

    createProjectSubFile("src/test/java/my/pack/AClassTest.java",
                         "package my.pack;\n" +
                         "import org.junit.Test;\n" +
                         "import static org.junit.Assert.*;\n" +
                         "public class AClassTest {\n" +
                         "  @Test\n" +
                         "  public void testSuccess() {\n" +
                         "    assertEquals(42, new AClass().method());\n" +
                         "  }\n" +
                         "  @Test\n" +
                         "  public void testFail() {\n" +
                         "    fail(\"failure message\");\n" +
                         "  }\n" +
                         "}")

    createProjectSubFile("src/test/java/my/otherpack/AClassTest.java",
                         "package my.otherpack;\n" +
                         "import my.pack.AClass;\n" +
                         "import org.junit.Test;\n" +
                         "import static org.junit.Assert.*;\n" +
                         "public class AClassTest {\n" +
                         "  @Test\n" +
                         "  public void testSuccess() {\n" +
                         "    assertEquals(42, new AClass().method());\n" +
                         "  }\n" +
                         "}")

    createProjectSubFile("src/junit5test/java/my/otherpack/ADisplayNamedTest.java",
                         """
                           package my.otherpack;
                           import org.junit.jupiter.api.DisplayNameGeneration;
                           import org.junit.jupiter.api.DisplayNameGenerator;
                           import org.junit.jupiter.api.Test;
                           import static org.junit.jupiter.api.Assertions.assertTrue;
                           @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
                           public class ADisplayNamedTest {
                               @Test
                               void successful_test() {
                                   assertTrue(true);
                               }
                           }
                         """.trimIndent())

    importProject(buildscript {
      withJavaPlugin()
      withJUnit4()
      if (gradleSupportsJunitPlatform) {
        addPostfix("""
          sourceSets {
            junit5test
          }
          
          dependencies {
            junit5testImplementation 'org.junit.jupiter:junit-jupiter-api:5.7.0'
            junit5testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.7.0'
          }
          
          task junit5test(type: Test) {
            useJUnitPlatform()
            testClassesDirs = sourceSets.junit5test.output.classesDirs
            classpath = sourceSets.junit5test.runtimeClasspath
          }
        """.trimIndent())
      }
      addPostfix("test { filter { includeTestsMatching 'my.pack.*' } }")
      addPostfix("""
        import java.util.concurrent.atomic.AtomicBoolean;
        def resolutionAllowed = new AtomicBoolean(false)

        if (configurations.findByName("testRuntimeClasspath") != null) {
          configurations.testRuntimeClasspath.incoming.beforeResolve {
              if (!resolutionAllowed.get() && !System.properties["idea.sync.active"]) {
                  logger.warn("Attempt to resolve configuration too early")
              }
          }
        }
        
        gradle.taskGraph.beforeTask { Task task ->
            if (task.path == ":test" ) {
                println("Greenlight to resolving the configuration!")
                resolutionAllowed.set(true)
            }
        }
      """.trimIndent())
    })

    runAll(
      { `call test task produces test events`() },
      { `call build task does not produce test events`() },
      { `call task for specific test overrides existing filters`() },
      { if (gradleSupportsJunitPlatform) `test events use display name`() }
    )
  }

  private fun testLauncherAPISupported(): Boolean = isGradleNewerOrSameAs("6.1")

  private fun extractTestClassesAndMethods(testListener: LoggingESStatusChangeListener) =
    testListener.eventLog
      .filterIsInstance<ExternalSystemTaskExecutionEvent>()
      .map { it.progressEvent.descriptor }
      .filterIsInstance<TestOperationDescriptor>()
      .map { it.run { className to methodName } }

  private fun `call test task produces test events`() {
    val testEventListener = LoggingESStatusChangeListener()
    val testListener = LoggingESOutputListener(testEventListener)

    val settings = createSettings { putUserData(GradleConstants.RUN_TASK_AS_TEST, true) }

    assertThatThrownBy {
      GradleTaskManager().executeTasks(createId(),
                                       listOf(":test"),
                                       projectPath,
                                       settings,
                                       null,
                                       testListener)
    }
      .`is`(Condition({
                        val message = it.message ?: return@Condition false
                        message.contains("Test failed.") || message.contains("There were failing tests")
                      },
                      "Contain failed tests message"))


    if (testLauncherAPISupported()) {
      val testOperationDescriptors = extractTestClassesAndMethods(testEventListener)

      assertThat(testOperationDescriptors)
        .contains("my.pack.AClassTest" to "testSuccess",
                  "my.pack.AClassTest" to "testFail",
                  "my.otherpack.AClassTest" to "testSuccess")
    }
    else {
      assertThat(testListener.eventLog)
        .contains(
          "<descriptor name='testFail' displayName='testFail' className='my.pack.AClassTest' />",
          "<descriptor name='testSuccess' displayName='testSuccess' className='my.pack.AClassTest' />")
        .doesNotContain(
          "<descriptor name='testSuccess' displayName='testSuccess' className='my.otherpack.AClassTest' />")
        .doesNotContain(
          "Attempt to resolve configuration too early")
    }
  }

  private fun `call build task does not produce test events`() {
    val testListener = LoggingESOutputListener()
    val settings = createSettings()

    assertThatThrownBy {
      GradleTaskManager().executeTasks(createId(),
                                       listOf("clean", "build"),
                                       projectPath,
                                       settings,
                                       null,
                                       testListener)
    }
      .hasMessageContaining("There were failing tests")
    assertThat(testListener.eventLog).noneMatch { it.contains("<ijLogEol/>") }
  }

  private fun `call task for specific test overrides existing filters`() {
    val testListener = LoggingESOutputListener()

    val settings = createSettings {
      putUserData(GradleConstants.RUN_TASK_AS_TEST, true)
      withArguments("--tests","my.otherpack.*")
    }

      GradleTaskManager().executeTasks(createId(),
                                       listOf(":cleanTest", ":test"),
                                       projectPath,
                                       settings,
                                       null,
                                       testListener)

    assertThat(testListener.eventLog)
      .contains("<descriptor name='testSuccess' displayName='testSuccess' className='my.otherpack.AClassTest' />")
      .doesNotContain("<descriptor name='testFail' displayName='testFail' className='my.pack.AClassTest' />",
                      "<descriptor name='testSuccess' displayName='testSuccess' className='my.pack.AClassTest' />")
  }

  private fun `test events use display name`() {
    val testEventListener = LoggingESStatusChangeListener()
    val testListener = LoggingESOutputListener(testEventListener)

    val settings = createSettings { putUserData(GradleConstants.RUN_TASK_AS_TEST, true) }

    GradleTaskManager().executeTasks(createId(),
                                     listOf(":junit5test"),
                                     projectPath,
                                     settings,
                                     null,
                                     testListener)

    if (testLauncherAPISupported()) {
      val testOperationDescriptors = testEventListener.eventLog
        .filterIsInstance<ExternalSystemTaskExecutionEvent>()
        .map { it.progressEvent.descriptor }
        .filterIsInstance<TestOperationDescriptor>()
        .map { it.run { "$className$$methodName" to displayName } }

      assertThat(testOperationDescriptors)
        .contains("my.otherpack.ADisplayNamedTest\$successful_test()" to "successful test")
    }
    else {
      if (GradleVersionComparator(currentGradleVersion).isOrGreaterThan("4.10.3")) {
        assertThat(testListener.eventLog)
          .contains("<descriptor name='successful_test()' displayName='successful test' className='my.otherpack.ADisplayNamedTest' />")
      } else {
        assertThat(testListener.eventLog)
          .contains("<descriptor name='successful test' displayName='successful test' className='my.otherpack.ADisplayNamedTest' />")
      }
    }

  }

  private fun createSettings(config: GradleExecutionSettings.() -> Unit = {}) = GradleManager()
    .executionSettingsProvider
    .`fun`(Pair.create(myProject, projectPath))
    .apply { config() }

  private fun createId() = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myProject)

  class LoggingESOutputListener(delegate: ExternalSystemTaskNotificationListener? = null) : ExternalSystemTaskNotificationListenerAdapter(
    delegate) {
    val eventLog = mutableListOf<String>()

    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
      addEventLogLines(text, eventLog)
    }

    private fun addEventLogLines(text: String, eventLog: MutableList<String>) {
      text.split("<ijLogEol/>").mapTo(eventLog) { it.trim('\r', '\n', ' ') }
    }
  }

  class LoggingESStatusChangeListener : ExternalSystemTaskNotificationListenerAdapter() {
    val eventLog = mutableListOf<ExternalSystemTaskNotificationEvent>()

    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
      eventLog.add(event)
    }
  }

}
