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
package org.jetbrains.plugins.groovy.refactoring.rename

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.refactoring.rename.inplace.GrMethodInplaceRenameHandler

@CompileStatic
class InplaceRenameTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

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
