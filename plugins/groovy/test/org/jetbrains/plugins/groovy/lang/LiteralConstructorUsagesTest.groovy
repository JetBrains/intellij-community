// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.groovy.LightGroovyTestCase

class LiteralConstructorUsagesTest extends LightGroovyTestCase {

  void testList_AsCast() throws Exception {
    def foo = myFixture.addClass('class Foo { Foo() {} }')
    myFixture.addFileToProject "a.groovy", "def x = [] as Foo"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }

  void testMap_AsCast() throws Exception {
    def foo = myFixture.addClass('class Foo { Foo() {} }')
    myFixture.addFileToProject "a.groovy", "def x = [:] as Foo"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }

  void testLiteralConstructorWithNamedArgs() {
    myFixture.addFileToProject "a.groovy", """\
import groovy.transform.Immutable

@Immutable class Money {
    String currency
    int amount
}

Money d = [amount: 100, currency:'USA']
"""
    def constructors = myFixture.findClass("Money").constructors
    assertEquals 0, MethodReferencesSearch.search(constructors[0]).size()
    assertEquals 1, MethodReferencesSearch.search(constructors[1]).size()
  }
}
