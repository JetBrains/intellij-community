// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnitMixedFrameworkInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaJUnitMixedFrameworkInspectionTest : JUnitMixedFrameworkInspectionTestBase() {
  fun `test no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class JUnit3Test extends junit.framework.TestCase {
        public void testFoo() { }
      }      
    """.trimIndent(), fileName = "JUnit3Test")
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class JUnit4Test {
        @org.junit.Test
        public void testFoo() { }
      }
    """.trimIndent(), fileName = "JUnit4Test")
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class JUnit5Test {
        @org.junit.jupiter.api.Test
        public void testFoo() { }
      }      
    """.trimIndent(), fileName = "JUnit5Test")
    myFixture.addFileToProject("JUnit5TestCase.java", """
      public class JUnit5TestCase {
        @org.junit.jupiter.api.BeforeAll
        public void beforeAll() { }
      }      
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class JUnit5Test extends JUnit5TestCase {
        @org.junit.jupiter.api.Test
        public void testFoo() { }
      }      
    """.trimIndent(), fileName = "JUnit5Test")
  }

  fun `test highlighting junit 3 test case with junit 4 test`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class MyTest extends junit.framework.TestCase {
        @org.junit.Test
        public void <warning descr="Method 'testFoo()' annotated with '@Test' inside class extending JUnit 3 TestCase">testFoo</warning>() { }
      }
    """.trimIndent(), fileName = "MyTest")
  }

  fun `test highlighting junit 3 test case with junit 5 test`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class MyTest extends junit.framework.TestCase {
        @org.junit.jupiter.api.Test
        public void <warning descr="Method 'testFoo()' annotated with '@Test' inside class extending JUnit 3 TestCase">testFoo</warning>() { }
      }
    """.trimIndent(), fileName = "MyTest")
  }

  fun `test junit 3 test case with junit 4 test remove before each annotation quickfix`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      public class MyTest extends junit.framework.TestCase {
        @org.junit.jupiter.api.BeforeEach
        public void do<caret>Something() { }
      }
    """.trimIndent(), """
      public class MyTest extends junit.framework.TestCase {
        public void doSomething() { }
      }
    """.trimIndent(), fileName = "MyTest", hint = "Remove '@BeforeEach' annotation", testPreview = true)
  }


  fun `test junit 3 test case with junit 4 test remove test annotation without rename quickfix`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      public class MyTest extends junit.framework.TestCase {
        @org.junit.Test
        public void test<caret>Foo() { }
      }
    """.trimIndent(), """
      public class MyTest extends junit.framework.TestCase {
        public void testFoo() { }
      }
    """.trimIndent(), fileName = "MyTest", hint = "Remove '@Test' annotation", testPreview = true)
  }

  fun `test junit 3 test case with junit 4 test remove test annotation and rename quickfix`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      public class MyTest extends junit.framework.TestCase {
        @org.junit.Test
        public void f<caret>oo() { }
      }
    """.trimIndent(), """
      public class MyTest extends junit.framework.TestCase {
        public void testFoo() { }
      }
    """.trimIndent(), fileName = "MyTest", hint = "Remove '@Test' annotation", testPreview = true)
  }

  fun `test junit 3 test case with junit 4 test remove ignore annotation and rename quickfix`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      public class MyTest extends junit.framework.TestCase {
        @org.junit.Ignore
        public void test<caret>Foo() { }
      }
    """.trimIndent(), """
      public class MyTest extends junit.framework.TestCase {
        public void _testFoo() { }
      }
    """.trimIndent(), fileName = "MyTest", hint = "Remove '@Ignore' annotation", testPreview = true)
  }

  fun `test highlighting junit 4 test case with junit 5 test`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class MyTest {
        @org.junit.jupiter.api.Test
        public void <warning descr="Method 'testFoo()' annotated with '@Test' inside class extending JUnit 4 TestCase">testFoo</warning>() { }
        
        @org.junit.Test
        public void testBar() { }
        
        @org.junit.Test
        public void testFooBar() { }
      }
    """.trimIndent(), fileName = "MyTest")
  }

  fun `test highlighting junit 5 test case with junit 4 test`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class MyTest {
        @org.junit.Test
        public void <warning descr="Method 'testFoo()' annotated with '@Test' inside class extending JUnit 5 TestCase">testFoo</warning>() { }
        
        @org.junit.jupiter.api.Test
        public void testBar() { }
        
        @org.junit.jupiter.api.Test
        public void testFooBar() { }
      }
    """.trimIndent(), fileName = "MyTest")
  }


  fun `test junit 5 test case with junit 4 quickfix`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      public class MyTest {
        @org.junit.Test
        public void test<caret>Foo() { }
        
        @org.junit.jupiter.api.Test
        public void testBar() { }
        
        @org.junit.jupiter.api.Test
        public void testFooBar() { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Test;
      
      class MyTest {
        @Test
        void testFoo() { }
        
        @org.junit.jupiter.api.Test
        public void testBar() { }
        
        @org.junit.jupiter.api.Test
        public void testFooBar() { }
      }
    """.trimIndent(), fileName = "MyTest", hint = "Migrate to JUnit 5")
  }

  fun `test highlighting junit 5 test case with junit 4 super class`() {
    myFixture.addFileToProject("JUnit4TestCase.java", """
      public class JUnit4TestCase {
        @org.junit.BeforeClass
        public void beforeClass() { }
      }      
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class MyTest extends JUnit4TestCase {
        @org.junit.jupiter.api.Test
        public void <warning descr="Method 'testFoo()' annotated with '@Test' inside class extending JUnit 4 TestCase">testFoo</warning>() { }
      }
    """.trimIndent(), fileName = "MyTest")
  }
}