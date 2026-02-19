// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.UsefulTestCase;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.util.Groovy25Test;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.junit.Test;

public class Groovy25ResolveTest extends Groovy25Test implements ResolveTest {

  @Test
  public void tap() {
    GrReferenceExpression expression = elementUnderCaret("""
      class A { def prop }
      new A().tap { <caret>prop = 1 }
      """, GrReferenceExpression.class);
    var reference = expression.getLValueReference();
    TestCase.assertNotNull(reference);
    var resolved = reference.resolve();
    UsefulTestCase.assertInstanceOf(resolved, GrAccessorMethod.class);
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