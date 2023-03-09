// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import com.intellij.openapi.editor.Document
import org.intellij.lang.annotations.Language

@Suppress("AssertBetweenInconvertibleTypes", "NewClassNamingConvention", "SameParameterValue")
class JavaTestDiffUpdateTest : JvmTestDiffUpdateTest() {
  @Suppress("SameParameterValue")
  private fun checkHasNoDiff(
    @Language("Java") before: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String
  ) = checkHasNoDiff(before, testClass, testName, expected, actual, stackTrace, fileExt)

  @Suppress("SameParameterValue")
  private fun checkAcceptFullDiff(
    @Language("Java") before: String,
    @Language("Java") after: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String
  ) = checkAcceptFullDiff(before, after, testClass, testName, expected, actual, stackTrace, fileExt)

  private fun checkPhysicalDiff(
    @Language("Java") before: String,
    @Language("Java") after: String,
    diffAfter: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String,
    change: (Document) -> Unit
  ) = checkPhysicalDiff(before, after, diffAfter, testClass, testName, expected, actual, stackTrace, fileExt, change)

  fun `test failure when stacktrace is corrupted`() {
    checkAcceptFullDiff("""
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

  fun `test accept string literal diff`() {
    checkAcceptFullDiff("""
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

  fun `test accept string literal diff with actual call`() {
    checkAcceptFullDiff("""
      import org.junit.Assert;
      import org.junit.Test;
        
      public class MyJUnitTest {
        @Test
        public void testFoo() {
              Assert.assertEquals("expected", getActual(getActual(getActual("actual"))));
          }
      
          private static String getActual(String str) {
              return str;
          }
      }
    """.trimIndent(), """
      import org.junit.Assert;
      import org.junit.Test;
        
      public class MyJUnitTest {
        @Test
        public void testFoo() {
              Assert.assertEquals("actual", getActual(getActual(getActual("actual"))));
          }
      
          private static String getActual(String str) {
              return str;
          }
      }
    """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
      at org.junit.Assert.assertEquals(Assert.java:117)
      at org.junit.Assert.assertEquals(Assert.java:146)
      at MyJUnitTest.testFoo(MyJUnitTest.java:7)
    """.trimIndent())
  }

  fun `test accept string literal diff with carriage return and line feed in expected`() {
    checkAcceptFullDiff("""
      import org.junit.Assert;
      import org.junit.Test;
        
      public class MyJUnitTest {
        @Test
        public void testFoo() {
          Assert.assertEquals("expected\r\n", "actual");
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

  fun `test accept diff is not available when expected is not a string literal`() {
    checkHasNoDiff("""
      import org.junit.Assert;
      import org.junit.Test;
      
      public class MyJUnitTest {
          @Test
          public void testFoo() {
              Assert.assertEquals(true, "actual");
          }
      }
    """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
      at org.junit.Assert.fail(Assert.java:89)
      at org.junit.Assert.failNotEquals(Assert.java:835)
      at org.junit.Assert.assertEquals(Assert.java:120)
      at org.junit.Assert.assertEquals(Assert.java:146)
      at MyJUnitTest.testFoo(MyJUnitTest.java:7)
    """.trimIndent())
  }

  fun `test accept diff is not available when actual is not a string literal`() {
    checkHasNoDiff("""
      import org.junit.Assert;
      import org.junit.Test;
      
      public class MyJUnitTest {
          @Test
          public void testFoo() {
              Assert.assertEquals("actual", actual);
          }
      }
    """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
      at org.junit.Assert.fail(Assert.java:89)
      at org.junit.Assert.failNotEquals(Assert.java:835)
      at org.junit.Assert.assertEquals(Assert.java:120)
      at org.junit.Assert.assertEquals(Assert.java:146)
      at MyJUnitTest.testFoo(MyJUnitTest.java:7)
    """.trimIndent())
  }

  fun `test accept text block diff`() {
    checkAcceptFullDiff("""
      import org.junit.Assert;
      import org.junit.Test;
        
      public class MyJUnitTest {
        @Test
        public void testFoo() {
          Assert.assertEquals(""${'"'}
                  expected""${'"'}, "actual");
        }
      }
    """.trimIndent(), """
      import org.junit.Assert;
      import org.junit.Test;
        
      public class MyJUnitTest {
        @Test
        public void testFoo() {
          Assert.assertEquals(""${'"'}
                  actual""${'"'}, "actual");
        }
      }
    """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
      at org.junit.Assert.assertEquals(Assert.java:117)
      at org.junit.Assert.assertEquals(Assert.java:146)
      at MyJUnitTest.testFoo(MyJUnitTest.java:7)
    """.trimIndent())
  }

  fun `test physical string literal change sync`() {
    checkPhysicalDiff("""
      import org.junit.Assert;
      import org.junit.Test;
        
      public class MyJUnitTest {
        @Test
        public void testFoo() {
          Assert.assertEquals("expected<caret>", "actual");
        }
      }
    """.trimIndent(), after = """
      import org.junit.Assert;
      import org.junit.Test;
        
      public class MyJUnitTest {
        @Test
        public void testFoo() {
          Assert.assertEquals("expectedFoo", "actual");
        }
      }
    """.trimIndent(), diffAfter = "expectedFoo", "MyJUnitTest", "testFoo", "expected", "actual", """
      at org.junit.Assert.assertEquals(Assert.java:117)
      at org.junit.Assert.assertEquals(Assert.java:146)
      at MyJUnitTest.testFoo(MyJUnitTest.java:7)
    """.trimIndent()) { document -> document.insertString(myFixture.editor.caretModel.offset, "Foo") }
  }

  fun `test physical non-string literal change sync`() {
    checkPhysicalDiff(before = """
      import org.junit.Assert;
      import org.junit.Test;
        
      public class MyJUnitTest<caret> {
        @Test
        public void testFoo() {
          Assert.assertEquals("expected", "actual");
        }
      }
    """.trimIndent(), after = """
      import org.junit.Assert;
      import org.junit.Test;
        
      public class MyJUnitTestFoo {
        @Test
        public void testFoo() {
          Assert.assertEquals("expected", "actual");
        }
      }
      """.trimIndent(), diffAfter = "expected", "MyJUnitTest", "testFoo", "expected", "actual", """
        at org.junit.Assert.assertEquals(Assert.java:117)
        at org.junit.Assert.assertEquals(Assert.java:146)
        at MyJUnitTest.testFoo(MyJUnitTest.java:7)
      """.trimIndent()) { document -> document.insertString(myFixture.editor.caretModel.offset, "Foo") }
  }

  fun `test accept string literal diff with escape`() {
    checkAcceptFullDiff("""
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

  fun `test accept parameter reference diff`() {
    checkAcceptFullDiff("""
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

  fun `test accept parameter reference diff multiple calls on same line`() {
    checkAcceptFullDiff("""
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

  fun `test accept local variable reference diff`() {
    checkAcceptFullDiff("""
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

  fun `test accept field reference diff`() {
    checkAcceptFullDiff("""
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

  companion object {
    private const val fileExt = "java"
  }
}