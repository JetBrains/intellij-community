// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.newify;

import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;
import org.junit.Assert;

import java.util.List;

public class NewifySupportTest extends GrHighlightingTestBase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_5;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
    getFixture().addClass("""
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

  public void testAutoNewify() {
    doTestHighlighting("""
                         @Newify
                         class B {
                           def a = A.new()
                           def i = Integer.new(1)
                         }
                         """);
    doTestHighlighting("""
                         @Newify
                         class B {
                           def a = A.new("B")
                         }
                         """);

    doTestHighlighting("""
                         @Newify
                         class B {
                           def a = A.new(name :"bar")
                         }
                         """);

    doTestHighlighting("""
                         class B {
                           @Newify
                           def a = A.new(name :"bar")
                         }
                         """);

    doTestHighlighting("""
                         class B {
                           @Newify
                           def a (){ return A.new(name :"bar")}
                         }
                         """);
    doTestHighlighting("""
                         class B {
                           @Newify(value = A, auto = false)
                           def a (){ return A.<warning>new</warning>()}
                         }
                         """);
  }

  public void testAutoNewifyImplicitConstructor() {
    doTestHighlighting("""
                         @Newify
                         class B {
                           def a = A2.new()
                         }
                         """);
    doTestHighlighting("""
                         @Newify
                         class B {
                           def a = A2.new<warning>("B")</warning>
                         }
                         """);

    doTestHighlighting("""
                         @Newify
                         class B {
                           def a = A2.new(name :"bar")
                         }
                         """);

    doTestHighlighting("""
                         class B {
                           @Newify(B)
                           def b = B()
                         }
                         """);

    doTestHighlighting("""
                         class B {
                           @Newify
                           def a = B.new()
                         }
                         """);

    doTestHighlighting("""
                         class B2 {
                           String str
                           @Newify
                           def a = B2.new(str: "B2")
                         }
                         """);

    doTestHighlighting("""
                         class B {
                           @Newify(value = A2, auto = false)
                           def a (){ return A2.<warning>new</warning>()}
                         }
                         """);
  }

  public void testNewifyByClass() {
    doTestHighlighting("""
                         @Newify([A, Integer])
                         class B {
                           def a = A()
                           def i = Integer(1)
                         }
                         """);

    doTestHighlighting("""
                         @Newify(A)
                         class B {
                           def a = A("B")
                         }
                         """);

    doTestHighlighting("""
                         @Newify(A)
                         class B {
                           def a = A(name :"bar")
                         }
                         """);

    doTestHighlighting("""
                         class B {
                           @Newify(A)
                           def a = A(name :"bar")
                         }
                         """);

    doTestHighlighting("""
                         class B {
                           @Newify(A)
                           def a (){ return A(name :"bar")}
                         }
                         """);
    doTestHighlighting("""
                         class B {
                           @Newify
                           def a (){ return <warning descr="Cannot resolve symbol 'A'">A</warning>()}
                         }
                         """);
  }

  public void testNewifyMapLookup() {
    doTestHighlighting("""
                         @Newify(A)
                         class B {
                           def a = A(<caret>)
                         }
                         """);
    myFixture.completeBasic();
    List<String> lookupElementStringList = myFixture.getLookupElementStrings();
    Assert.assertTrue(lookupElementStringList.contains("name"));
    Assert.assertTrue(lookupElementStringList.contains("age"));
  }

  public void testNewifyElementsKind() {
    doTestHighlighting("""
                         @Newify(A)
                         class B {
                           def a = <caret>A()
                         }
                         """);
    PsiMethod method = (PsiMethod)myFixture.getElementAtCaret();
    Assert.assertTrue(method.isConstructor());
  }

  public void testNewifyElementsKind2() {
    doTestHighlighting("""
                         @Newify(A)
                         class B {
                           def a = A.ne<caret>w()
                         }
                         """);
    PsiMethod method = (PsiMethod)myFixture.getElementAtCaret();
    Assert.assertTrue(method.isConstructor());
  }

  public void testNewifyAutoLookup() {
    myFixture.configureByText("a.groovy", """
      @Newify(A)
      class B {
        def a = A.<caret>
      }
      """);
    myFixture.completeBasic();
    List<String> lookupElementStringList = myFixture.getLookupElementStrings();
    Assert.assertTrue(lookupElementStringList.contains("new"));
  }

  public void testNewifyLookupImplicitConstructor() {
    myFixture.configureByText("a.groovy", """
      @Newify
      class B {
        def b = B.<caret>
      }
      """);
    myFixture.completeBasic();
    List<String> lookupElementStringList = myFixture.getLookupElementStrings();
    Assert.assertTrue(lookupElementStringList.contains("new"));
  }

  public void testNewifyAutoMapLookup() {
    doTestHighlighting("""
                         @Newify(A)
                         class B {
                           def a = A.new(<caret>)
                         }
                         """);
    myFixture.completeBasic();
    List<String> lookupElementStringList = myFixture.getLookupElementStrings();
    Assert.assertTrue(lookupElementStringList.contains("name"));
    Assert.assertTrue(lookupElementStringList.contains("age"));
  }

  public void testNewifySupportsRegexPatterns() {
    doTestHighlighting("""
                         @Newify(pattern = /[A-Z].*/)
                         class B {
                           def a = A()
                           def b = new A()
                           def c = Integer(1)
                         }
                         """);
    doTestHighlighting("""
                         @Newify(pattern = "A")
                         class B {
                           def a = A("B")
                         }
                         """);
  }

  public void testNewifyShouldNotThrowOnIncorrectRegex() {
    doTestHighlighting("""
                         @Newify(pattern = "<error>*</error>")
                         class B {
                           def a = <warning descr="Cannot resolve symbol 'A'">A</warning>()
                         }""");
  }

  public void testNonStaticClassesAreNotAvailableInStaticContext() {
    doTestHighlighting("""
                         class Z {
                             class Inner {
                             }
                         
                             @Newify(<error descr="Cannot reference non-static symbol 'Inner' from static context">Inner</error>)
                             public static void main(String[] args) {
                                 def aaa = <warning descr="Cannot resolve symbol 'Inner'">Inner</warning>()
                             }
                         }""");
  }

  public void testNoErrorForMultipleConstructorsAndNamedArguments() {
    doTestHighlighting("""
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