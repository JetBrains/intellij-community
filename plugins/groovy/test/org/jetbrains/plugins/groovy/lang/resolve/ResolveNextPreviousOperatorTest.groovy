// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Before
import org.junit.Test

class ResolveNextPreviousOperatorTest extends GroovyLatestTest implements ResolveTest {

  @Before
  void addClasses() {
    fixture.addFileToProject 'classes.groovy', '''\
class P {
  A getProp() { println "$this: getProp"; new A() }
  void setProp(B b) { println "$this: setProp $b" }

  A getAt(int i) { println "$this: getAt $i"; new A() }
  void putAt(int i, B b) { println "$this: putAt $i $b" }
}

class A {
  B next() { println "$this: next"; new B() }
}

class B {}
'''
  }

  @Test
  void 'postfix getter & setter'() {
    def expression = elementUnderCaret 'new P().<caret>prop++', GrReferenceExpression
    expression.RValueReference.with {
      def result = advancedResolve()
      assert result instanceof AccessorResolveResult
      assert result.validResult
      assert result.element.name == 'getProp'
    }
    expression.LValueReference.with {
      def result = advancedResolve()
      assert result instanceof AccessorResolveResult
      assert result.validResult
      assert result.element.name == 'setProp'
    }
  }

  @Test
  void 'prefix getter & setter'() {
    def expression = elementUnderCaret '++new P().<caret>prop', GrReferenceExpression
    expression.RValueReference.with {
      def result = advancedResolve()
      assert result instanceof AccessorResolveResult
      assert result.validResult
      assert result.element.name == 'getProp'
    }
    expression.LValueReference.with {
      def result = advancedResolve()
      assert result instanceof AccessorResolveResult
      assert result.validResult
      assert result.element.name == 'setProp'
    }
  }

  @Test
  void 'postfix index get & put'() {
    def expression = elementUnderCaret 'new P()<caret>[42]++', GrIndexProperty
    expression.RValueReference.with {
      def result = advancedResolve()
      def element = result.element
      assert element instanceof PsiMethod
      assert result.validResult
      assert element.name == 'getAt'
    }
    expression.LValueReference.with {
      def result = advancedResolve()
      def element = result.element
      assert element instanceof PsiMethod
      assert result.validResult
      assert element.name == 'putAt'
    }
  }

  @Test
  void 'prefix index get & put'() {
    def expression = elementUnderCaret '++new P()<caret>[42]', GrIndexProperty
    expression.RValueReference.with {
      def result = advancedResolve()
      def element = result.element
      assert element instanceof PsiMethod
      assert result.validResult
      assert element.name == 'getAt'
    }
    expression.LValueReference.with {
      def result = advancedResolve()
      def element = result.element
      assert element instanceof PsiMethod
      assert result.validResult
      assert element.name == 'putAt'
    }
  }
}
