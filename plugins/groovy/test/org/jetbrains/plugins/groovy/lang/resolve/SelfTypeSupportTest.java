// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class SelfTypeSupportTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void test_resolve_from_within_trait() {
    PsiElement resolved = resolveByText("""
                                          interface I { def foo() }
                                          @SelfType(I)
                                          trait T {
                                            def bar() {
                                              fo<caret>o()
                                            }
                                          }
                                          """);
    ReadAction.runBlocking(() -> {
      assertEquals("I", ((GrMethod)resolved).getContainingClass().getName());
    });
  }

  @Test
  public void test_resolve_outside_trait() {
    PsiElement resolved = resolveByText("""
                                          interface I { def foo() }
                                          @SelfType(I)
                                          trait T {}
                                          def bar(T t) {
                                            t.f<caret>oo()
                                          }
                                          """);
    ReadAction.runBlocking(() -> {
      assertEquals("I", ((GrMethod)resolved).getContainingClass().getName());
    });
  }

  @Test
  public void test_resolve_inside_trait_extending_trait() {
    PsiElement resolved = resolveByText("""
                                          interface I { def foo() }
                                          @SelfType(I)
                                          trait T {}
                                          trait T2 extends T {
                                            def bar() {
                                              f<caret>oo()
                                            }
                                          }
                                          """);
    ReadAction.runBlocking(() -> {
      assertEquals("I", ((GrMethod)resolved).getContainingClass().getName());
    });
  }

  @Test
  public void test_do_not_count__SelfType_on_interfaces_in_hierarchy() {
    assertNull(resolveByText("""
                           interface I { def foo() }
                           @SelfType(I)
                           interface II {}
                           trait T implements II {
                               def bar() {
                                   fo<caret>o()
                               }
                           }
                           """));
  }

  @Test
  public void test_highlighting() {
    doTestHighlighting("""
                         interface I {}
                         @SelfType(I)
                         trait T {}
                         <error descr="@SelfType: Class 'A' does not inherit 'I'">class A implements T</error> {}
                         
                         trait T2 extends T {}
                         <error descr="@SelfType: Class 'B' does not inherit 'I'">class B implements T2</error> {}
                         """);
  }

  public PsiElement resolveByText(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", getDefaultImports() + text);
    return ReadAction.computeBlocking(() -> {
      return myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()).resolve();
    });
  }

  public void doTestHighlighting(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", getDefaultImports() + text);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkHighlighting();
    });
  }

  @Test
  public void test_do_not_process_self_types_when_not_needed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFile file = (GroovyFile)myFixture.configureByText("_.groovy", getDefaultImports() + """
      interface I {}
      @SelfType(I)\s
      trait T implements I {}
      """);
    ReadAction.runBlocking(() -> {
      GrTypeDefinition[] definitions = file.getTypeDefinitions();
      assertFalse(definitions[definitions.length - 1].getSuperTypes().length == 0);
    });
  }

  public String getDefaultImports() {
    return """
      import groovy.transform.SelfType
      """;
  }
}
