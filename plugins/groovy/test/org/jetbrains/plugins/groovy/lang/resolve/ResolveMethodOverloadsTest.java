// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.junit.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class ResolveMethodOverloadsTest extends GroovyLatestTest implements ResolveTest {
  @Test
  public void void_argument_List_vs_Object() {
    GrMethod method = resolveTest("def foo(Object o); def foo(List l); void bar(); <caret>foo(bar())", GrMethod.class);
    assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText(JAVA_LANG_OBJECT));
  }

  @Test
  public void null_argument_List_vs_Object() {
    GrMethod method = resolveTest("def foo(Object o); def foo(List l); <caret>foo(null)", GrMethod.class);
    assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText(JAVA_LANG_OBJECT));
  }

  @Test
  public void null_argument_List_vs_erased_Object() {
    PsiMethod method = resolveTest("""
                                     def <R> void bar(List<R> l) {}
                                     def <R> void bar(R r) {}
                                     <caret>bar(null)
                                     """, PsiMethod.class);
    PsiParameter[] parameters = method.getParameterList().getParameters();
    assertTrue(parameters[parameters.length - 1].getType().equalsToText("R"));
  }

  @Test
  public void list_equals_null() {
    PsiMethod method = resolveTest("void usage(List<String> l) { l.<caret>equals(null) }", PsiMethod.class);
    assertEquals(JAVA_UTIL_LIST, method.getContainingClass().getQualifiedName());
    PsiParameter[] parameters = method.getParameterList().getParameters();
    assertTrue(parameters[parameters.length - 1].getType().equalsToText(JAVA_LANG_OBJECT));
  }

  @Test
  public void list____null() {
    PsiMethod method = resolveTest("void usage(List<String> l) { l <caret>== null }", PsiMethod.class);
    assertEquals(JAVA_UTIL_LIST, method.getContainingClass().getQualifiedName());
    PsiParameter[] parameters = method.getParameterList().getParameters();
    assertTrue(parameters[parameters.length - 1].getType().equalsToText(JAVA_LANG_OBJECT));
  }
}
