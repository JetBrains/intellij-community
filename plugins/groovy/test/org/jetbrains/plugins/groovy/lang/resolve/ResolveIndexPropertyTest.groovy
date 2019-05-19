// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.valid

@CompileStatic
class ResolveIndexPropertyTest extends GroovyResolveTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

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

  void 'test putAt with three parameters'() {
    fixture.addFileToProject 'classes.groovy', 'class A { def putAt(a, b, c) {} }'
    doLTest 'a[1, 2] = 3'
  }

  private doTest(String text, int methodIndex = -1) {
    doTest(text, true, methodIndex)
  }

  private doLTest(String text, int methodIndex = -1) {
    doTest(text, false, methodIndex)
  }

  private doTest(String text, boolean rValue, int methodIndex) {
    def file = (GroovyFile)fixture.configureByText('_.groovy', """\
def a = new A()
$text
""")
    def expression = file.statements.last()
    def reference = rValue ? ((GrIndexProperty)expression).getRValueReference()
                           : ((GrIndexProperty)((GrAssignmentExpression)expression).getLValue()).getLValueReference()
    def results = valid(reference.resolve(false))
    if (methodIndex < 0) {
      assert results.isEmpty()
    }
    else {
      assert results.size() == 1
      def resolved = (PsiMethod)results[0].element
      assert resolved
      assert resolved.containingClass.qualifiedName == 'A'
      assert ((GrTypeDefinition)resolved.containingClass).codeMethods[methodIndex] == resolved: resolved.parameterList.parameters*.type
    }
  }
}
