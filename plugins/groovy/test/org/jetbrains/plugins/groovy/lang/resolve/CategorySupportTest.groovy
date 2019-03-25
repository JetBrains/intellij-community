// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiModifier
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

@CompileStatic
class CategorySupportTest extends GroovyLatestTest implements ResolveTest {

  @Test
  void 'static category method outside of category'() {
    def resolved = resolveTest '''\
@Category(String) class C { def foo() {} }
C.<caret>foo("")
''', GrReflectedMethod
    assert resolved.modifierList.hasModifierProperty(PsiModifier.STATIC)
  }

  @Test
  void 'category method within category'() {
    resolveTest '''\
@Category(String)
class C {
  def foo() {}
  def usage() { <caret>foo() }
}
''', GrGdkMethod
  }

  @Test
  void 'explicit category method within category'() {
    resolveTest '''\
@Category(String)
class C {
  static def foo(String s) {}
  def usage() { <caret>foo() }
}
''', GrGdkMethod
  }

  @Test
  void 'property within category with explicit method'() {
    resolveTest '''\
@Category(String)
class C {
  static def foo(String s) {}
  def usage() { <caret>foo }
}
''', null
  }
}
