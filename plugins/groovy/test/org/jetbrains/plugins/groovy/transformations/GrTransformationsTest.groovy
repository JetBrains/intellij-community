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
package org.jetbrains.plugins.groovy.transformations

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase

@CompileStatic
class GrTransformationsTest extends GroovyResolveTestCase {

  LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test skip compiled classes while checking if to include synthetic members'() {
    def resolved = resolveByText('''
class MyBase {}
trait MyT {}
class MyInheritor extends Script implements MyT {
  def ppp
}
new MyInheritor().pp<caret>p
''', GrAccessorMethod)
    assert resolved.getName() == 'getPpp'
  }

  void 'test transform anonymous classes'() {
    myFixture.addFileToProject('Base.groovy', '''\
abstract class Base {
  abstract getFoo()
}
''')
    myFixture.configureByText('a.groovy', '''\
class A {
  def baz = new Base() { // no error, getFoo() exists
    def foo = 1
  }
}
''')
    myFixture.checkHighlighting()
  }
}
