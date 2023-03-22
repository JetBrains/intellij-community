// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.openapi.util.SystemInfo
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest

class GradleTestRunnerViewTest : GradleTestRunnerViewTestCase() {

  @ParameterizedTest
  @TargetVersions("4.7+")
  @AllGradleVersionsSource
  fun `test grouping events of the same suite comes from different tasks`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/AppTest.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class AppTest {
        |  @Test
        |  public void test() {
        |    String prop = System.getProperty("prop");
        |    if (prop != null) {
        |      throw new RuntimeException(prop);
        |    }
        |  }
        |}
      """.trimMargin())
      appendText("build.gradle", """
        |tasks.register('additionalTest', Test) {
        |  testClassesDirs = sourceSets.test.output.classesDirs
        |  classpath = sourceSets.test.runtimeClasspath
        |  jvmArgs += "-Dprop='Test error message'"
        |
        |  useJUnitPlatform()
        |}
      """.trimMargin())

      executeTasks(":test :additionalTest")

      assertTestExecutionTree("""
        |-[root]
        | -AppTest
        |  test
        |  test
      """.trimMargin())
      assertBuildExecutionTreeContains(
        testLauncher = """
          |-
          | -failed
          |  :compileJava
          |  :processResources
          |  :classes
          |  :compileTestJava
          |  :processTestResources
          |  :testClasses
          |  -:test
          |   -Gradle Test Run :test
          |    -Gradle Test Executor 1
          |     -AppTest
          |      Test test()(org.example.AppTest)
          |  -:additionalTest
          |   -Gradle Test Run :additionalTest
          |    -Gradle Test Executor 2
          |     -AppTest
          |      -Test test()(org.example.AppTest)
          |       'Test error message'
          |  Test failed.
        """.trimMargin(),
        junit = """
          |-
          | -failed
          |  :compileJava
          |  :processResources
          |  :classes
          |  :compileTestJava
          |  :processTestResources
          |  :testClasses
          |  :test
          |  -:additionalTest
          |   There were failing tests. See the report at:
        """.trimMargin()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test console empty lines and output without eol at the end`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/AppTest.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class AppTest {
        |  @Test
        |  public void test() {
        |    System.out.println("test \n" + "output\n" + "\n" + "text");
        |  }
        |}
      """.trimMargin())
      prependText("build.gradle", """
        |buildscript {
        |  print("buildscript \n" + "output\n" + "\n" + "text\n")
        |}
      """.trimMargin())
      appendText("build.gradle", """
        |print("script output text without eol")
      """.trimMargin())

      executeTasks(":test")

      assertTestExecutionTree("""
        |-[root]
        | -AppTest
        |  test
      """.trimMargin())
      when {
        SystemInfo.isWindows ->
          assertTestExecutionConsoleContains("buildscript \n" + "output\n" + "\n" + "text\n")
        else ->
          assertTestExecutionConsoleContains("buildscript \n" + "output\n" + "text\n")
      }
      assertTestExecutionConsoleContains("script output text without eol")
      assertTestExecutionConsoleContains("test \n" + "output\n" + "\n" + "text\n")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test build tw output for Gradle test runner execution`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/AppTest.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class AppTest {
        |    @Test public void test() {}
        |}
      """.trimMargin())

      executeTasks(":test")

      assertTestExecutionTree("""
        |-[root]
        | -AppTest
        |  test
      """.trimMargin())
      assertBuildExecutionTree(
        testLauncher = """
          |-
          | -successful
          |  :compileJava
          |  :processResources
          |  :classes
          |  :compileTestJava
          |  :processTestResources
          |  :testClasses
          |  -:test
          |   -Gradle Test Run :test
          |    -Gradle Test Executor 1
          |     -AppTest
          |      Test test()(org.example.AppTest)
        """.trimMargin(),
        junit = """
          |-
          | -successful
          |  :compileJava
          |  :processResources
          |  :classes
          |  :compileTestJava
          |  :processTestResources
          |  :testClasses
          |  :test
        """.trimMargin()
      )
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

      testEventCounter.assertTestEvents("TestCase", 1, 1, 0, 0, 0, 0)
      testEventCounter.assertTestEvents("successTest", 0, 0, 1, 1, 0, 0)
      testEventCounter.assertTestEvents("failedTest", 0, 0, 1, 1, 1, 0)
      testEventCounter.assertTestEvents("ignoredTest", 0, 0, 1, 1, 0, 1)
    }
  }

  @ParameterizedTest
  @TargetVersions("5.6+")
  @AllGradleVersionsSource
  fun `navigation for unrolled spock 2 tests`(gradleVersion: GradleVersion) {
    test(gradleVersion, GROOVY_SPOCK_FIXTURE) {
      writeText("src/test/groovy/org/example/HelloSpockSpec.groovy", """
        |package org.example
        |
        |import spock.lang.Specification
        |
        |class HelloSpockSpec extends Specification {
        |
        |  def "length of #name is #length"() {
        |    expect:
        |    name.size() != length
        |
        |    where:
        |    name     | length
        |    "Spock"  | 5
        |  }
        |}
      """.trimMargin())

      executeTasks(":test")

      val rootTestProxy = testExecutionConsole.resultsViewer.root
      val classTestProxy = rootTestProxy.children.single()
      val methodTestProxy = classTestProxy.children.single()
      val errorTestProxy = methodTestProxy.children.single()
      Assertions.assertEquals("HelloSpockSpec", classTestProxy.name)
      Assertions.assertEquals("length of #name is #length", methodTestProxy.name)
      Assertions.assertEquals("length of Spock is 5", errorTestProxy.name)
      Assertions.assertEquals("HelloSpockSpec", classTestProxy.psiClass.name)
      Assertions.assertEquals("length of #name is #length", methodTestProxy.psiMethod.name)
      Assertions.assertEquals("length of #name is #length", errorTestProxy.psiMethod.name)
    }
  }

  companion object {

    private val GROOVY_SPOCK_FIXTURE = GradleTestFixtureBuilder.create("groovy-spock-junit-fixture") { gradleVersion ->
      withSettingsFile {
        setProjectName("groovy-spock-junit-fixture")
      }
      withBuildFile(gradleVersion) {
        withGroovyPlugin("3.0.0")
        addTestImplementationDependency(call("platform", "org.spockframework:spock-bom:2.1-groovy-3.0"))
        addTestImplementationDependency("org.spockframework:spock-core:2.1-groovy-3.0")
        withJUnit()
      }
      withDirectory("src/main/groovy")
      withDirectory("src/test/groovy")
    }
  }
}
