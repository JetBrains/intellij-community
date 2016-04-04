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

  void testLiteralConstructorWithNamedArgs() {
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
