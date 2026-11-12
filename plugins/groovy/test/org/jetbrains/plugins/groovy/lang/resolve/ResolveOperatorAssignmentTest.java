// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveOperatorAssignmentTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @Test
  public void test_resolve_both_getter___setter() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
        resolve.get().configureByText("_.groovy", """
          class A {
            C plus(B b) {b}
          }
          class B {}
          class C {}
          class Foo {
            A getProp() {}
            Long setProp(C c) {c}
          }
          def foo = new Foo()
          foo.pr<caret>op += new B()
          """);
        EdtTestUtil.runInEdtAndWait(() -> {
          myFixture.enableInspections(GrUnresolvedAccessInspection.class, GroovyUnusedDeclarationInspection.class);
          myFixture.checkHighlighting();
          GrReferenceExpression ref =
            DefaultGroovyMethods.asType(myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()), GrReferenceExpression.class);
          GroovyResolveResult[] results = ref.multiResolve(false);
          assertThat(results).hasSize(2);
          for (GroovyResolveResult result : results) {
            assertTrue(result.isValidResult());
          }
        });
  }

  @Test
  public void test_resolve_primitive_getter___setter() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
        myFixture.addClass("""
                   class Nothing {
                       private int value;
                       public void setValue(int value) { this.value = value; }
                       public int getValue() { return this.value; }
                   }
                   """);
        GrReferenceExpression ref = DefaultGroovyMethods.asType(resolve.get().configureByText("""
                                                                                  new Nothing().val<caret>ue += 42
                                                                                  """), GrReferenceExpression.class);
        ReadAction.runBlocking(() -> {
          GroovyResolveResult[] results = ref.multiResolve(false);
          assertThat(results).hasSize(2);
          for (GroovyResolveResult it : results) {
            assertTrue(it.isValidResult());
            assertInstanceOf(PsiMethod.class, it.getElement());
          }
        });
  }

  @Test
  public void test_type_reassigned() {
        GrReferenceExpression expression = DefaultGroovyMethods.asType(resolve.get().configureByText("""
                                                                                         class ClassWithPlus {
                                                                                           AnotherClass plus(a) {new AnotherClass()}
                                                                                         }
                                                                                         class AnotherClass {}
                                                                                         def c = new ClassWithPlus()
                                                                                         c += 1
                                                                                         <caret>c
                                                                                         """), GrReferenceExpression.class);
        ReadAction.runBlocking(() -> {
          assertTrue(expression.getType().equalsToText("AnotherClass"));
        });
  }
}
