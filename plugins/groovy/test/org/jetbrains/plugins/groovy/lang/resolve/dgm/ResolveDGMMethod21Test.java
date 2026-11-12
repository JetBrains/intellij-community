// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.dgm;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveDGMMethod21Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @Test
  public void testIsNumber() {
    GrGdkMethod resolved = resolve.get().resolveByText("\"1.2.3\".isN<caret>umber()", GrGdkMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("org.codehaus.groovy.runtime.StringGroovyMethods",
                          resolved.getStaticMethod().getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testSort() {
    GrGdkMethod resolved = resolve.get().resolveByText("[].so<caret>rt()", GrGdkMethod.class);
    ReadAction.runBlocking(() -> {
      PsiParameter[] parameterList = resolved.getStaticMethod().getParameterList().getParameters();
      assertEquals(1, parameterList.length);
      assertEquals("java.util.Collection<T>", parameterList[0].getType().getCanonicalText());
    });
  }

  @Test
  public void testEach() {
    GrGdkMethod resolved = resolve.get().resolveByText("""
                                           void foo(Iterator<Object> iterator) {
                                             iterator.ea<caret>ch {}
                                           }
                                           """, GrGdkMethod.class);
    ReadAction.runBlocking(() -> {
      PsiMethod staticMethod = resolved.getStaticMethod();
      assertTrue(staticMethod.hasModifierProperty(PsiModifier.PUBLIC));
      PsiParameter[] parameterList = staticMethod.getParameterList().getParameters();
      assertEquals(2, parameterList.length);
      assertEquals("T", parameterList[0].getType().getCanonicalText());
    });
  }
}
