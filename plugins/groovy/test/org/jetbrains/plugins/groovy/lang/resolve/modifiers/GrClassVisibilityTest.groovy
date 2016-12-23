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
package org.jetbrains.plugins.groovy.lang.resolve.modifiers

import groovy.transform.CompileStatic

import static com.intellij.psi.PsiModifier.PACKAGE_LOCAL
import static com.intellij.psi.PsiModifier.PUBLIC

@CompileStatic
class GrClassVisibilityTest extends GrVisibilityTestBase {

  void 'test @PackageScope'() {
    def clazz = addClass('''\
@PackageScope
class A {}
''')
    assertVisibility clazz, PACKAGE_LOCAL
  }

  void 'test @PackageScope with explicit visibility'() {
    def clazz = addClass('''\
@PackageScope
public class A {}
''')
    assertVisibility clazz, PUBLIC
  }

  void 'test @PackageScope empty value'() {
    def clazz = addClass('''\
@PackageScope(value = [])
class A {}
''')
    assertVisibility clazz, PACKAGE_LOCAL
  }

  void 'test @PackageScope CLASS value'() {
    def clazz = addClass('''\
@PackageScope(value = [CLASS])
class A {}
''')
    assertVisibility clazz, PACKAGE_LOCAL
  }

  void 'test @PackageScope non CLASS value'() {
    def clazz = addClass('''\
@PackageScope(value = [FIELDS])
class A {}
''')
    assertVisibility clazz, PUBLIC
  }

  void 'test @PackageScope inner class'() {
    def clazz = addClass('''\
@PackageScope()
class A {
  class Inner {}
}
''')
    def inner = clazz.innerClasses.first()
    assertVisibility inner, PUBLIC
  }

  void 'test @PackageScope CLASS value inner class'() {
    def clazz = addClass('''\
@PackageScope(CLASS)
class A {
  class Inner {}
}
''')
    def inner = clazz.innerClasses.first()
    assertVisibility inner, PUBLIC
  }

  void 'test inner class @PackageScope'() {
    def clazz = addClass('''\
class A {
  @PackageScope
  class Inner {}
}
''')
    def inner = clazz.innerClasses.first()
    assertVisibility inner, PACKAGE_LOCAL
  }
}
