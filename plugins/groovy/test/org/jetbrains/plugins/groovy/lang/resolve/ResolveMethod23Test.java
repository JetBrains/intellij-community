// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

public class ResolveMethod23Test extends GroovyResolveTestCase {
  public void test_resolve_upper_bound_type_method() {
    addCompileStatic();
    PsiMethod method = resolveByText("""
                                       @groovy.transform.CompileStatic
                                       def filter(Collection<? extends Number> numbers) {
                                           numbers.findAll { it.double<caret>Value() }
                                       }
                                       """, PsiMethod.class);
    assert method.getContainingClass().getQualifiedName().equals("java.lang.Number");
  }

  public void test_resolve_unknown_class_reference() {
    resolveByText("""
                    def filter(a) {
                        a.clas<caret>s
                    }
                    """, null);
  }

  public void test_resolve_class_reference_on_method() {
    PsiMethod method = resolveByText("""
                                       def b() {return new Object()}
                                       def filter() {
                                           b().clas<caret>s
                                       }
                                       """, PsiMethod.class);
    TestCase.assertEquals("getClass", method.getName());
  }

  public void test_resolve_unknown_class_reference_CS() {
    addCompileStatic();
    PsiMethod method = resolveByText("""
                                       @groovy.transform.CompileStatic
                                       def filter(a) {
                                           a.clas<caret>s
                                       }
                                       """, PsiMethod.class);
    TestCase.assertEquals("getClass", method.getName());
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_3;
}
