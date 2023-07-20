// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.producer

import org.jetbrains.plugins.gradle.settings.TestRunner
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.junit.Test

class TestMethodGradleConfigurationProducerTest : GradleConfigurationProducerTestCase() {

  @Test
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
    importProject {
      withJavaPlugin()
      withJUnit4()
    }
    assertModules("project", "project.main", "project.test")

    currentExternalProjectSettings.testRunner = TestRunner.GRADLE
    assertTestFilter("package1.T1Test", null, ":test --tests \"package1.T1Test\"")
    assertTestFilter("package1.T1Test", "testFoo", ":test --tests \"package1.T1Test.testFoo[*]\"")
    assertParameterizedLocationTestFilter("package1.T1Test", "testFoo", "param1", ":test --tests \"package1.T1Test.testFoo[*param1*]\"")
    assertParameterizedLocationTestFilter("package1.T1Test", "testFoo", "param2", ":test --tests \"package1.T1Test.testFoo[*param2*]\"")
    assertParameterizedLocationTestFilter("package1.T1Test", "testFoo", "[1]", ":test --tests \"package1.T1Test.testFoo[1]\"")
    assertParameterizedLocationTestFilter("package1.T1Test", "testFoo", "[2]", ":test --tests \"package1.T1Test.testFoo[2]\"")
    assertTestFilter("package1.T2Test", null, ":test --tests \"package1.T2Test\"")
    assertTestFilter("package1.T2Test", "testFoo2", ":test --tests \"package1.T2Test.testFoo2[*]\"")
    assertParameterizedLocationTestFilter("package1.T2Test", "testFoo2", "param1", ":test --tests \"package1.T2Test.testFoo2[*param1*]\"")
    assertParameterizedLocationTestFilter("package1.T2Test", "testFoo2", "param2", ":test --tests \"package1.T2Test.testFoo2[*param2*]\"")
    assertParameterizedLocationTestFilter("package1.T2Test", "testFoo2", "[1]", ":test --tests \"package1.T2Test.testFoo2[1]\"")
    assertParameterizedLocationTestFilter("package1.T2Test", "testFoo2", "[2]", ":test --tests \"package1.T2Test.testFoo2[2]\"")
  }
}