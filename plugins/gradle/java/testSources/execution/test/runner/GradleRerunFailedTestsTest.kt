// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.api.Assertions
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

      executeTasks(":test --tests org.example.TestCase")
      assertTestExecutionTree("""
        |-[root]
        | -TestCase
        |  test1
        |  test2
      """.trimMargin())
      Assertions.assertTrue(performRerunFailedTestsAction())
      assertTestExecutionTree("""
        |-[root]
        | -TestCase
        |  test2
      """.trimMargin())
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

      executeTasks(":test --tests org.example.SubTestCase1")
      assertTestExecutionTree("""
        |-[root]
        | -SubTestCase1
        |  test1
        |  test2
      """.trimMargin())
      Assertions.assertTrue(performRerunFailedTestsAction())
      assertTestExecutionTree("""
        |-[root]
        | -SubTestCase1
        |  test2
      """.trimMargin())

      executeTasks(":test --tests org.example.SubTestCase2")
      assertTestExecutionTree("""
        |-[root]
        | -SubTestCase2
        |  test1
        |  test2
        |  test3
        |  test4
      """.trimMargin())
      Assertions.assertTrue(performRerunFailedTestsAction())
      assertTestExecutionTree("""
        |-[root]
        | -SubTestCase2
        |  test2
        |  test4
      """.trimMargin())
    }
  }
}
