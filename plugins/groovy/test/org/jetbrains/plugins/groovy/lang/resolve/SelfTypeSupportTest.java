// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class SelfTypeSupportTest extends LightGroovyTestCase {
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
    assertEquals("I", ((GrMethod)resolved).getContainingClass().getName());
  }

  public void test_resolve_outside_trait() {
    PsiElement resolved = resolveByText("""
                                          interface I { def foo() }
                                          @SelfType(I)
                                          trait T {}
                                          def bar(T t) {
                                            t.f<caret>oo()
                                          }
                                          """);
    assertEquals("I", ((GrMethod)resolved).getContainingClass().getName());
  }

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
    assertEquals("I", ((GrMethod)resolved).getContainingClass().getName());
  }

  public void test_do_not_count__SelfType_on_interfaces_in_hierarchy() {
    assert resolveByText("""
                           interface I { def foo() }
                           @SelfType(I)
                           interface II {}
                           trait T implements II {
                               def bar() {
                                   fo<caret>o()
                               }
                           }
                           """) == null;
  }

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
    getFixture().configureByText("_.groovy", getDefaultImports() + text);
    return getFile().findReferenceAt(getEditor().getCaretModel().getOffset()).resolve();
  }

  public void doTestHighlighting(String text) {
    getFixture().configureByText("_.groovy", getDefaultImports() + text);
    getFixture().checkHighlighting();
  }

  public void test_do_not_process_self_types_when_not_needed() {
    GroovyFile file = (GroovyFile)getFixture().configureByText("_.groovy", getDefaultImports() + """
      interface I {}
      @SelfType(I)\s
      trait T implements I {}
      """);
    GrTypeDefinition[] definitions = file.getTypeDefinitions();
    assertFalse(definitions[definitions.length - 1].getSuperTypes().length == 0);
  }

  @Override
  public @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  public void setProjectDescriptor(LightProjectDescriptor projectDescriptor) {
    this.projectDescriptor = projectDescriptor;
  }

  public String getDefaultImports() {
    return """
      import groovy.transform.SelfType
      """;
  }

  private LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;
}
