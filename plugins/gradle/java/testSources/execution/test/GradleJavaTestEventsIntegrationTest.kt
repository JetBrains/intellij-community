// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test

import com.intellij.openapi.externalSystem.model.task.*
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Condition
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.testFramework.GradleTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.isGradleAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.tooling.util.GradleVersionComparator
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.params.ParameterizedTest

class GradleJavaTestEventsIntegrationTest : GradleTestCase() {

  override fun test(gradleVersion: GradleVersion, fixtureBuilder: GradleTestFixtureBuilder, test: () -> Unit) {
    super.test(gradleVersion, fixtureBuilder) {
      val testLauncherApi = Registry.get("gradle.testLauncherAPI.enabled")
      if (testLauncherAPISupported()) {
        testLauncherApi.setValue(true)
      }
      try {
        test()
      }
      finally {
        if (testLauncherAPISupported()) {
          testLauncherApi.setValue(false)
        }
      }
    }
  }

  companion object {
    private val FIXTURE_BUILDER = GradleTestFixtureBuilder.create("GradleJavaTestEventsIntegrationTest") { gradleVersion ->
      val gradleSupportsJunitPlatform = gradleVersion.isGradleAtLeast("4.6")
      withFile("src/main/java/my/pack/AClass.java", """
        package my.pack;
        
        public class AClass {
          public int method() { 
            return 42;
          }
        }
      """.trimIndent())

      withFile("src/test/java/my/pack/AClassTest.java", """
        package my.pack;
        
        import org.junit.Test;
        import static org.junit.Assert.*;
        
        public class AClassTest {
          @Test
          public void testSuccess() {
            assertEquals(42, new AClass().method());
          }
          
          @Test
          public void testFail() {
            fail("failure message");
          }
        }
      """.trimIndent())

      withFile("src/test/java/my/otherpack/AClassTest.java", """
        package my.otherpack;
        
        import my.pack.AClass;
        import org.junit.Test;
        import static org.junit.Assert.*;
        
        public class AClassTest {
          @Test
          public void testSuccess() {
            assertEquals(42, new AClass().method());
          }
        }
      """.trimIndent())

      withFile("src/junit5test/java/my/otherpack/ADisplayNamedTest.java", """
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

      withBuildFile(gradleVersion) {
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
      }
    }
  }

  private fun testLauncherAPISupported(): Boolean =  isGradleAtLeast("6.1")

  private fun extractTestClassesAndMethods(testListener: LoggingESStatusChangeListener) =
    testListener.eventLog
      .filterIsInstance<ExternalSystemTaskExecutionEvent>()
      .map { it.progressEvent.descriptor }
      .filterIsInstance<TestOperationDescriptor>()
      .map { it.run { className to methodName } }

  @ParameterizedTest
  @TargetVersions("!6.9")
  @AllGradleVersionsSource
  fun `test call test task produces test events`(gradleVersion: GradleVersion) {
    test(gradleVersion, FIXTURE_BUILDER) {
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
  }

  @ParameterizedTest
  @TargetVersions("!6.9")
  @AllGradleVersionsSource
  fun `test call build task does not produce test events`(gradleVersion: GradleVersion) {
    test(gradleVersion, FIXTURE_BUILDER) {
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
  }

  @ParameterizedTest
  @TargetVersions("!6.9")
  @AllGradleVersionsSource
  fun `test call task for specific test overrides existing filters`(gradleVersion: GradleVersion) {
    test(gradleVersion, FIXTURE_BUILDER) {
      val testListener = LoggingESOutputListener()

      val settings = createSettings {
        putUserData(GradleConstants.RUN_TASK_AS_TEST, true)
        withArguments("--tests", "my.otherpack.*")
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
  }

  @ParameterizedTest
  @TargetVersions("4.6+", "!6.9")
  @AllGradleVersionsSource
  fun `test display name is used by test events`(gradleVersion: GradleVersion) {
    test(gradleVersion, FIXTURE_BUILDER) {
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
        if (GradleVersionComparator(gradleVersion).isOrGreaterThan("4.10.3")) {
          assertThat(testListener.eventLog)
            .contains("<descriptor name='successful_test()' displayName='successful test' className='my.otherpack.ADisplayNamedTest' />")
        }
        else {
          assertThat(testListener.eventLog)
            .contains("<descriptor name='successful test' displayName='successful test' className='my.otherpack.ADisplayNamedTest' />")
        }
      }
    }

  }

  private fun createSettings(config: GradleExecutionSettings.() -> Unit = {}) = GradleManager()
    .executionSettingsProvider
    .`fun`(Pair.create(project, projectPath))
    .apply { config() }

  private fun createId() = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)

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
