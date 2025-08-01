// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil


@TestDataPath($$"$CONTENT_ROOT/testData/threadingModelHelper/")
class LockReqsUnitTest : BasePlatformTestCase() {

  private lateinit var analyzer: LockReqsAnalyzer

  override fun setUp() {
    super.setUp()
    analyzer = LockReqsAnalyzer()
    myFixture.addFileToProject("testutils/RequiresReadLock.java", """
        package testutils;
        public @interface RequiresReadLock {}
        """.trimIndent())
    myFixture.addFileToProject("testutils/ThreadingAssertions.java", """
      package testutils;
      public class ThreadingAssertions {
        public static void assertReadAccess() {}
      }
      """.trimIndent())
    myFixture.addFileToProject("testutils/ExpectedPath.java", """
      package testutils;
      import java.lang.annotation.*;
      @Target(ElementType.TYPE)
      @Retention(RetentionPolicy.RUNTIME)
      public @interface ExpectedPath {
        String value();
      }
      """.trimIndent())
  }

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "threadingModelHelper/"

  fun testMethodsInDifferentClassesInline() {
    val testFileContent = """
        package test;
        
        import com.intellij.util.concurrency.annotations.RequiresReadLock;
        
        public class MethodsInDifferentClasses {
            public void testMethod() {
                Helper helper = new Helper();
                helper.helperMethod();
            }
        }
        
        class Helper {
            public void helperMethod() {
                Service service = new Service();
                service.serviceMethod();
            }
        }
        
        class Service {
            @RequiresReadLock
            public void serviceMethod() {
                System.out.println("Service method");
            }
        }

    """.trimIndent()

    // Write the content to a temporary file
    val psiJavaFile = myFixture.configureByText("TestFile.java", testFileContent) as PsiJavaFile

    // Create the analyzer
    val analyzer = LockReqsAnalyzer()

    // Get all classes from the file
    println("Classes in file: ${psiJavaFile.classes.map { it.name }}")

    // Find the main test class
    val testClass = psiJavaFile.classes.find { it.name == "MethodsInDifferentClasses" }
    assertNotNull("Could not find MethodsInDifferentClasses", testClass)
    println("Found test class: ${testClass?.name}")

    // Extract expected paths
    val expectedPathAnnotations = testClass!!.annotations
    println("Annotations on test class: ${expectedPathAnnotations.map { it.qualifiedName }}")

    val expectedPaths = listOf("MethodsInDifferentClasses.testMethod -> Helper.helperMethod -> Service.serviceMethod -> @RequiresReadLock")

    println("Expected paths: $expectedPaths")

    // Find the test method
    val sourceMethod = testClass.findMethodsByName("testMethod", false).firstOrNull()
    assertNotNull("Could not find testMethod", sourceMethod)
    println("Found method: ${sourceMethod?.name}")

    // Analyze the method
    val executionPaths = analyzer.analyzeMethod(sourceMethod!!)
    println("Found ${executionPaths.size} execution paths")

    val actualPaths = executionPaths.map { it.pathString }

    assertEquals("Paths don't match!", expectedPaths, actualPaths)
  }

  fun testNoLockRequirements() {
    doTest()
  }

  fun testAnnotationInChain() {
    doTest()
  }

  fun testAssertionInNestedBlock() {
    doTest()
  }

  fun testBothAnnotationAndAssertion() {
    doTest()
  }

  fun testCyclicRecursiveCalls() {
    doTest()
  }

  fun testMethodsInDifferentClasses() {
    doTest()
  }

  fun testMultipleAssertionsInMethod() {
    doTest()
  }

  fun testLambdaWithMethodReference() {
    doTest()
  }

  private fun doTest() {
    val fileName = "${getTestName(false)}.java"
    val sourceMethodName = "testMethod"
    val psiJavaFile = myFixture.configureByFile(fileName) as PsiJavaFile

    val testClass = psiJavaFile.classes.first()
    val expectedPaths = testClass.annotations
      .filter { it.qualifiedName == "test.ExpectedPath" }
      .mapNotNull { it.findAttributeValue("value")?.text?.removeSurrounding("\"") }
      .sorted()

    val sourceMethod = testClass.findMethodsByName(sourceMethodName, false).first()
    val actualPaths = analyzer.analyzeMethod(sourceMethod).map { it.pathString }.sorted()

    assertEquals(expectedPaths, actualPaths)
  }
}
