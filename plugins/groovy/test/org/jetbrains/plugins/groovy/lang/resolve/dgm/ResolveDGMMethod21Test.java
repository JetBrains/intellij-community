// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.dgm;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase;
import org.junit.Assert;

public class ResolveDGMMethod21Test extends GroovyResolveTestCase {
  public void testIsNumber() {
    GrGdkMethod resolved = resolveByText("\"1.2.3\".isN<caret>umber()", GrGdkMethod.class);
    Assert.assertEquals("org.codehaus.groovy.runtime.StringGroovyMethods",
                        resolved.getStaticMethod().getContainingClass().getQualifiedName());
  }

  public void testSort() {
    GrGdkMethod resolved = resolveByText("[].so<caret>rt()", GrGdkMethod.class);
    PsiParameter[] parameterList = resolved.getStaticMethod().getParameterList().getParameters();
    Assert.assertEquals(1, parameterList.length);
    Assert.assertEquals("java.util.Collection<T>", parameterList[0].getType().getCanonicalText());
  }

  public void testEach() {
    GrGdkMethod resolved = resolveByText("""
                                           void foo(Iterator<Object> iterator) {
                                             iterator.ea<caret>ch {}
                                           }
                                           """, GrGdkMethod.class);
    PsiMethod staticMethod = resolved.getStaticMethod();
    Assert.assertTrue(staticMethod.hasModifierProperty(PsiModifier.PUBLIC));
    PsiParameter[] parameterList = staticMethod.getParameterList().getParameters();
    Assert.assertEquals(2, parameterList.length);
    Assert.assertEquals("T", parameterList[0].getType().getCanonicalText());
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_1;
}
