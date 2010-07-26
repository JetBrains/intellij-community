package org.jetbrains.plugins.groovy.lang

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.psi.search.searches.ReferencesSearch

/**
 * @author peter
 */
class LiteralConstructorUsagesTest extends LightCodeInsightFixtureTestCase {

  public void testListVariable() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
""")
    myFixture.addFileToProject "a.groovy", "Foo x = []"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }
  
  public void testListReturnValue() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
""")
    myFixture.addFileToProject "a.groovy", "Foo foo() { if (true) [] else return [] }"
    assertEquals(2, ReferencesSearch.search(foo.constructors[0]).findAll().size())
  }

  public void testListCast() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
""")
    myFixture.addFileToProject "a.groovy", "def x = (Foo) []"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }

  public void testListAsCast() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
""")
    myFixture.addFileToProject "a.groovy", "def x = [] as Foo"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }

}
