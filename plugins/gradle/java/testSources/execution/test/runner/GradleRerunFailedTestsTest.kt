// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder.Companion.buildscript
import org.junit.Test

class GradleRerunFailedTestsTest : GradleRerunFailedTestsTestCase() {
  @Test
  fun `test return failed tests`() {
    val virtualFile = createProjectSubFile("src/test/java/org/example/TestCase.java", """
      package org.example;
      import $jUnitTestAnnotationClass;
      public class TestCase {
        @Test public void test1() {}
        @Test public void test2() { throw new RuntimeException(); }
        @Test public void test3() {}
        @Test public void test4() { throw new RuntimeException(); }
        @Test public void test5() { throw new RuntimeException(); }
        @Test public void test6() {}
        @Test public void test7() {}
      }
    """.trimIndent())
    createProjectSubFile("build.gradle", buildscript {
      withJUnit()
      withJavaPlugin()
    })
    importProject()

    execute(":test --tests org.example.TestCase")
    assertThat(getJUnitTestsExecutionTree())
      .isEqualTo("""
        -[root]
         -TestCase
          test1
          test2
          test3
          test4
          test5
          test6
          test7
      """.trimIndent())
    assertTrue(performRerunFailedTestsAction())
    assertThat(getJUnitTestsExecutionTree())
      .isEqualTo("""
        -[root]
         -TestCase
          test2
          test4
          test5
      """.trimIndent())

    virtualFile.replaceFirst("{ throw new RuntimeException(); }", "{}")
    assertTrue(performRerunFailedTestsAction())
    assertThat(getJUnitTestsExecutionTree())
      .isEqualTo("""
        -[root]
         -TestCase
          test2
          test4
          test5
      """.trimIndent())
    assertTrue(performRerunFailedTestsAction())
    assertThat(getJUnitTestsExecutionTree())
      .isEqualTo("""
        -[root]
         -TestCase
          test4
          test5
      """.trimIndent())

    virtualFile.replaceFirst("{ throw new RuntimeException(); }", "{}")
    virtualFile.replaceFirst("{ throw new RuntimeException(); }", "{}")
    assertTrue(performRerunFailedTestsAction())
    assertThat(getJUnitTestsExecutionTree())
      .isEqualTo("""
        -[root]
         -TestCase
          test4
          test5
      """.trimIndent())
    assertFalse(performRerunFailedTestsAction())
  }

  @Test
  fun `test return failed tests abstract superclass`() {
    createProjectSubFile("src/test/java/org/example/TestCase.java", """
      package org.example;
      import $jUnitTestAnnotationClass;
      public abstract class TestCase {
        @Test public void test1() {}
        @Test public void test2() { throw new RuntimeException(); }
        @Test public void test3() {}
        @Test public void test4() { throw new RuntimeException(); }
        @Test public void test5() { throw new RuntimeException(); }
        @Test public void test6() {}
        @Test public void test7() {}
      }
    """.trimIndent())
    createProjectSubFile("src/test/java/org/example/SubTestCase.java", """
      package org.example;
      public class SubTestCase extends TestCase {
      }
    """.trimIndent())
    createProjectSubFile("build.gradle", buildscript {
      withJUnit()
      withJavaPlugin()
    })
    importProject()

    execute(":test --tests org.example.SubTestCase")
    assertThat(getJUnitTestsExecutionTree())
      .isEqualTo("""
        -[root]
         -SubTestCase
          test1
          test2
          test3
          test4
          test5
          test6
          test7
      """.trimIndent())
    assertTrue(performRerunFailedTestsAction())
    assertThat(getJUnitTestsExecutionTree())
      .isEqualTo("""
        -[root]
         -SubTestCase
          test2
          test4
          test5
      """.trimIndent())

    createProjectSubFile("src/test/java/org/example/SubTestCase1.java", """
      package org.example;
      import $jUnitTestAnnotationClass;
      public class SubTestCase1 extends TestCase {
         @Test public void test8() { throw new RuntimeException(); }
         @Test public void test9() {}
      }
    """.trimIndent())
    execute(":test --tests org.example.SubTestCase1")
    assertThat(getJUnitTestsExecutionTree())
      .isEqualTo("""
         -[root]
          -SubTestCase1
           test1
           test2
           test3
           test4
           test5
           test6
           test7
           test8
           test9
       """.trimIndent())
    assertTrue(performRerunFailedTestsAction())
    assertThat(getJUnitTestsExecutionTree())
      .isEqualTo("""
         -[root]
          -SubTestCase1
           test2
           test4
           test5
           test8
       """.trimIndent())
  }
}
