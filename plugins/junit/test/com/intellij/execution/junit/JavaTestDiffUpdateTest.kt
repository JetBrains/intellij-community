// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import org.intellij.lang.annotations.Language

class JavaTestDiffUpdateTest : JvmTestDiffUpdateTest() {
  @Suppress("SameParameterValue")
  private fun checkAcceptDiff(
    @Language("Java") before: String,
    @Language("Java") after: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String
  ) = checkAcceptDiff(before, after, testClass, testName, expected, actual, stackTrace, "java")

  fun `test failure when stacktrace is corrupted`() {
    checkAcceptDiff("""
      import org.junit.Assert;
      import org.junit.Test;
      
      public class MyJUnitTest {
          @Test
          public void testFoo() {
              Assert.assertEquals("expected", "actual");
          }
      }
    """.trimIndent(), """
      import org.junit.Assert;
      import org.junit.Test;
      
      public class MyJUnitTest {
          @Test
          public void testFoo() {
              Assert.assertEquals("expected", "actual");
          }
      }
    """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
      	at org.junit.Assert.assertEquals(Assert.java:117)
      	at org.junit.Assert.assertEquals(Assert.java:146)
      	unexpected input
    """.trimIndent())
  }

  fun `test success when stacktrace is polluted`() {
    checkAcceptDiff("""
      import org.junit.Assert;
      import org.junit.Test;
      
      public class MyJUnitTest {
          @Test
          public void testFoo() {
              Assert.assertEquals("expected", "actual");
          }
      }
    """.trimIndent(), """
      import org.junit.Assert;
      import org.junit.Test;
      
      public class MyJUnitTest {
          @Test
          public void testFoo() {
              Assert.assertEquals("actual", "actual");
          }
      }
    """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
        unexpected input
      	at org.junit.Assert.assertEquals(Assert.java:117)
        unexpected input
      	at org.junit.Assert.assertEquals(Assert.java:146)
      	at MyJUnitTest.testFoo(MyJUnitTest.java:7)
        unexpected input
    """.trimIndent())
  }

  fun `test string literal diff`() {
    checkAcceptDiff("""
      import org.junit.Assert;
      import org.junit.Test;
      
      public class MyJUnitTest {
          @Test
          public void testFoo() {
              Assert.assertEquals("expected", "actual");
          }
      }
    """.trimIndent(), """
      import org.junit.Assert;
      import org.junit.Test;
      
      public class MyJUnitTest {
          @Test
          public void testFoo() {
              Assert.assertEquals("actual", "actual");
          }
      }
    """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
      	at org.junit.Assert.assertEquals(Assert.java:117)
      	at org.junit.Assert.assertEquals(Assert.java:146)
      	at MyJUnitTest.testFoo(MyJUnitTest.java:7)
    """.trimIndent())
  }

  fun `test string literal diff with escape`() {
    checkAcceptDiff("""
      import org.junit.Assert;
      import org.junit.Test;
      
      public class MyJUnitTest {
          @Test
          public void testFoo() {
              Assert.assertEquals("expected", "actual\"");
          }
      }
    """.trimIndent(), """
      import org.junit.Assert;
      import org.junit.Test;
      
      public class MyJUnitTest {
          @Test
          public void testFoo() {
              Assert.assertEquals("actual\"", "actual\"");
          }
      }
    """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual\"", """
      	at org.junit.Assert.assertEquals(Assert.java:117)
      	at org.junit.Assert.assertEquals(Assert.java:146)
      	at MyJUnitTest.testFoo(MyJUnitTest.java:7)
    """.trimIndent())
  }

  fun `test parameter reference diff`() {
    checkAcceptDiff("""
      import org.junit.Assert;
      import org.testng.annotations.Test;
      
      public class MyJUnitTest {
          @Test
          void testFoo() {
              doTest("expected");
          }
          
          void doTest(String expected) {
              Assert.assertEquals(expected, "actual");
          }
      }
    """.trimIndent(), """
      import org.junit.Assert;
      import org.testng.annotations.Test;
      
      public class MyJUnitTest {
          @Test
          void testFoo() {
              doTest("actual");
          }
          
          void doTest(String expected) {
              Assert.assertEquals(expected, "actual");
          }
      }
    """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
      at org.junit.Assert.assertEquals(Assert.java:117)
      at org.junit.Assert.assertEquals(Assert.java:146)
      at MyJUnitTest.doTest(MyJUnitTest.java:11)
      at MyJUnitTest.testFoo(MyJUnitTest.java:7)
    """.trimIndent())
  }

  fun `test parameter reference diff multiple methods on same line`() {
    checkAcceptDiff("""
      import org.junit.Assert;
      import org.testng.annotations.Test;
      
      public class MyJUnitTest {
          @Test
          void testFoo() {
              doAnotherTest(); doTest("expected"); 
          }
          
          void doTest(String expected) {
              Assert.assertEquals(expected, "actual");
          }
          
          void doAnotherTest() { }
      }
    """.trimIndent(), """
      import org.junit.Assert;
      import org.testng.annotations.Test;
      
      public class MyJUnitTest {
          @Test
          void testFoo() {
              doAnotherTest(); doTest("actual"); 
          }
          
          void doTest(String expected) {
              Assert.assertEquals(expected, "actual");
          }
          
          void doAnotherTest() { }
      }
    """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
      at org.junit.Assert.assertEquals(Assert.java:117)
      at org.junit.Assert.assertEquals(Assert.java:146)
      at MyJUnitTest.doTest(MyJUnitTest.java:11)
      at MyJUnitTest.testFoo(MyJUnitTest.java:7)
    """.trimIndent())
  }

  fun `test local variable reference diff`() {
    checkAcceptDiff("""
      import org.junit.Assert;
      import org.testng.annotations.Test;
      
      public class MyJUnitTest {
          @Test
          void testFoo() {
              String exp = "expected";
              Assert.assertEquals(exp, "actual");
          }
      }
    """.trimIndent(), """
      import org.junit.Assert;
      import org.testng.annotations.Test;
      
      public class MyJUnitTest {
          @Test
          void testFoo() {
              String exp = "actual";
              Assert.assertEquals(exp, "actual");
          }
      }
    """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
      at org.junit.Assert.assertEquals(Assert.java:117)
      at org.junit.Assert.assertEquals(Assert.java:146)
      at MyJUnitTest.testFoo(MyJUnitTest.java:8)
    """.trimIndent())
  }

  fun `test field reference diff`() {
    checkAcceptDiff("""
      import org.junit.Assert;
      import org.testng.annotations.Test;
      
      public class MyJUnitTest {
          private String exp = "expected";
          
          @Test
          void testFoo() {
              Assert.assertEquals(exp, "actual");
          }
      }
    """.trimIndent(), """
      import org.junit.Assert;
      import org.testng.annotations.Test;
      
      public class MyJUnitTest {
          private String exp = "actual";
          
          @Test
          void testFoo() {
              Assert.assertEquals(exp, "actual");
          }
      }
    """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
      at org.junit.Assert.assertEquals(Assert.java:117)
      at org.junit.Assert.assertEquals(Assert.java:146)
      at MyJUnitTest.testFoo(MyJUnitTest.java:9)
    """.trimIndent())
  }
}