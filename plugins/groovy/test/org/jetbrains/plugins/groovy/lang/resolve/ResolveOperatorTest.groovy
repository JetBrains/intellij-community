/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

@CompileStatic
class ResolveOperatorTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void "test plus assignment"() {
    fixture.with {
      addClass '''\
public class Foo {
  private java.util.Set<Integer> numbers;
  public java.util.Set<Integer> getNumbers() {return null;}
  public void setNumbers(java.util.Set<Integer> files) {}
}
'''
      def file = configureByText('_.groovy', '''\
def f(Foo foo, Integer i) {
  foo.numbers += i
}
''') as GroovyFile
      def assignment = file.methods.first().block.statements.first() as GrAssignmentExpression
      def lValue = assignment.LValue as GrReferenceExpression
      def resolved = lValue.resolve()
      assert resolved instanceof PsiMethod
      assert ((PsiMethod)resolved).name == "setNumbers"
    }
  }
}
