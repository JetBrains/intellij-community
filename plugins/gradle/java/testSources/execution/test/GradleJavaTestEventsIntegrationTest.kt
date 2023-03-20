// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test

import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.buildScript
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.params.ParameterizedTest

class GradleJavaTestEventsIntegrationTest : GradleJavaTestEventsIntegrationTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test call test task produces test events`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      appendText("build.gradle", """
        |import java.util.concurrent.atomic.AtomicBoolean;
        |def resolutionAllowed = new AtomicBoolean(false)
        |
        |if (configurations.findByName("testRuntimeClasspath") != null) {
        |  configurations.testRuntimeClasspath.incoming.beforeResolve {
        |    if (!resolutionAllowed.get() && !System.properties["idea.sync.active"]) {
        |      logger.warn("Attempt to resolve configuration too early")
        |    }
        |  }
        |}
        |
        |gradle.taskGraph.beforeTask { Task task ->
        |  if (task.path == ":test" ) {
        |    resolutionAllowed.set(true)
        |  }
        |}
      """.trimMargin())
      writeText("src/test/java/pkg/AClassTest.java", """
        |package pkg;
        |import $jUnitTestAnnotationClass;
        |public class AClassTest {
        |  @Test public void testSuccess() {}
        |  @Test public void testFail() { throw new RuntimeException(); }
        |}
      """.trimMargin())
      writeText("src/test/java/other/pkg/AClassTest.java", """
        |package other.pkg;
        |import $jUnitTestAnnotationClass;
        |public class AClassTest {
        |  @Test public void testSuccess() {}
        |}
      """.trimMargin())

      val output = executeTasks(":test", hasFailingTests = true)
      assertThat(output.testsDescriptors)
        .transform { it.className to it.methodName }
        .contains("pkg.AClassTest" to "testSuccess")
        .contains("pkg.AClassTest" to "testFail")
        .contains("other.pkg.AClassTest" to "testSuccess")
      assertThat(output.outputLog)
        .doesNotContain("Attempt to resolve configuration too early")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test call build task does not produce test events`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/pkg/AClassTest.java", """
        |package pkg;
        |import $jUnitTestAnnotationClass;
        |public class AClassTest {
        |  @Test public void testSuccess() {}
        |  @Test public void testFail() { throw new RuntimeException(); }
        |}
      """.trimMargin())

      val output = executeTasks("clean build", hasFailingTests = true)
      assertThat(output.outputLog)
        .noneMatch { it.contains("<ijLogEol/>") }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test call task for specific test overrides existing filters`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      appendText("build.gradle", """
        |test { 
        |  filter { 
        |    includeTestsMatching 'pkg.*'
        |  } 
        |}
      """.trimMargin())
      writeText("src/test/java/pkg/AClassTest.java", """
        |package pkg;
        |import $jUnitTestAnnotationClass;
        |public class AClassTest {
        |  @Test public void testSuccess() {}
        |  @Test public void testFail() { throw new RuntimeException(); }
        |}
      """.trimMargin())
      writeText("src/test/java/other/pkg/AClassTest.java", """
        |package other.pkg;
        |import $jUnitTestAnnotationClass;
        |public class AClassTest {
        |  @Test public void testSuccess() {}
        |}
      """.trimMargin())

      val output1 = executeTasks("test", hasFailingTests = true)
      assertThat(output1.testsDescriptors)
        .transform { it.className to it.methodName }
        .contains("pkg.AClassTest" to "testFail")
        .contains("pkg.AClassTest" to "testSuccess")
        .doesNotContain("other.pkg.AClassTest" to "testSuccess")

      val output2 = executeTasks("test --tests pkg.AClassTest.testFail", hasFailingTests = true)
      assertThat(output2.testsDescriptors)
        .transform { it.className to it.methodName }
        .contains("pkg.AClassTest" to "testFail")
        .doesNotContain("pkg.AClassTest" to "testSuccess")
        .doesNotContain("other.pkg.AClassTest" to "testSuccess")

      if (isGradleAtLeast("4.1")) {
        val output3 = executeTasks("test --tests other.pkg.*", noMatchingTests = true)
        assertThat(output3.testsDescriptors)
          .transform { it.className to it.methodName }
          .doesNotContain("pkg.AClassTest" to "testFail")
          .doesNotContain("pkg.AClassTest" to "testSuccess")
          .doesNotContain("other.pkg.AClassTest" to "testSuccess")
      }
    }
  }

  @ParameterizedTest
  @TargetVersions("4.10.3+")
  @AllGradleVersionsSource
  fun `test display name is used by test events`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/pkg/ADisplayNamedTest.java", """
        |package pkg;
        |import org.junit.jupiter.api.DisplayNameGeneration;
        |import org.junit.jupiter.api.DisplayNameGenerator;
        |import org.junit.jupiter.api.Test;
        |@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
        |public class ADisplayNamedTest {
        |  @Test public void successful_test() {}
        |}
      """.trimMargin())

      val output = executeTasks(":test --tests pkg.ADisplayNamedTest")
      assertThat(output.testsDescriptors)
        .transform { it.className to it.methodName to it.displayName }
        .contains("pkg.ADisplayNamedTest" to "successful_test" to "successful test")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test rerun test`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/pkg/AClassTest.java", """
        |package pkg;
        |import $jUnitTestAnnotationClass;
        |public class AClassTest {
        |  @Test public void testSuccess() {}
        |  @Test public void testFail() { throw new RuntimeException(); }
        |}
      """.trimMargin())

      executeTasks("test --tests 'pkg.AClassTest.testSuccess'")
      val output = executeTasks("test --tests 'pkg.AClassTest.testSuccess'")
      assertThat(output.testsDescriptors)
        .transform { it.className to it.methodName }
        .contains("pkg.AClassTest" to "testSuccess")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test tasks order`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      appendText("build.gradle", buildScript(gradleVersion) {
        withPostfix {
          call("tasks.create", "beforeTest")
          call("tasks.create", "afterTest")
        }
      })
      writeText("src/test/java/pkg/AClassTest.java", """
        |package pkg;
        |import $jUnitTestAnnotationClass;
        |public class AClassTest {
        |  @Test public void testSuccess() {}
        |  @Test public void testFail() { throw new RuntimeException(); }
        |}
      """.trimMargin())

      val output1 = executeTasks("beforeTest test --tests pkg.AClassTest.testSuccess")
      assertTaskOrder(output1, ":beforeTest", ":test")
      val output2 = executeTasks("test --tests pkg.AClassTest.testSuccess afterTest")
      assertTaskOrder(output2, ":test", ":afterTest")
      val output3 = executeTasks("beforeTest test --tests pkg.AClassTest.testSuccess afterTest")
      assertTaskOrder(output3, ":beforeTest", ":test", ":afterTest")
    }
  }
}
