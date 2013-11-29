/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang

import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.groovy.LightGroovyTestCase

/**
 * @author peter
 */
class LiteralConstructorUsagesTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    null
  }

  @Override
  protected void setUp() {
    super.setUp();
    myFixture.addClass("package groovy.lang; public @interface Typed {}");
  }

  public void testList_Variable() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
""")
    myFixture.addFileToProject "a.gpp", "Foo x = []"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }

  public void testList_ReturnValue() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
""")
    myFixture.addFileToProject "a.groovy", """
@Typed Foo foo() {
  if (true) []
  else return []
}
Foo untyped() { [] }
@Typed Foo bar() { [] }
"""
    assertEquals(4, ReferencesSearch.search(foo.constructors[0]).findAll().size())
  }

  public void testList_Cast() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
""")
    myFixture.addFileToProject "a.gpp", "def x = (Foo) []"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }

  public void testList_AsCast() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
}""")
    myFixture.addFileToProject "a.groovy", "def x = [] as Foo"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }

  public void testMap_AsCast() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
}""")
    myFixture.addFileToProject "a.groovy", "def x = [:] as Foo"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }

  public void testMapSuper_AsCast() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo(int a) {}
    }
}""")
    myFixture.addFileToProject "a.gpp", "def x = ['super':[2]] as Foo"
    myFixture.addFileToProject "c.gpp", "def x = [super:2] as Foo"

    assertEquals(2, ReferencesSearch.search(foo.constructors[0]).findAll().size())
  }

  public void testOverloadedConstructorUsages() throws Exception {
    def foo = myFixture.addClass("""
      class Foo {
          Foo() {}
          Foo(int a) {}
      }
      """)

    myFixture.addFileToProject "a.gpp", """
      Foo b = []
      Foo b1 = [2]
      """
    assertEquals(2, MethodReferencesSearch.search(foo.constructors[0], false).findAll().size())
    assertEquals(2, MethodReferencesSearch.search(foo.constructors[1], false).findAll().size())
  }

  public void testLiteralWithinALiteral() throws Exception {
    myFixture.addFileToProject "a.gpp", """
class Foo {
  Foo(Bar b) {}
}

class Bar {
  Bar(String s) {}
}

Foo f = [new Bar('a')]
Foo f2 = ['super':['super':'a']]
"""
    assertEquals 1, MethodReferencesSearch.search(myFixture.findClass("Bar").constructors[0]).findAll().size()
  }

  public void testCannibalisticConstructor() throws Exception {
    myFixture.addFileToProject "a.gpp", """
      class Foo {
        Foo(Foo... children) {}
      }
      Foo f = [new Foo('a')]
    """
    assertEquals 2, MethodReferencesSearch.search(myFixture.findClass("Foo").constructors[0]).findAll().size()
  }

  void testLiteralConstructorWithNamedArgs() {
    addLinkedHashMap()
    myFixture.addFileToProject "a.groovy", """\
import groovy.transform.Immutable

@Immutable class Money {
    String currency
    int amount
}

Money d = [amount: 100, currency:'USA']
"""
    assertEquals 1, MethodReferencesSearch.search(myFixture.findClass("Money").constructors[0]).findAll().size()
  }
}
