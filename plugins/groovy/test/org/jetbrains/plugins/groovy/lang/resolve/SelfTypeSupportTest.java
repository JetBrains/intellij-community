// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

@CompileStatic
class SelfTypeSupportTest extends LightGroovyTestCase {

  LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST
  String defaultImports = '''\
import groovy.transform.SelfType
'''

  void 'test resolve from within trait'() {
    def resolved = resolveByText('''\
interface I { def foo() }
@SelfType(I)
trait T {
  def bar() {
    fo<caret>o()
  }
}
''')
    assert (resolved as GrMethod).containingClass.name == 'I'
  }

  void 'test resolve outside trait'() {
    def resolved = resolveByText('''\
interface I { def foo() }
@SelfType(I)
trait T {}
def bar(T t) {
  t.f<caret>oo()
}
''')
    assert (resolved as GrMethod).containingClass.name == 'I'
  }

  void 'test resolve inside trait extending trait'() {
    def resolved = resolveByText('''\
interface I { def foo() }
@SelfType(I)
trait T {}
trait T2 extends T {
  def bar() {
    f<caret>oo()
  }
}
''')
    assert (resolved as GrMethod).containingClass.name == 'I'
  }

  void 'test do not count @SelfType on interfaces in hierarchy'() {
    assert resolveByText('''\
interface I { def foo() }
@SelfType(I)
interface II {}
trait T implements II {
    def bar() {
        fo<caret>o()
    }
}
''') == null
  }

  void 'test highlighting'() {
    doTestHighlighting '''\
interface I {}
@SelfType(I)
trait T {}
<error descr="@SelfType: Class 'A' does not inherit 'I'">class A implements T</error> {}

trait T2 extends T {}
<error descr="@SelfType: Class 'B' does not inherit 'I'">class B implements T2</error> {}
'''
  }

  PsiElement resolveByText(String text) {
    fixture.configureByText '_.groovy', "$defaultImports$text"
    file.findReferenceAt(editor.caretModel.offset).resolve()
  }

  void doTestHighlighting(String text) {
    fixture.configureByText '_.groovy', "$defaultImports$text"
    fixture.checkHighlighting()
  }

  void 'test do not process self types when not needed'() {
    def file = fixture.configureByText('_.groovy', """$defaultImports
interface I {}
@SelfType(I) 
trait T implements I {}
""") as GroovyFile
    assert file.typeDefinitions.last().superTypes.length != 0
  }
}
