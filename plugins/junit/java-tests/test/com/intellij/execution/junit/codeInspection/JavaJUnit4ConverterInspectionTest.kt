// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.MavenTestLib.JUNIT3
import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.junit.testFramework.junit4ConverterInspectionFixture
import com.intellij.junit.testFramework.junitProjectFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

@TestApplication
class JavaJUnit4ConverterInspectionTest {
  companion object {
    private val projectFixture = junitProjectFixture(JUNIT3, JUNIT4)
  }

  private val myFixture by junit4ConverterInspectionFixture(projectFixture)

  @Test
  fun `test highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import junit.framework.TestCase;
      
      class <warning descr="'JUnit3Test' could be converted to JUnit4 test case">JUnit3Test</warning> extends TestCase {
        public void testAddition() {
          assertEquals(2, 1 + 1);
        }
      }""".trimIndent())
  }

  @Test
  fun `test quickfix lifecycle method name conflict`() {
    myFixture.addFileToProject("AbstractJUnit3Test.java", """
        import junit.framework.TestCase;
      
        public abstract class AbstractJUnit3Test extends TestCase {
            @Override
            public void setUp() throws Exception {
                System.out.println("setup 2");
                super.setUp();
            }
  
            @Override
            public void tearDown() throws Exception {
                try {
                    System.out.println("tearDown 2");
                } finally {
                    super.tearDown();
                }
            }
        }      
    """.trimIndent())
    myFixture.configureByText("JUnit3Test.java", """
        import junit.framework.TestCase;
      
        class JUnit3<caret>Test extends AbstractJUnit3Test {
            @Override
            public void setUp() throws Exception {
                System.out.println("setup 1");
                super.setUp();
            }
  
            public void testAddition() {
                assertEquals(2, 1 + 1);
            }
  
            @Override
            public void tearDown() throws Exception {
                try {
                    System.out.println("tearDown 1");
                } finally {
                    super.tearDown();
                }
            }
        }
    """.trimIndent())
    try {
      myFixture.runQuickFix("Convert to JUnit 4 test case")
      fail("Expected ConflictsInTestsException exception te be thrown.")
    } catch(e: BaseRefactoringProcessor.ConflictsInTestsException) {
      assertEquals(e.messages.size, 2)
      assertThat(e.messages).contains("Method setUp will have a name collision with its super method",
        "Method tearDown will have a name collision with its super method")
    }
  }

  @Test
  fun `test quickfix semantic change`() {
    myFixture.testQuickFixException<BaseRefactoringProcessor.ConflictsInTestsException>(JvmLanguage.JAVA, """
        import junit.framework.TestCase;
      
        class JUnit3<caret>Test extends TestCase {
            public void testAddition() {
                System.out.println(toString());
            }
        }  
    """.trimIndent(), "Convert to JUnit 4 test case") { e ->
      assertEquals(e.messages.size, 1)
      assertThat(e.messages).contains("Method call toString() may change semantics when class JUnit3Test is converted to JUnit 4")
    }
  }

  @Test
  fun `test quickfix removed method`() {
    myFixture.testQuickFixException<BaseRefactoringProcessor.ConflictsInTestsException>(JvmLanguage.JAVA, """
        import junit.framework.TestCase;
      
        class JUnit3<caret>Test extends TestCase {
            public void testAddition() {
                System.out.println(countTestCases());
            }
        }      
    """.trimIndent(), "Convert to JUnit 4 test case") { e ->
      assertEquals(e.messages.size, 1)
      assertThat(e.messages).contains("Method call countTestCases() will not compile when class JUnit3Test is converted to JUnit 4")
    }
  }

  @Test
  fun `test quickfix non convertable suite`() {
    myFixture.testQuickFixException<BaseRefactoringProcessor.ConflictsInTestsException>(JvmLanguage.JAVA, """
        import junit.framework.TestCase;
        import junit.framework.Test;
        
        class JUnit3<caret>Test extends TestCase {
            public static Test suite() {
                System.out.println("Creating test suite");
                TestSuite suite = new TestSuite();
                suite.addTestSuite(Foo.class);
                suite.addTestSuite(Bar.class);
                return suite;
            }
        }
    """.trimIndent(), "Convert to JUnit 4 test case") { e ->
      assertEquals(e.messages.size, 1)
      assertThat(e.messages).contains("Migration of suite method for class JUnit3Test has side effects which will be deleted")
    }
  }

  @Test
  fun `test quickfix class expression suite converter`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      import junit.framework.TestCase;
      import junit.framework.TestSuite;
      import junit.framework.Test;

      class Foo extends TestCase { }
      class Bar extends TestCase { }
      
      class JUnit3<caret>Test extends TestCase {
        public static Test suite() {
          TestSuite suite = new TestSuite();
          suite.addTestSuite(Foo.class);
          suite.addTestSuite(Bar.class);
          return suite;
        }
      }
      """.trimIndent(), """
      import junit.framework.TestCase;
      import junit.framework.TestSuite;
      import junit.framework.Test;
      import org.junit.runner.RunWith;
      import org.junit.runners.Suite;
      
      class Foo extends TestCase { }
      class Bar extends TestCase { }

      @RunWith(Suite.class)
      @Suite.SuiteClasses({Foo.class, Bar.class})
      class JUnit3Test {
      }
      """.trimIndent(), "Convert to JUnit 4 test case", testPreview = true)
  }

  @Test
  fun `test quickfix nested suite converter`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      import junit.framework.TestCase;
      import junit.framework.TestSuite;
      import junit.framework.Test;

      class Foo extends TestCase {
        public static Test suite() {
          TestSuite suite = new TestSuite();
          suite.addTestSuite(Bar.class);
          return suite;
        }
      }
      
      class Bar extends TestCase { }
      
      class JUnit3<caret>Test extends TestCase {
        public static Test suite() {
          TestSuite suite = new TestSuite();
          suite.addTest(Foo.suite());
          return suite;
        }
      }
      """.trimIndent(), """
      import junit.framework.TestCase;
      import junit.framework.TestSuite;
      import junit.framework.Test;
      import org.junit.runner.RunWith;
      import org.junit.runners.Suite;
      
      class Foo extends TestCase {
        public static Test suite() {
          TestSuite suite = new TestSuite();
          suite.addTestSuite(Bar.class);
          return suite;
        }
      }
      
      class Bar extends TestCase { }

      @RunWith(Suite.class)
      @Suite.SuiteClasses({Foo.class})
      class JUnit3Test {
      }
      """.trimIndent(), "Convert to JUnit 4 test case", testPreview = true)
  }

  @Test
  fun `test quickfix assertion converter`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      import junit.framework.TestCase;
      
      class JUnit3<caret>Test extends TestCase {
          public void testAddition() {
              assertEquals(2, 1 + 1);
          }
      }
      """.trimIndent(), """
      import junit.framework.TestCase;
      import org.junit.Assert;
      import org.junit.Test;

      class JUnit3Test {
          @Test
          public void testAddition() {
              Assert.assertEquals(2, 1 + 1);
          }
      }
      """.trimIndent(), "Convert to JUnit 4 test case", testPreview = true)
  }

  @Test
  fun `test quickfix setup and teardown converter`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      import junit.framework.TestCase;
      
      class JUnit3<caret>Test extends TestCase {
          @Override
          public void setUp() {
              System.out.println("setup");
              super.setUp();
          }

          public void testAddition() {
              assertEquals(2, 1 + 1);
          }

          @Override
          void tearDown() {
              try {
                  System.out.println("tearDown");
              } finally {
                  super.tearDown();
              }
          }
      }
      """.trimIndent(), """
      import junit.framework.TestCase;
      import org.junit.After;
      import org.junit.Assert;
      import org.junit.Before;
      import org.junit.Test;

      class JUnit3Test {
          @Before
          public void setUp() {
              System.out.println("setup");
          }

          @Test
          public void testAddition() {
              Assert.assertEquals(2, 1 + 1);
          }

          @After
          public void tearDown() {
              System.out.println("tearDown");
          }
      }
      """.trimIndent(), "Convert to JUnit 4 test case", testPreview = true)
  }
}