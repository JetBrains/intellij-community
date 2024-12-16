// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.builder;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

public class XmlMarkupBuilderTest extends LightGroovyTestCase {
  public void testHighlighting() {
    myFixture.enableInspections(GroovyAssignabilityCheckInspection.class, GrUnresolvedAccessInspection.class);
    myFixture.configureByText("A.groovy",
                              """
    new groovy.xml.MarkupBuilder().root {
    a()
    b {}
    c(2) {}
    d(a: 1) {}
    e(b: 2, {})

    f(c: 3, d: 4)
    g(e: 5, [1, 2, 3], f: 6)
    h([g: 7, h: 8], new Object())
    i(new Object(), i: 9, j: 10) {}
    j([k: 11, l: 12], new Object(), {})
    
    k(new Object())
    l(new Object(), [m: 13])
    m(new Object(), [n: 14]) {}
    n(new Object(), [o: 15], {})
    
    foo<warning>("a", "b")</warning>
    }
    """);
    myFixture.testHighlighting(true, false, true);
  }

  public void testResolveToMethod1() {
    myFixture.configureByText("A.groovy",
                              "\nclass A {\n    void foo() {}\n\n    void testSomething() {\n        def xml = new groovy.xml.MarkupBuilder()\n        xml.records() {\n            foo<caret>()\n        }\n    }\n}\n");

    PsiElement method = myFixture.getElementAtCaret();
    assertInstanceOf(method, GrMethodImpl.class);
    assertTrue(method.isPhysical());
    assertEquals("A", ((GrMethodImpl)method).getContainingClass().getName());
  }

  public void testResolveToMethod2() {
    myFixture.configureByText("A.groovy",
                              """
                                class A {
                                    void testSomething() {
                                        def xml = new groovy.xml.MarkupBuilder()
                                        xml.records() {
                                            getDoubleQuotes<caret>()
                                        }
                                    }
                                }
                                """);

    PsiElement method = myFixture.getElementAtCaret();
    assertInstanceOf(method, ClsMethodImpl.class);
    assertTrue(method.isPhysical());
    assertEquals("MarkupBuilder", ((ClsMethodImpl)method).getContainingClass().getName());
  }

  public void testResolveToDynamicMethod() {
    myFixture.configureByText("A.groovy", """
      def xml = new groovy.xml.MarkupBuilder()
      xml.records() {
        foo<caret>()
      }
      """);

    PsiElement method = myFixture.getElementAtCaret();
    assertInstanceOf(method, GrLightMethodBuilder.class);
    assertEquals("java.lang.String", ((GrLightMethodBuilder)method).getReturnType().getCanonicalText());
  }
}
