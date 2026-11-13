// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveMethodOverloadsTest implements ResolveTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void void_argument_List_vs_Object() {
    GrMethod method = resolveTest("def foo(Object o); def foo(List l); void bar(); <caret>foo(bar())", GrMethod.class);
    ReadAction.run(() -> assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText(JAVA_LANG_OBJECT)));
  }

  @Test
  public void null_argument_List_vs_Object() {
    GrMethod method = resolveTest("def foo(Object o); def foo(List l); <caret>foo(null)", GrMethod.class);
    ReadAction.run(() -> assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText(JAVA_LANG_OBJECT)));
  }

  @Test
  public void null_argument_List_vs_erased_Object() {
    PsiMethod method = resolveTest("""
                                     def <R> void bar(List<R> l) {}
                                     def <R> void bar(R r) {}
                                     <caret>bar(null)
                                     """, PsiMethod.class);
    ReadAction.run(() -> {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      assertTrue(parameters[parameters.length - 1].getType().equalsToText("R"));
    });
  }

  @Test
  public void list_equals_null() {
    PsiMethod method = resolveTest("void usage(List<String> l) { l.<caret>equals(null) }", PsiMethod.class);
    ReadAction.run(() -> {
      assertEquals(JAVA_UTIL_LIST, method.getContainingClass().getQualifiedName());
      PsiParameter[] parameters = method.getParameterList().getParameters();
      assertTrue(parameters[parameters.length - 1].getType().equalsToText(JAVA_LANG_OBJECT));
    });
  }

  @Test
  public void list____null() {
    PsiMethod method = resolveTest("void usage(List<String> l) { l <caret>== null }", PsiMethod.class);
    ReadAction.run(() -> {
      assertEquals(JAVA_UTIL_LIST, method.getContainingClass().getQualifiedName());
      PsiParameter[] parameters = method.getParameterList().getParameters();
      assertTrue(parameters[parameters.length - 1].getType().equalsToText(JAVA_LANG_OBJECT));
    });
  }
}
