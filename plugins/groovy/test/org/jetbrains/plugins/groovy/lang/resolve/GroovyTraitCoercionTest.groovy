/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class GroovyTraitCoercionTest extends GroovyResolveTestCase {

  String basePath = null
  LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_2_3_9

  @Override
  void setUp() {
    super.setUp()
    'add necessary classes'()
  }

  public void 'add necessary classes'() {
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
    testExpressionTypes([
      'new Foo() as T1'        : 'Foo as T1',
      'new Foo() as T2'        : 'Foo as T2',
      '(new Foo() as T1) as T2': 'Foo as T1, T2',
      '(new Foo() as T2) as T1': 'Foo as T2, T1',
    ])
  }

  void 'test types: `withTraits()` and chained `withTraits()`'() {
    testExpressionTypes([
      'new Foo().withTraits(T1)'               : 'Foo as T1',
      'new Foo().withTraits(T2)'               : 'Foo as T2',
      'new Foo().withTraits(T1).withTraits(T2)': 'Foo as T1, T2',
      'new Foo().withTraits(T2).withTraits(T1)': 'Foo as T2, T1',
    ])
  }

  void 'test types: remove duplicates'() {
    testExpressionTypes([
      '(new Foo() as T1) as T1'                : 'Foo as T1',
      '(new Foo() as T1).withTraits(T1)'       : 'Foo as T1',
      'new Foo().withTraits(T2) as T2'         : 'Foo as T2',
      'new Foo().withTraits(T2).withTraits(T2)': 'Foo as T2',
    ])
  }

  void 'test types: mixed `as` and `withTraits()`'() {
    testExpressionTypes([
      '(new Foo() as T1).withTraits(T2)': 'Foo as T1, T2',
      '(new Foo() as T2).withTraits(T1)': 'Foo as T2, T1',
      'new Foo().withTraits(T1) as T2'  : 'Foo as T1, T2',
      'new Foo().withTraits(T2) as T1'  : 'Foo as T2, T1',
    ])
  }

  void 'test types: with two traits'() {
    testExpressionTypes(
      'new Foo().withTraits(T1, T2)': 'Foo as T1, T2'
    )
  }

  void 'test types: traits duplicates and order'() {
    testExpressionTypes(
      '(new Foo() as T1).withTraits(T2, T1)': 'Foo as T2, T1'
    )
  }

  void 'test `as` operator'() {
    testResolveContainingClass([
      '(new Foo() as T1).fo<caret>o()'        : 'T1',
      '(new Foo() as T1).ba<caret>r()'        : 'T1',
      '(new Foo() as T1).some<caret>Method()' : 'Foo',
      '(new Foo() as T1).foo<caret>Method()'  : 'Foo',
      '((new Foo() as T1) as T2).fo<caret>o()': 'T2',
      '((new Foo() as T1) as T2).ba<caret>r()': 'T1',
    ])
  }

  void 'test `withTraits()`'() {
    testResolveContainingClass([
      'new Foo().withTraits(T1).fo<caret>o()'               : 'T1',
      'new Foo().withTraits(T1).ba<caret>r()'               : 'T1',
      'new Foo().withTraits(T1).withTraits(T2).fo<caret>o()': 'T2',
      'new Foo().withTraits(T2).withTraits(T1).fo<caret>o()': 'T1',
    ])
  }

  void 'test duplicates and order'() {
    testResolveContainingClass([
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

  def testExpressionType(String expressionString, String typeString) {
    configureByExpression(expressionString).with { GrExpression expression ->
      assert expression.type: expression.getText()
      assert expression.type.internalCanonicalText == typeString: "$expression.text: $expression.type.internalCanonicalText == $typeString"
    }
  }

  def testExpressionTypes(Map<String, String> data) {
    data.each this.&testExpressionType
  }

  def testResolveContainingClass(Map<String, String> data) {
    data.each { k, v ->
      assert resolveByText(k, GrMethod).containingClass == fixture.findClass(v)
    }
  }
}
