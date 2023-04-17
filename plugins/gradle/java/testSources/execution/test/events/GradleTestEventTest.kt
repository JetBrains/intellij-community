// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.params.ParameterizedTest

class GradleTestEventTest : GradleTestEventTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test test execution console doesn't contain intellij xml test events`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test() {}
        |}
      """.trimMargin())

      executeTasks(":test")
      assertTestConsoleDoesNotContain("<ijLogEol/>")
    }
  }

  @ParameterizedTest
  @TargetVersions("3.5+")
  @AllGradleVersionsSource
  fun `test configuration resolves after execution graph`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      appendText("build.gradle", """
        |import java.util.concurrent.atomic.AtomicBoolean;
        |
        |def resolutionAllowed = new AtomicBoolean(false)
        |
        |configurations.testRuntimeClasspath.incoming.beforeResolve {
        |  logger.warn("Attempt to resolve configuration")
        |  if (!resolutionAllowed.get()) {
        |    logger.warn("Attempt to resolve configuration too early")
        |  }
        |}
        |
        |gradle.taskGraph.beforeTask { Task task ->
        |  if (task.path == ":test" ) {
        |    logger.warn("Green light to resolve configuration")
        |    resolutionAllowed.set(true)
        |  }
        |}
      """.trimMargin())
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test() {}
        |}
      """.trimMargin())

      executeTasks(":test")
      assertTestConsoleContains("Green light to resolve configuration")
      assertTestConsoleContains("Attempt to resolve configuration")
      assertTestConsoleDoesNotContain("Attempt to resolve configuration too early")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test call test task produces test events`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void testSuccess() {}
        |  @Test public void testFail() { throw new RuntimeException(); }
        |}
      """.trimMargin())
      writeText("src/test/java/com/intellij/TestCase.java", """
        |package com.intellij;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void testSuccess() {}
        |  @Test public void testFail() { throw new RuntimeException(); }
        |}
      """.trimMargin())

      executeTasks(":test")
      assertTestEventsContain("org.example.TestCase", "testSuccess")
      assertTestEventsContain("org.example.TestCase", "testFail")
      assertTestEventsContain("com.intellij.TestCase", "testSuccess")
      assertTestEventsContain("com.intellij.TestCase", "testFail")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test call build task produce test events`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test() {}
        |}
      """.trimMargin())

      executeTasks(":build --rerun-tasks")
      assertTestEventsContain("org.example.TestCase", "test")

      executeTasks(":build")
      assertTestEventsWasNotReceived()
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test call task for specific test overrides existing filters`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      appendText("build.gradle", """
        |test { 
        |  filter { 
        |    includeTestsMatching 'org.example.*'
        |  } 
        |}
      """.trimMargin())
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void testSuccess() {}
        |  @Test public void testFail() { throw new RuntimeException(); }
        |}
      """.trimMargin())
      writeText("src/test/java/com/intellij/TestCase.java", """
        |package com.intellij;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void testSuccess() {}
        |  @Test public void testFail() { throw new RuntimeException(); }
        |}
      """.trimMargin())

      executeTasks(":test")
      assertTestEventsContain("org.example.TestCase", "testSuccess")
      assertTestEventsContain("org.example.TestCase", "testFail")
      assertTestEventsDoesNotContain("com.intellij.TestCase", "testSuccess")
      assertTestEventsDoesNotContain("com.intellij.TestCase", "testFail")

      executeTasks(":test --tests org.example.TestCase.testFail")
      assertTestEventsDoesNotContain("org.example.TestCase", "testSuccess")
      assertTestEventsContain("org.example.TestCase", "testFail")
      assertTestEventsDoesNotContain("com.intellij.TestCase", "testSuccess")
      assertTestEventsDoesNotContain("com.intellij.TestCase", "testFail")

      if (isGradleAtLeast("4.1")) {
        executeTasks(":test --tests com.intellij.*")
        assertTestEventsDoesNotContain("org.example.TestCase", "testSuccess")
        assertTestEventsDoesNotContain("org.example.TestCase", "testFail")
        assertTestEventsDoesNotContain("com.intellij.TestCase", "testSuccess")
        assertTestEventsDoesNotContain("com.intellij.TestCase", "testFail")
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test test execution status`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |
        |public class TestCase {
        |
        |  @$jUnitTestAnnotationClass
        |  public void successTest() {}
        |
        |  @$jUnitTestAnnotationClass
        |  public void failedTest() { 
        |    throw new RuntimeException(); 
        |  }
        |
        |  @$jUnitIgnoreAnnotationClass
        |  @$jUnitTestAnnotationClass
        |  public void ignoredTest() {}
        |}
      """.trimMargin())

      executeTasks(":test")
      assertTestTreeView {
        assertNode("TestCase") {
          assertNode("failedTest")
          assertNode("ignoredTest")
          assertNode("successTest")
        }
      }
      assertTestEventCount("TestCase", 1, 1, 0, 0, 0, 0)
      assertTestEventCount("successTest", 0, 0, 1, 1, 0, 0)
      assertTestEventCount("failedTest", 0, 0, 1, 1, 1, 0)
      assertTestEventCount("ignoredTest", 0, 0, 1, 1, 0, 1)
    }
  }
}
