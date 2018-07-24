// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrConstructorImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultConstructor
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.jetbrains.plugins.groovy.util.TestUtils
import org.jetbrains.plugins.groovy.util.TypingTest

@CompileStatic
class MethodReferenceTest extends GroovyResolveTestCase implements TypingTest, ResolveTest, HighlightingTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0
  final String basePath = TestUtils.testDataPath + 'lang/methodReferences/'
  final Collection<Class<? extends LocalInspectionTool>> inspections = [GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection]

  void 'test method reference in static context'() {
    fixture.addFileToProject 'classes.groovy', '''\
class C { 
  C(int a) {}
  static foo() {}
'''
    def results = multiResolveByText 'C::<caret>foo'
    assert results.size() == 1
    assert results[0].element instanceof GrMethodImpl
  }

  void 'test multiple constructors'() {
    def results = multiResolveByText '''\
class D {
  D(a) {}
  D(a, b) {}
}
D::<caret>new
'''
    assert results.size() == 2
    for (result in results) {
      def element = results[0].element
      assert element instanceof GrConstructorImpl
    }
  }

  void 'test constructor in static context'() {
    def results = multiResolveByText '''\
class D { D() {} }
D::<caret>new
'''
    assert results.size() == 1
    assert results[0].element instanceof GrConstructorImpl
  }

  void 'test static method in static context'() {
    def results = multiResolveByText '''\
class D { static 'new'() {} }
D::<caret>new
'''
    assert results.size() == 2
    assert results[0].element instanceof GrMethodImpl
    assert results[1].element instanceof DefaultConstructor
  }


  void 'test constructor in instance context'() {
    def results = multiResolveByText '''\
class D { D() {} }
def d = new D()
d::<caret>new
'''
    assert results.size() == 0
  }

  void 'test instance method in instance context'() {
    def results = multiResolveByText '''\
class D { def 'new'() {} }
def d = new D()
d::<caret>new
'''
    assert results.size() == 1
    assert results[0].element instanceof GrMethodImpl
  }

  void 'test typing'() {
    fixture.addFileToProject 'classes.groovy', '''\
class D {
  def prop1
  D() {}
  D(int a) {}
  static long 'new'(String b) { 42l }
}
'''
    typingTest 'def ref = D::new; ref(1)', 'D'
    typingTest 'def ref = D::new; ref("hello")', 'long'
  }

  void 'test array typing'() {
    typingTest 'Integer[]::new(0)', 'java.lang.Integer[]'
    typingTest 'int[][]::new(0)', 'int[][]'
    typingTest 'int[][]::new(1, 2)', 'int[][]'
  }

  void 'test constructors highlighting'() { highlightingTest() }

  void 'test array constructors highlighting'() { highlightingTest() }

  void 'test constructor search and rename'() {
    fixture.addFileToProject 'classes.groovy', 'class A { A(String p){} }'
    configureByText '''\
<caret>A::new
A::'new'
A::"new"
'''

    def clazz = fixture.findClass 'A'
    def constructor = clazz.constructors[0]
    assert MethodReferencesSearch.search(constructor).size() == 3

    fixture.renameElementAtCaret 'B'
    fixture.checkResult '''\
B::new
B::'new'
B::"new"
'''
  }
}
