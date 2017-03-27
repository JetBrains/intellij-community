/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class ResolveOperatorAssignmentTest extends GroovyResolveTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test resolve both getter & setter'() {
    fixture.with {
      configureByText '_.groovy', '''\
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
'''
      enableInspections GrUnresolvedAccessInspection, GroovyUnusedDeclarationInspection
      checkHighlighting()
      def ref = file.findReferenceAt(editor.caretModel.offset) as GrReferenceExpression
      def results = ref.multiResolve(false)
      assert results.size() == 2
      for (result in results) {
        assert result.isValidResult()
      }
    }
  }

  void 'test resolve primitive getter & setter'() {
    fixture.with {
      addClass '''\
class Nothing {
    private int value;
    public void setValue(int value) { this.value = value; }
    public int getValue() { return this.value; }
}
'''
      def ref = configureByText('''\
new Nothing().val<caret>ue += 42
''') as GrReferenceExpression
      def results = ref.multiResolve(false)
      assert results.size() == 2
      results.each {
        assert it.validResult
        assert it.element instanceof PsiMethod
      }
    }
  }

  void 'test type reassigned'() {
    fixture.with {
      def expression = configureByText('''\
class ClassWithPlus {
  AnotherClass plus(a) {new AnotherClass()}
}
class AnotherClass {}
def c = new ClassWithPlus()
c += 1
<caret>c
''') as GrReferenceExpression
      assert expression.type.equalsToText("AnotherClass")
    }
  }
}
