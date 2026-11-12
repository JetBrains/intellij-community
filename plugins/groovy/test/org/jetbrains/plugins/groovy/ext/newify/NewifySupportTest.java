// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.newify;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class NewifySupportTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<GrHighlightingFixture> highlighting = grHighlightingFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
    myFixture.addClass("""
                            public class A {
                            String name;
                              int age;
                              public A(){}
                              public A(String name){}
                            }
                            
                            public class A2 {
                              String name;
                              int age;
                            }
                            """);
  }

  @Test
  public void testAutoNewify() {
    highlighting.get().doTestHighlighting("""
                         @Newify
                         class B {
                           def a = A.new()
                           def i = Integer.new(1)
                         }
                         """);
    highlighting.get().doTestHighlighting("""
                         @Newify
                         class B {
                           def a = A.new("B")
                         }
                         """);

    highlighting.get().doTestHighlighting("""
                         @Newify
                         class B {
                           def a = A.new(name :"bar")
                         }
                         """);

    highlighting.get().doTestHighlighting("""
                         class B {
                           @Newify
                           def a = A.new(name :"bar")
                         }
                         """);

    highlighting.get().doTestHighlighting("""
                         class B {
                           @Newify
                           def a (){ return A.new(name :"bar")}
                         }
                         """);
    highlighting.get().doTestHighlighting("""
                         class B {
                           @Newify(value = A, auto = false)
                           def a (){ return A.<warning>new</warning>()}
                         }
                         """);
  }

  @Test
  public void testAutoNewifyImplicitConstructor() {
    highlighting.get().doTestHighlighting("""
                         @Newify
                         class B {
                           def a = A2.new()
                         }
                         """);
    highlighting.get().doTestHighlighting("""
                         @Newify
                         class B {
                           def a = A2.new<warning>("B")</warning>
                         }
                         """);

    highlighting.get().doTestHighlighting("""
                         @Newify
                         class B {
                           def a = A2.new(name :"bar")
                         }
                         """);

    highlighting.get().doTestHighlighting("""
                         class B {
                           @Newify(B)
                           def b = B()
                         }
                         """);

    highlighting.get().doTestHighlighting("""
                         class B {
                           @Newify
                           def a = B.new()
                         }
                         """);

    highlighting.get().doTestHighlighting("""
                         class B2 {
                           String str
                           @Newify
                           def a = B2.new(str: "B2")
                         }
                         """);

    highlighting.get().doTestHighlighting("""
                         class B {
                           @Newify(value = A2, auto = false)
                           def a (){ return A2.<warning>new</warning>()}
                         }
                         """);
  }

  @Test
  public void testNewifyByClass() {
    highlighting.get().doTestHighlighting("""
                         @Newify([A, Integer])
                         class B {
                           def a = A()
                           def i = Integer(1)
                         }
                         """);

    highlighting.get().doTestHighlighting("""
                         @Newify(A)
                         class B {
                           def a = A("B")
                         }
                         """);

    highlighting.get().doTestHighlighting("""
                         @Newify(A)
                         class B {
                           def a = A(name :"bar")
                         }
                         """);

    highlighting.get().doTestHighlighting("""
                         class B {
                           @Newify(A)
                           def a = A(name :"bar")
                         }
                         """);

    highlighting.get().doTestHighlighting("""
                         class B {
                           @Newify(A)
                           def a (){ return A(name :"bar")}
                         }
                         """);
    highlighting.get().doTestHighlighting("""
                         class B {
                           @Newify
                           def a (){ return <warning descr="Cannot resolve symbol 'A'">A</warning>()}
                         }
                         """);
  }

  @Test
  public void testNewifyMapLookup() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().doTestHighlighting("""
                         @Newify(A)
                         class B {
                           def a = A(<caret>)
                         }
                         """);
    myFixture.completeBasic();
    List<String> lookupElementStringList = myFixture.getLookupElementStrings();
    assertTrue(lookupElementStringList.contains("name"));
    assertTrue(lookupElementStringList.contains("age"));
  }

  @Test
  public void testNewifyElementsKind() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().doTestHighlighting("""
                         @Newify(A)
                         class B {
                           def a = <caret>A()
                         }
                         """);
    ReadAction.runBlocking(() -> {
      PsiMethod method = (PsiMethod)myFixture.getElementAtCaret();
      assertTrue(method.isConstructor());
    });
  }

  @Test
  public void testNewifyElementsKind2() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().doTestHighlighting("""
                         @Newify(A)
                         class B {
                           def a = A.ne<caret>w()
                         }
                         """);
    ReadAction.runBlocking(() -> {
      PsiMethod method = (PsiMethod)myFixture.getElementAtCaret();
      assertTrue(method.isConstructor());
    });
  }

  @Test
  public void testNewifyAutoLookup() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.configureByText("a.groovy", """
      @Newify(A)
      class B {
        def a = A.<caret>
      }
      """);
    myFixture.completeBasic();
    List<String> lookupElementStringList = myFixture.getLookupElementStrings();
    assertTrue(lookupElementStringList.contains("new"));
  }

  @Test
  public void testNewifyLookupImplicitConstructor() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.configureByText("a.groovy", """
      @Newify
      class B {
        def b = B.<caret>
      }
      """);
    myFixture.completeBasic();
    List<String> lookupElementStringList = myFixture.getLookupElementStrings();
    assertTrue(lookupElementStringList.contains("new"));
  }

  @Test
  public void testNewifyAutoMapLookup() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().doTestHighlighting("""
                         @Newify(A)
                         class B {
                           def a = A.new(<caret>)
                         }
                         """);
    myFixture.completeBasic();
    List<String> lookupElementStringList = myFixture.getLookupElementStrings();
    assertTrue(lookupElementStringList.contains("name"));
    assertTrue(lookupElementStringList.contains("age"));
  }

  @Test
  public void testNewifySupportsRegexPatterns() {
    highlighting.get().doTestHighlighting("""
                         @Newify(pattern = /[A-Z].*/)
                         class B {
                           def a = A()
                           def b = new A()
                           def c = Integer(1)
                         }
                         """);
    highlighting.get().doTestHighlighting("""
                         @Newify(pattern = "A")
                         class B {
                           def a = A("B")
                         }
                         """);
  }

  @Test
  public void testNewifyShouldNotThrowOnIncorrectRegex() {
    highlighting.get().doTestHighlighting("""
                         @Newify(pattern = "<error>*</error>")
                         class B {
                           def a = <warning descr="Cannot resolve symbol 'A'">A</warning>()
                         }""");
  }

  @Test
  public void testNonStaticClassesAreNotAvailableInStaticContext() {
    highlighting.get().doTestHighlighting("""
                         class Z {
                             class Inner {
                             }
                         
                             @Newify(<error descr="Cannot reference non-static symbol 'Inner' from static context">Inner</error>)
                             public static void main(String[] args) {
                                 def aaa = <warning descr="Cannot resolve symbol 'Inner'">Inner</warning>()
                             }
                         }""");
  }

  @Test
  public void testNoErrorForMultipleConstructorsAndNamedArguments() {
    highlighting.get().doTestHighlighting("""
                         class Rr {
                             Rr() {}
                             Rr(s) {}
                         }
                         
                         
                         @Newify(pattern = /[A-Z].*/)
                         class C {
                             static void main(String[] args) {
                                 def x = Rr(a: 2)
                             }
                         }
                         """);
  }
}