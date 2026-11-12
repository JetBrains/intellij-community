// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ResolveMethod23Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_3);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @Test
  public void test_resolve_upper_bound_type_method() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    LightGroovyFixtures.addCompileStatic(myFixture);
    PsiMethod method = resolve.get().resolveByText("""
                                       @groovy.transform.CompileStatic
                                       def filter(Collection<? extends Number> numbers) {
                                           numbers.findAll { it.double<caret>Value() }
                                       }
                                       """, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("java.lang.Number", method.getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void test_resolve_unknown_class_reference() {
    resolve.get().resolveByText("""
                    def filter(a) {
                        a.clas<caret>s
                    }
                    """, null);
  }

  @Test
  public void test_resolve_class_reference_on_method() {
    PsiMethod method = resolve.get().resolveByText("""
                                       def b() {return new Object()}
                                       def filter() {
                                           b().clas<caret>s
                                       }
                                       """, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("getClass", method.getName());
    });
  }

  @Test
  public void test_resolve_unknown_class_reference_CS() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    LightGroovyFixtures.addCompileStatic(myFixture);
    PsiMethod method = resolve.get().resolveByText("""
                                       @groovy.transform.CompileStatic
                                       def filter(a) {
                                           a.clas<caret>s
                                       }
                                       """, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("getClass", method.getName());
    });
  }
}
