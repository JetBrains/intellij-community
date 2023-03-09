// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test

import com.intellij.openapi.externalSystem.model.task.*
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isGradleAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.params.ParameterizedTest
import java.util.function.Function

class GradleJavaTestEventsIntegrationTest : GradleJavaTestEventsIntegrationTestCase() {

  @ParameterizedTest
  @TargetVersions("!6.9")
  @AllGradleVersionsSource
  fun `test call test task produces test events`(gradleVersion: GradleVersion) {
    test(gradleVersion, FIXTURE_BUILDER) {
      val output = LoggingESOutputListener()
      Assertions.setMaxStackTraceElementsDisplayed(1000)
      assertThatThrownBy {
        executeTasks(output, ":test") {
          putUserData(GradleConstants.RUN_TASK_AS_TEST, true)
        }
      }
        .`is`("contain failed tests message") { "Test failed." in it || "There were failing tests" in it }

      if (testLauncherAPISupported()) {
        assertThat(output.testsDescriptors)
          .extracting(Function { it.className to it.methodName })
          .contains("my.pack.AClassTest" to "testSuccess",
                    "my.pack.AClassTest" to "testFail",
                    "my.otherpack.AClassTest" to "testSuccess")
      }
      else {
        assertThat(output.eventLog)
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
      val output = LoggingESOutputListener()
      assertThatThrownBy { executeTasks(output, "clean", "build") }
        .hasMessageContaining("There were failing tests")
      assertThat(output.eventLog)
        .noneMatch { it.contains("<ijLogEol/>") }
    }
  }

  @ParameterizedTest
  @TargetVersions("!6.9")
  @AllGradleVersionsSource
  fun `test call task for specific test overrides existing filters`(gradleVersion: GradleVersion) {
    test(gradleVersion, FIXTURE_BUILDER) {
      val output = executeTasks(":cleanTest", ":test") {
        putUserData(GradleConstants.RUN_TASK_AS_TEST, true)
        withArguments("--tests", "my.otherpack.*")
      }

      assertThat(output.eventLog)
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
      val output = executeTasks(":junit5test") {
        putUserData(GradleConstants.RUN_TASK_AS_TEST, true)
      }

      when {
        testLauncherAPISupported() -> {
          assertThat(output.testsDescriptors)
            .extracting(Function { (it.className + "$" + it.methodName) to it.displayName })
            .contains("my.otherpack.ADisplayNamedTest\$successful_test()" to "successful test")
        }
        isGradleAtLeast("4.10.3") -> {
          assertThat(output.eventLog)
            .contains("<descriptor name='successful_test()' displayName='successful test' className='my.otherpack.ADisplayNamedTest' />")
        }
        else -> {
          assertThat(output.eventLog)
            .contains("<descriptor name='successful test' displayName='successful test' className='my.otherpack.ADisplayNamedTest' />")
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
}
