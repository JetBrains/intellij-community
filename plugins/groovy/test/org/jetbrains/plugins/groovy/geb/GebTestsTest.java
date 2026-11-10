// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.geb;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor;
import org.jetbrains.plugins.groovy.RepositoryTestLibrary;
import org.jetbrains.plugins.groovy.TestLibrary;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class GebTestsTest {
  private static final TestLibrary LIB_GEB =
    new RepositoryTestLibrary("org.codehaus.geb:geb-core:0.7.2", "org.codehaus.geb:geb-junit4:0.7.2", "org.codehaus.geb:geb-spock:0.7.2",
                              "org.codehaus.geb:geb-testng:0.7.2");

  public static LightProjectDescriptor DESCRIPTOR = new LibraryLightProjectDescriptor(
    GroovyProjectDescriptors.LIB_GROOVY_1_6.plus(LIB_GEB));
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSpockTestMemberCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("FooTest.groovy", """
      class FooTest extends geb.spock.GebReportingSpec {
          def testFoo() {
            when:
            <caret>
          }
      }
      """);

    TestUtils.checkCompletionContains(myFixture, "$()", "to()", "go()", "currentWindow", "verifyAt()", "title");
  }

  @Test
  public void testJUnitTestMemberCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("FooTest.groovy", """
      class FooTest extends geb.junit4.GebReportingTest {
          def testFoo() {
            <caret>
          }
      }
      """);

    TestUtils.checkCompletionContains(myFixture, "$()", "to()", "go()", "currentWindow", "verifyAt()", "title");
  }

  @Test
  public void testTestNGTestMemberCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("FooTest.groovy", """
      class FooTest extends geb.testng.GebReportingTest {
          def testFoo() {
            <caret>
          }
      }
      """);

    TestUtils.checkCompletionContains(myFixture, "$()", "to()", "go()", "currentWindow", "verifyAt()", "title");
  }

  @Test
  public void testFieldNameCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("FooTest.groovy", """
      class FooTest extends geb.Page {
      
          static <caret>
      
          static content = {}
      }
      """);

    TestUtils.checkCompletionContains(myFixture, "at", "url");
    assertFalse(myFixture.getLookupElementStrings().contains("content"));
  }

  @Test
  public void testResolveFromParent() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.groovy", """
      class A extends ParentClass {
        static at = {
          aaa.<caret>
        }
      }
      
      class ParentClass extends geb.Page {
        static content = {
          aaa { $('#fieldA') }
        }
      }
      """);

    TestUtils.checkCompletionContains(myFixture, "allElements()", "add()", "firstElement()");
  }

  @Test
  public void testResolveContentFieldsAndMethods() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("PageWithContent.groovy", """
      class PageWithContent extends geb.Page {
        static content = {
          button { $('button') }
          formField { String name -> $('input', name: name) }
        }
      
        def someMethod() {
          <caret>
        }
      }
      """);

    TestUtils.checkCompletionContains(myFixture, "button", "formField()");
  }

  @Test
  public void testContentElementsCompletionType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("PageWithContent.groovy", """
      class PageWithContent extends geb.Page {
        static content = {
          button { $('button') }
          formField { String name -> $('input', name: name) }
        }
      
        def someMethod() {
          <caret>
        }
      }
      """);

    TestUtils.checkCompletionType(myFixture, "button", "geb.navigator.Navigator");
    TestUtils.checkCompletionType(myFixture, "formField", "geb.navigator.Navigator");
  }

  @Test
  public void testContentMethodReturnType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("PageWithContent.groovy", """
      class PageWithContent extends geb.Page {
        static content = {
          formField { String name -> $('input', name: name) }
        }
      
        def someMethod() {
          formField('username').<caret>
        }
      }
      """);

    TestUtils.checkCompletionContains(myFixture, "allElements()", "add()", "firstElement()");
  }

  @Test
  public void testCheckHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(GroovyAssignabilityCheckInspection.class);

    myFixture.configureByText("A.groovy", """
      class A extends geb.Page {
    
        static someField = "abc"
    
        static at = {
          int x = bbb
          Boolean s = bbb
        }
    
        static content = {
          someField<warning descr="'someField' cannot be applied to '()'">()</warning>
          aaa { "Aaa" }
          bbb { aaa.length() }
          ccc(required: false) { aaa.length() }
          eee(1, required: false) { aaa.length() }
        }
      }
      """);

    myFixture.checkHighlighting(true, false, true);
    ReadAction.runBlocking(() -> TestUtils.checkResolve(myFixture.getFile(), "eee"));
  }

  @Test
  public void testRename() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile a = myFixture.addFileToProject("A.groovy", """
      class A extends geb.Page {
        static at = {
          String x = aaa
        }
    
        static content = {
          aaa { "Aaa" }
          bbb { aaa.length() }
        }
      }
      """);

    myFixture.configureByText("B.groovy", """
      class B extends A {
        static at = {
          def x = aaa<caret>
        }
    
        static content = {
          ttt { bbb + aaa.length() }
        }
      }
      """);

    EdtTestUtil.runInEdtAndWait(() -> myFixture.renameElementAtCaret("aaa777"));

    myFixture.checkResult("""
                            class B extends A {
                              static at = {
                                def x = aaa777
                              }
                          
                              static content = {
                                ttt { bbb + aaa777.length() }
                              }
                            }
                            """);

    assertEquals("""
                   class A extends geb.Page {
                     static at = {
                       String x = aaa777
                     }
                 
                     static content = {
                       aaa777 { "Aaa" }
                       bbb { aaa777.length() }
                     }
                   }
                   """, a.getText());
  }

  @Test
  public void testRename2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.groovy", """
      class A extends geb.Page {
        static at = {
          String x = aaa
        }
    
        static content = {
          aaa<caret> { "Aaa" }
          bbb { aaa.length() }
        }
      }
      """);

    PsiFile b = myFixture.addFileToProject("B.groovy", """
      class B extends A {
        static at = {
          def x = aaa
        }
    
        static content = {
          ttt { bbb + aaa.length() }
        }
      }
      """);

    EdtTestUtil.runInEdtAndWait(() -> myFixture.renameElementAtCaret("aaa777"));

    assertEquals("""
                   class B extends A {
                     static at = {
                       def x = aaa777
                     }
                 
                     static content = {
                       ttt { bbb + aaa777.length() }
                     }
                   }
                   """, b.getText());

    myFixture.checkResult("""
                            class A extends geb.Page {
                              static at = {
                                String x = aaa777
                              }
                          
                              static content = {
                                aaa777 { "Aaa" }
                                bbb { aaa777.length() }
                              }
                            }
                            """);
  }
}
