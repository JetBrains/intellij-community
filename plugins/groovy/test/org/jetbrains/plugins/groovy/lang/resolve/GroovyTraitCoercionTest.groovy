// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class GroovyTraitCoercionTest extends GroovyResolveTestCase {

  LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  @Override
  void setUp() {
    super.setUp()
    'add necessary classes'()
  }

  void 'add necessary classes'() {
    fixture.addFileToProject 'classes.groovy', '''\
trait T1 {
    String foo() {}
    def bar() {}
}

trait T2 {
    Integer foo() {}
}

interface I {
    def someMethod()
}

class Foo implements I {
    Foo foo() {}
    def someMethod() {}
    def fooMethod() {}
}
'''
  }

  void 'test types: `as` and chained `as`'() {
    doTestExpressionTypes([
      'new Foo() as T1'        : 'Foo as T1',
      'new Foo() as T2'        : 'Foo as T2',
      '(new Foo() as T1) as T2': 'Foo as T1, T2',
      '(new Foo() as T2) as T1': 'Foo as T2, T1',
    ])
  }

  void 'test types: `withTraits()` and chained `withTraits()`'() {
    doTestExpressionTypes([
      'new Foo().withTraits(T1)'               : 'Foo as T1',
      'new Foo().withTraits(T2)'               : 'Foo as T2',
      'new Foo().withTraits(T1).withTraits(T2)': 'Foo as T1, T2',
      'new Foo().withTraits(T2).withTraits(T1)': 'Foo as T2, T1',
    ])
  }

  void 'test types: remove duplicates'() {
    doTestExpressionTypes([
      '(new Foo() as T1) as T1'                : 'Foo as T1',
      '(new Foo() as T1).withTraits(T1)'       : 'Foo as T1',
      'new Foo().withTraits(T2) as T2'         : 'Foo as T2',
      'new Foo().withTraits(T2).withTraits(T2)': 'Foo as T2',
    ])
  }

  void 'test types: mixed `as` and `withTraits()`'() {
    doTestExpressionTypes([
      '(new Foo() as T1).withTraits(T2)': 'Foo as T1, T2',
      '(new Foo() as T2).withTraits(T1)': 'Foo as T2, T1',
      'new Foo().withTraits(T1) as T2'  : 'Foo as T1, T2',
      'new Foo().withTraits(T2) as T1'  : 'Foo as T2, T1',
    ])
  }

  void 'test types: with two traits'() {
    doTestExpressionTypes(
      'new Foo().withTraits(T1, T2)': 'Foo as T1, T2'
    )
  }

  void 'test types: traits duplicates and order'() {
    doTestExpressionTypes(
      '(new Foo() as T1).withTraits(T2, T1)': 'Foo as T2, T1'
    )
  }

  void 'test `as` operator'() {
    doTestResolveContainingClass([
      '(new Foo() as T1).fo<caret>o()'        : 'T1',
      '(new Foo() as T1).ba<caret>r()'        : 'T1',
      '(new Foo() as T1).some<caret>Method()' : 'Foo',
      '(new Foo() as T1).foo<caret>Method()'  : 'Foo',
      '((new Foo() as T1) as T2).fo<caret>o()': 'T2',
      '((new Foo() as T1) as T2).ba<caret>r()': 'T1',
    ])
  }

  void 'test `withTraits()`'() {
    doTestResolveContainingClass([
      'new Foo().withTraits(T1).fo<caret>o()'               : 'T1',
      'new Foo().withTraits(T1).ba<caret>r()'               : 'T1',
      'new Foo().withTraits(T1).withTraits(T2).fo<caret>o()': 'T2',
      'new Foo().withTraits(T2).withTraits(T1).fo<caret>o()': 'T1',
    ])
  }

  void 'test duplicates and order'() {
    doTestResolveContainingClass([
      '(new Foo() as T1).withTraits(T2, T1).fo<caret>o()': 'T1'
    ])
  }

  void 'test completion proirity'() {
    fixture.configureByText GroovyFileType.GROOVY_FILE_TYPE, '''\
(new Foo().withTraits(T1, T2).f<caret>)
'''
    def method = fixture.findClass('T2').findMethodsByName('foo', false)[0]
    def lookupElements = fixture.complete(CompletionType.BASIC)
    assert lookupElements.find { it.psiElement == method }
  }

  def <T extends GrExpression> T configureByExpression(String text, Class<T> expressionType = GrExpression) {
    assert text
    fixture.configureByText GroovyFileType.GROOVY_FILE_TYPE, text
    PsiTreeUtil.findFirstParent(fixture.file.findElementAt(0), { PsiElement element ->
      element in expressionType && element.text == text
    }).asType(expressionType)
  }

  def doTestExpressionType(String expressionString, String typeString) {
    configureByExpression(expressionString).with { GrExpression expression ->
      assert expression.type: expression.getText()
      assert expression.type.internalCanonicalText == typeString: "$expression.text: $expression.type.internalCanonicalText == $typeString"
    }
  }

  def doTestExpressionTypes(Map<String, String> data) {
    data.each this.&doTestExpressionType
  }

  def doTestResolveContainingClass(Map<String, String> data) {
    data.each { k, v ->
      assert resolveByText(k, GrMethod).containingClass == fixture.findClass(v)
    }
  }
}
