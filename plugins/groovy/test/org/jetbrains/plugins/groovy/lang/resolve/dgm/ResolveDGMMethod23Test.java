// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.dgm;

import com.intellij.psi.PsiParameter;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase;
import org.junit.Assert;

public class ResolveDGMMethod23Test extends GroovyResolveTestCase {

  @NotNull
  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_3;

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  public void testIsNumber() {
    GrGdkMethod resolved = resolveByText("\"1.2.3\".isN<caret>umber()", GrGdkMethod.class);
    Assert.assertEquals("org.codehaus.groovy.runtime.StringGroovyMethods",
                        resolved.getStaticMethod().getContainingClass().getQualifiedName());
  }

  public void testSort() {
    GrGdkMethod resolved = resolveByText("[].so<caret>rt()", GrGdkMethod.class);
    PsiParameter[] parameterList = resolved.getStaticMethod().getParameterList().getParameters();
    Assert.assertEquals(1, parameterList.length);
    Assert.assertEquals("java.lang.Iterable<T>", parameterList[0].getType().getCanonicalText());
  }

  @SuppressWarnings("GroovyUnusedDeclaration")
  public void _testCloseable() {
    myFixture.addClass("package java.io; class Closeable {}");
    GrGdkMethod resolved = resolveByText("""
                                           class A implements Closeable {}
                                           new A().withC<caret>loseable {}
                                           """, GrGdkMethod.class);
    Assert.assertEquals("org.codehaus.groovy.runtime.NioGroovyMethods", resolved.getStaticMethod().getContainingClass().getQualifiedName());
  }
}
