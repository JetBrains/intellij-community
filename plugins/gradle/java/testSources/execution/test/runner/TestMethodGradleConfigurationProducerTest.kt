// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer.findAllTestsTaskToRun
import org.jetbrains.plugins.gradle.importing.GradleBuildScriptBuilderEx
import org.jetbrains.plugins.gradle.settings.TestRunner
import org.junit.Assert
import org.junit.Test

class TestMethodGradleConfigurationProducerTest : GradleConfigurationProducerTestCase() {

  @Test
  @Throws(Exception::class)
  fun `test junit parameterized tests`() {
    createProjectSubFile("src/test/java/package1/T1Test.java", """
      package package1;
      import org.junit.Test;
      import org.junit.runner.RunWith;
      import org.junit.runners.Parameterized;

      import java.util.Arrays;
      import java.util.Collection;

      @RunWith(Parameterized.class)
      public class T1Test {
          @org.junit.runners.Parameterized.Parameter()
          public String param;

          @Parameterized.Parameters(name = "{0}")
          public static Collection<Object[]> data() {
              return Arrays.asList(new Object[][]{{"param1"}, {"param2"}});
          }

          @Test
          public void testFoo() {
          }
      }
    """.trimIndent())
    createProjectSubFile("src/test/java/package1/T2Test.java", """
      package package1;
      import org.junit.Test;

      public class T2Test extends T1Test {
          @Test
          public void testFoo2() {
          }
      }
    """.trimIndent())

    createProjectSubFile("settings.gradle", "")
    importProject("""
      apply plugin: 'java'
      dependencies {
        testCompile 'junit:junit:4.11'
      }
""")
    assertModules("project", "project.main", "project.test")

    currentExternalProjectSettings.testRunner = TestRunner.GRADLE
    assertTestFilter("package1.T1Test", null, "--tests \"package1.T1Test\"")
    assertTestFilter("package1.T1Test", "testFoo", "--tests \"package1.T1Test.testFoo[*]\" ")
    assertParameterizedLocationTestFilter("package1.T1Test", "testFoo", "param1", "--tests \"package1.T1Test.testFoo[*param1*]\" ")
    assertParameterizedLocationTestFilter("package1.T1Test", "testFoo", "param2", "--tests \"package1.T1Test.testFoo[*param2*]\" ")
    assertTestFilter("package1.T2Test", null, "--tests \"package1.T2Test\"")
    assertTestFilter("package1.T2Test", "testFoo2", "--tests \"package1.T2Test.testFoo2[*]\" ")
    assertParameterizedLocationTestFilter("package1.T2Test", "testFoo2", "param1", "--tests \"package1.T2Test.testFoo2[*param1*]\" ")
    assertParameterizedLocationTestFilter("package1.T2Test", "testFoo2", "param2", "--tests \"package1.T2Test.testFoo2[*param2*]\" ")
  }

  @Test
  fun `test pattern gradle run configuration`() {
    val virtualFile = createProjectSubFile("src/test/groovy/Test.groovy", """
      import static org.junit.Assert.fail
      import org.junit.Test

      class MyGroovyTest {
          @Test
          void 'Don\\\'t use single . quo\\"tes'() {
              fail()
          }
          @Test
          void test1() {
              fail()
          }
          @Test
          void test2() {
              fail()
          }
      }
    """.trimIndent())
    val buildScript = GradleBuildScriptBuilderEx()
      .withGroovyPlugin("2.4.14")
      .withJUnit("4.12")
    importProject(buildScript.generate())

    currentExternalProjectSettings.testRunner = TestRunner.GRADLE
    val filter = "--tests \"MyGroovyTest.Don\\'t use single * quo\\*tes\"  --tests \"MyGroovyTest.test2\" "
    assertTestPatternFilter(filter, virtualFile, "Don\\'t use single . quo\\\"tes", "test2")
  }

  @Test
  fun `test intellij tests finding`() {
    val buildScript = GradleBuildScriptBuilderEx()
      .withJavaPlugin()
      .withJUnit("4.12")
      .addPrefix("""
        sourceSets {
          foo.java.srcDirs = ["foo-src", "foo-other-src"]
          foo.java.outputDir = file('far/away/bin')
          foo.compileClasspath += sourceSets.test.runtimeClasspath
          bar.java.srcDirs = ["bar-src", "bar-other-src"]
          bar.java.outputDir = file('far/far/away/build')
          bar.compileClasspath += sourceSets.test.runtimeClasspath
        }
      """.trimIndent())
      .addPrefix("""
        task 'foo test task'(type: Test) {
          testClassesDirs = sourceSets.foo.output.classesDirs
          classpath += sourceSets.foo.runtimeClasspath
        }
        task 'super foo test task'(type: Test) {
          testClassesDirs = sourceSets.foo.output.classesDirs
          classpath += sourceSets.foo.runtimeClasspath
        }
      """.trimIndent())
    importProject(buildScript.generate())
    assertTestTasks(createProjectSubFile("foo-src/package/TestCase.java", "class TestCase"),
                    listOf(":cleanFoo test task", ":foo test task"),
                    listOf(":cleanSuper foo test task", ":super foo test task"))
    assertTestTasks(createProjectSubFile("foo-other-src/package/TestCase.java", "class TestCase"),
                    listOf(":cleanFoo test task", ":foo test task"),
                    listOf(":cleanSuper foo test task", ":super foo test task"))
  }

  private fun assertTestTasks(source: VirtualFile, vararg expected: List<String>) {
    val tasks = findAllTestsTaskToRun(source, myProject)
    Assert.assertEquals(expected.toList(), tasks)
  }
}