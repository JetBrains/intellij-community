// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.rename

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.refactoring.rename.inplace.GrMethodInplaceRenameHandler

@CompileStatic
class InplaceRenameTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  private void doTest(String before, String newName, String after) {
    fixture.configureByText '_.groovy', before
    CodeInsightTestUtil.doInlineRename(new GrMethodInplaceRenameHandler(), newName, fixture)
    fixture.checkResult after
  }

  void 'test inplace rename method add space'() {
    doTest(/\
def fo<caret>o() {}

foo()
/, 'foo bar', /\
def 'foo bar'() {}

'foo bar'()
/)
  }

  void "test inplace rename method remove space '"() {
    doTest(/\
def 'foo<caret> bar'() {}

'foo bar'()
'''foo bar'''()
"foo bar"()
"""foo bar"""()
/, 'foo', /\
def foo() {}

foo()
foo()
foo()
foo()
/)
  }

  void "test inplace rename method remove space '''"() {
    doTest(/\
def '''foo<caret> bar'''() {}
'foo bar'()
'''foo bar'''()
"foo bar"()
"""foo bar"""()
/, 'foo', /\
def foo() {}
foo()
foo()
foo()
foo()
/)
  }

  void 'test inplace rename method remove space "'() {
    doTest(/\
def "foo<caret> bar"() {}

'foo bar'()
'''foo bar'''()
"foo bar"()
"""foo bar"""()
/, 'foo', /\
def foo() {}

foo()
foo()
foo()
foo()
/)
  }

  void 'test inplace rename method remove space """'() {
    doTest(/\
def """foo<caret> bar"""() {}

'foo bar'()
'''foo bar'''()
"foo bar"()
"""foo bar"""()
/, 'foo', /\
def foo() {}

foo()
foo()
foo()
foo()
/)
  }

  void "test inplace rename method '"() {
    doTest(/\
def 'foo<caret> bar'() {}

'foo bar'()
'''foo bar'''()
"foo bar"()
"""foo bar"""()
/, 'foo baz', /\
def 'foo baz'() {}

'foo baz'()
'foo baz'()
'foo baz'()
'foo baz'()
/)
  }

  void "test inplace rename method '''"() {
    doTest(/\
def '''foo<caret> bar'''() {}

'foo bar'()
'''foo bar'''()
"foo bar"()
"""foo bar"""()
/, 'foo baz', /\
def 'foo baz'() {}

'foo baz'()
'foo baz'()
'foo baz'()
'foo baz'()
/)
  }

  void 'test inplace rename method "'() {
    doTest(/\
def "foo<caret> bar"() {}

'foo bar'()
'''foo bar'''()
"foo bar"()
"""foo bar"""()
/, 'foo baz', /\
def 'foo baz'() {}

'foo baz'()
'foo baz'()
'foo baz'()
'foo baz'()
/)
  }

  void 'test inplace rename method """'() {
    doTest(/\
def """foo<caret> bar"""() {}

'foo bar'()
'''foo bar'''()
"foo bar"()
"""foo bar"""()
/, 'foo baz', /\
def 'foo baz'() {}

'foo baz'()
'foo baz'()
'foo baz'()
'foo baz'()
/)
  }
}
