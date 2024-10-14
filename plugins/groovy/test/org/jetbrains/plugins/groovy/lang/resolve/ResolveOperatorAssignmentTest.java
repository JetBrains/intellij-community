// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

public class ResolveOperatorAssignmentTest extends GroovyResolveTestCase {
  public void test_resolve_both_getter___setter() {
    DefaultGroovyMethods.with(getFixture(), new Closure<Void>(this, this) {
      public void doCall(JavaCodeInsightTestFixture it) {
        configureByText("_.groovy", """
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
        myFixture.enableInspections(GrUnresolvedAccessInspection.class, GroovyUnusedDeclarationInspection.class);
        myFixture.checkHighlighting();
        GrReferenceExpression ref =
          DefaultGroovyMethods.asType(getFile().findReferenceAt(getEditor().getCaretModel().getOffset()), GrReferenceExpression.class);
        GroovyResolveResult[] results = ref.multiResolve(false);
        assert DefaultGroovyMethods.size(results) == 2;
        for (GroovyResolveResult result : results) {
          assert result.isValidResult();
        }
      }

      public void doCall() {
        doCall(null);
      }
    });
  }

  public void test_resolve_primitive_getter___setter() {
    DefaultGroovyMethods.with(getFixture(), new Closure<GroovyResolveResult[]>(this, this) {
      public GroovyResolveResult[] doCall(JavaCodeInsightTestFixture it) {
        myFixture.addClass("""
                   class Nothing {
                       private int value;
                       public void setValue(int value) { this.value = value; }
                       public int getValue() { return this.value; }
                   }
                   """);
        GrReferenceExpression ref = DefaultGroovyMethods.asType(configureByText("""
                                                                                  new Nothing().val<caret>ue += 42
                                                                                  """), GrReferenceExpression.class);
        GroovyResolveResult[] results = ref.multiResolve(false);
        assert DefaultGroovyMethods.size(results) == 2;
        return DefaultGroovyMethods.each(results,
                                         new Closure<Void>(ResolveOperatorAssignmentTest.this, ResolveOperatorAssignmentTest.this) {
                                           public void doCall(GroovyResolveResult it) {
                                             assert it.isValidResult();
                                             assert it.getElement() instanceof PsiMethod;
                                           }

                                           public void doCall() {
                                             doCall(null);
                                           }
                                         });
      }

      public GroovyResolveResult[] doCall() {
        return doCall(null);
      }
    });
  }

  public void test_type_reassigned() {
    DefaultGroovyMethods.with(getFixture(), new Closure<Void>(this, this) {
      public void doCall(JavaCodeInsightTestFixture it) {
        GrReferenceExpression expression = DefaultGroovyMethods.asType(configureByText("""
                                                                                         class ClassWithPlus {
                                                                                           AnotherClass plus(a) {new AnotherClass()}
                                                                                         }
                                                                                         class AnotherClass {}
                                                                                         def c = new ClassWithPlus()
                                                                                         c += 1
                                                                                         <caret>c
                                                                                         """), GrReferenceExpression.class);
        assert expression.getType().equalsToText("AnotherClass");
      }

      public void doCall() {
        doCall(null);
      }
    });
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;
}
