// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.params.ParameterizedTest

class GradleRerunFailedTestsTest : GradleRerunFailedTestsTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test return failed tests`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test1() {}
        |  @Test public void test2() { throw new RuntimeException(); }
        |}
      """.trimMargin())

      executeTasks(":test --tests org.example.TestCase", isRunAsTest = true)
      assertTestViewTree {
        assertNode("TestCase") {
          assertNode("test1")
          assertNode("test2")
        }
      }
      rerunFailedTests()
      assertTestViewTree {
        assertNode("TestCase") {
          assertNode("test2")
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test return failed tests abstract superclass`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public abstract class TestCase {
        |  @Test public void test1() {}
        |  @Test public void test2() { throw new RuntimeException(); }
        |}
      """.trimMargin())
      writeText("src/test/java/org/example/SubTestCase1.java", """
        |package org.example;
        |public class SubTestCase1 extends TestCase {}
      """.trimMargin())
      writeText("src/test/java/org/example/SubTestCase2.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class SubTestCase2 extends TestCase {
        |   @Test public void test3() {}
        |   @Test public void test4() { throw new RuntimeException(); }
        |}
      """.trimMargin())

      executeTasks(":test --tests org.example.SubTestCase1", isRunAsTest = true)
      assertTestViewTree {
        assertNode("SubTestCase1") {
          assertNode("test1")
          assertNode("test2")
        }
      }
      rerunFailedTests()
      assertTestViewTree {
        assertNode("SubTestCase1") {
          assertNode("test2")
        }
      }

      executeTasks(":test --tests org.example.SubTestCase2", isRunAsTest = true)
      assertTestViewTree {
        assertNode("SubTestCase2") {
          assertNode("test1")
          assertNode("test2")
          assertNode("test3")
          assertNode("test4")
        }
      }
      rerunFailedTests()
      assertTestViewTree {
        assertNode("SubTestCase2") {
          assertNode("test2")
          assertNode("test4")
        }
      }
    }
  }
}
