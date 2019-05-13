// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.immutable

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl

@CompileStatic
class ImmutableTransformationSupportTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.addFileToProject 'classes.groovy', '''\
@groovy.transform.Immutable(copyWith = true)
class CopyWith {
  String stringProp
  Integer integerProp
}
'''
    fixture.enableInspections GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection
  }

  void 'test copyWith no properties'() {
    fixture.configureByText '_.groovy', '''\
@groovy.transform.Immutable(copyWith = true)
class CopyWithNoProps {}
new CopyWithNoProps().<warning descr="Cannot resolve symbol 'copyWith'">copyWith</warning>()
'''
    fixture.checkHighlighting()
  }

  void 'test copyWith exists with single parameter'() {
    fixture.addFileToProject '_.groovy', '''\
@groovy.transform.Immutable(copyWith = true)
class CopyWithExistingMethod {
  def copyWith(a) {}
}
'''
    def clazz = fixture.findClass('CopyWithExistingMethod')
    def methods = clazz.findMethodsByName('copyWith', false)
    assert methods.size() == 1
    assert methods.first() instanceof GrMethodImpl
  }

  void 'test copyWith resolve arguments'() {
    fixture.configureByText '_.groovy', '''\
def usage(CopyWith cw) {
  cw.copyWith(st<caret>ringProp: 'hello')
}
'''
    def ref = fixture.file.findReferenceAt(editor.caretModel.offset)
    assert ref
    def resolved = ref.resolve()
    assert resolved instanceof GrField
  }

  void 'test copyWith check argument types'() {
    fixture.configureByText '_.groovy', '''\
def usage(CopyWith cw) {
  cw.copyWith(
    stringProp: 42, 
    integerProp: <warning descr="Type of argument 'integerProp' can not be 'String'">'23'</warning>, 
    unknownProp: new Object()
  ) 
}
'''
    fixture.checkHighlighting()
  }

  void 'test copyWith complete properties'() {
    fixture.configureByText '_.groovy', '''\
def usage(CopyWith cw) {
  cw.copyWith(<caret>)
}
'''
    fixture.complete(CompletionType.BASIC)
    assert fixture.lookupElementStrings.containsAll(['stringProp', 'integerProp'])
  }
}
