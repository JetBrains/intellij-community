// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.builder;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class XmlMarkupBuilderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testResolveToMethod1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.groovy",
                              "\nclass A {\n    void foo() {}\n\n    void testSomething() {\n        def xml = new groovy.xml.MarkupBuilder()\n        xml.records() {\n            foo<caret>()\n        }\n    }\n}\n");

    ReadAction.runBlocking(() -> {
      PsiElement method = myFixture.getElementAtCaret();
      assertInstanceOf(GrMethodImpl.class, method);
      assertTrue(method.isPhysical());
      assertEquals("A", ((GrMethodImpl)method).getContainingClass().getName());
    });
  }

  @Test
  public void testResolveToMethod2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    ReadAction.runBlocking(() -> {
      PsiElement method = myFixture.getElementAtCaret();
      assertInstanceOf(ClsMethodImpl.class, method);
      assertTrue(method.isPhysical());
      assertEquals("MarkupBuilder", ((ClsMethodImpl)method).getContainingClass().getName());
    });
  }

  @Test
  public void testResolveToDynamicMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.groovy", """
      def xml = new groovy.xml.MarkupBuilder()
      xml.records() {
        foo<caret>()
      }
      """);

    ReadAction.runBlocking(() -> {
      PsiElement method = myFixture.getElementAtCaret();
      assertInstanceOf(GrLightMethodBuilder.class, method);
      assertEquals("java.lang.String", ((GrLightMethodBuilder)method).getReturnType().getCanonicalText());
    });
  }
}
