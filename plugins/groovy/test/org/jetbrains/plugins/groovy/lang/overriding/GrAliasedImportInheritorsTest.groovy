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
package org.jetbrains.plugins.groovy.lang.overriding

import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

@CompileStatic
class GrAliasedImportInheritorsTest extends LightGroovyTestCase {

  void 'test aliased import'() {
    def iface = myFixture.addClass '''
package pckg;

public interface MyInterface {}
'''
    myFixture.addFileToProject 'a.groovy', """\
import pckg.MyInterface as Roo

class MyClass implements Roo {}
enum MyEnum implements Roo {}
trait MyTrait implements Roo {}
new Roo() {}
"""
    def inheritors = DirectClassInheritorsSearch.search(iface).findAll()
    assert inheritors.size() == 4
  }

  void 'test aliased import with generics'() {
    def iface = myFixture.addClass '''
package pckg;

public interface MyInterface<T> {}
'''
    myFixture.addFileToProject 'a.groovy', """\
import pckg.MyInterface as Roo

class MyClass implements Roo<
String> {}
enum MyEnum implements Roo<? extends Integer> {}
trait MyTrait implements Roo<Long
> {}
new Roo<Double>() {}
"""
    def inheritors = DirectClassInheritorsSearch.search(iface).findAll()
    assert inheritors.size() == 4
    inheritors.each {
      def type = (it as GrTypeDefinition).getSuperTypes(false).find {
        it.resolve() == iface
      }
      assert type != null
      def resolveResult = type.resolveGenerics()
      assert resolveResult.element == iface
      assert resolveResult.substitutor.substitute(iface.typeParameters.first())
    }
  }

  void 'test aliased import fqn'() {
    myFixture.addClass '''\
package foo;
public interface Foo{}
'''
    def iface = myFixture.addClass('''\
package bar;
public interface Bar{}
''')
    myFixture.addFileToProject 'a.groovy', '''\
package test

import foo.Foo as Bar

new bar.Bar(){}
'''
    def inheritors = DirectClassInheritorsSearch.search(iface).findAll()
    assert inheritors.size() == 1
    def clazz = inheritors.first()
    assert clazz
    assert clazz instanceof GrAnonymousClassDefinition
  }

  void 'test aliased import redefined in same package'() {
    def iface = myFixture.addClass('''\
package foo;
public interface Foo {}
''')
    myFixture.addClass '''\
package test;
public class Bar {}
'''
    myFixture.addFileToProject 'test/a.groovy', '''\
package test

import foo.Foo as Bar

new Bar() {} // inherits foo.Foo
'''
    def inheritors = DirectClassInheritorsSearch.search(iface).findAll()
    assert inheritors.size() == 1
    assert inheritors.first()
  }

  void 'test aliased import redefined in same file'() {
    def iface = myFixture.addClass('''\
package foo;
public interface Foo {}
''')
    myFixture.addFileToProject 'test/a.groovy', '''\
package test

import foo.Foo as Bar

class Bar {}

new Bar() {} // inherits foo.Foo
'''
    def inheritors = DirectClassInheritorsSearch.search(iface).findAll()
    assert inheritors.size() == 1
    assert inheritors.first()
  }
}
