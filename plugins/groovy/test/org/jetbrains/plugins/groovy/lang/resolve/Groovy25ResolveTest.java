// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class Groovy25ResolveTest implements ResolveTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = lightJavaCodeInsightFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void tap() {
    GrReferenceExpression expression = elementUnderCaret("""
      class A { def prop }
      new A().tap { <caret>prop = 1 }
      """, GrReferenceExpression.class);
    ReadAction.run(() -> {
      var reference = expression.getLValueReference();
      Assertions.assertNotNull(reference);
      var resolved = reference.resolve();
      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void recursiveType() {
    getFixture().addFileToProject("A.java", """
      class A<SELF extends A<SELF>> {
         public static A<?> gen() {
             return null;
         }


         public SELF foo() { return (SELF)this;}
         public void bar(){}
      }""");
    resolveTest("A.gen().foo().<caret>bar()", PsiMethod.class);
  }

  @Test
  public void wildcardType() {
    getFixture().addFileToProject("A.java", """
      class A<SELF extends Integer> {
         public static A<?> gen() {
             return null;
         }
         public SELF foo() { return null;}
      }""");
    resolveTest("A.gen().foo().<caret>byteValue()", PsiMethod.class);
  }
}