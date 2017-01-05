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

import static com.intellij.psi.PsiModifier.*

@CompileStatic
class GrFieldVisibilityTest extends GrVisibilityTestBase {

  void 'test property field'() {
    def clazz = addClass('''\
class A {
  def foo
}
''')
    def field = clazz.fields.first()
    assertVisibility field, PRIVATE
  }

  void 'test @PackageScope'() {
    def clazz = addClass('''\
class A {
  @PackageScope
  def foo
}
''')
    def field = clazz.fields.first()
    assertVisibility field, PACKAGE_LOCAL
  }

  void 'test @PackageScope with explicit visibility'() {
    def clazz = addClass('''\
class A {
  @PackageScope
  protected foo
}
''')
    def field = clazz.fields.first()
    assertVisibility field, PROTECTED
  }

  void 'test @PackageScope non empty'() {
    def clazz = addClass('''\
class A {
  @PackageScope([FIELDS])
  def foo
}
''')
    def field = clazz.fields.first()
    assertVisibility field, PRIVATE
  }

  void 'test @PackageScope on class'() {
    def clazz = addClass('''\
@PackageScope([FIELDS])
class A {
  def foo
}
''')
    def field = clazz.fields.first()
    assertVisibility field, PACKAGE_LOCAL
  }

  void 'test @PackageScope on class without FIELDS'() {
    def clazz = addClass('''\
@PackageScope
class A {
  def foo
}
''')
    def field = clazz.fields.first()
    assertVisibility field, PRIVATE
  }
}
