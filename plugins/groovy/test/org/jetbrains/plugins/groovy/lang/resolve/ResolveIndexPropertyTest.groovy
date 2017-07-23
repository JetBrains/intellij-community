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
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

@CompileStatic
class ResolveIndexPropertyTest extends GroovyResolveTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test prefer single parameter'() {
    fixture.addFileToProject 'classes.groovy', '''\
class A {
  def getAt(a) {}
  def getAt(Integer a) {}
  def getAt(List a) {}
  def getAt(a, b, c) {}
}
'''
    doTest 'a[0]', 1
    doTest 'a[[0]]', 2
    doTest 'a[0, 1, 2]', 2
    doTest 'def l = [0]; a[l]', 2
    doTest 'def l = [0, 1, 2]; a[l]', 2
  }

  void 'test resolve with unwrapped argument types'() {
    fixture.addFileToProject 'classes.groovy', '''\
class A {
  def getAt(Integer a) {}
  def getAt(a, b, c) {}
}
'''
    doTest 'a[0]', 0
    doTest 'a[[0]]', 0
    doTest 'a[0, 1, 2]', 1
    doTest 'a[[0, 1, 2]]', 1
    doTest 'def l = [0]; a[l]', 0
    doTest 'def l = [0]; a[[l]]'
    doTest 'def l = [0, 1, 2]; a[l]', 1
    doTest 'def l = [0, 1, 2]; a[[l]]'
  }

  private doTest(String text, int methodIndex = -1) {
    def file = (GroovyFile)fixture.configureByText('_.groovy', """\
def a = new A()
$text
""")
    def expression = file.statements.last() as GrIndexProperty
    def resolved = (PsiMethod)expression.references[0].resolve()
    if (methodIndex < 0) {
      assert !resolved
    }
    else {
      assert resolved
      assert resolved.containingClass.qualifiedName == 'A'
      assert ((GrTypeDefinition)resolved.containingClass).codeMethods[methodIndex] == resolved: resolved.parameterList.parameters*.type
    }
  }
}
