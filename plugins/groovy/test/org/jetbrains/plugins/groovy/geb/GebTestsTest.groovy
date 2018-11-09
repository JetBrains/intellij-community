// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.geb

import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Sergey Evdokimov
 */
class GebTestsTest extends AbstractGebLightTestCase {

  void testSpockTestMemberCompletion() {
    myFixture.configureByText("FooTest.groovy", """
class FooTest extends geb.spock.GebReportingSpec {
    def testFoo() {
      when:
      <caret>
    }
}
""")

    TestUtils.checkCompletionContains(myFixture, "\$()", "to()", "go()", "currentWindow", "verifyAt()", "title")
  }

  void testJUnitTestMemberCompletion() {
    myFixture.configureByText("FooTest.groovy", """
class FooTest extends geb.junit4.GebReportingTest {
    def testFoo() {
      <caret>
    }
}
""")

    TestUtils.checkCompletionContains(myFixture, "\$()", "to()", "go()", "currentWindow", "verifyAt()", "title")
  }
  
  void testTestNGTestMemberCompletion() {
    myFixture.configureByText("FooTest.groovy", """
class FooTest extends geb.testng.GebReportingTest {
    def testFoo() {
      <caret>
    }
}
""")

    TestUtils.checkCompletionContains(myFixture, "\$()", "to()", "go()", "currentWindow", "verifyAt()", "title")
  }

  void testFieldNameCompletion() {
    myFixture.configureByText("FooTest.groovy", """
class FooTest extends geb.Page {

    static <caret>

    static content = {}
}
""")

    TestUtils.checkCompletionContains(myFixture, "at", "url")
    assert !myFixture.getLookupElementStrings().contains("content")
  }

  void testResolveFromParent() {
    myFixture.configureByText("A.groovy", """
class A extends ParentClass {
  static at = {
    aaa.<caret>
  }
}

class ParentClass extends geb.Page {
  static content = {
    aaa { \$('#fieldA') }
  }
}
""")

    TestUtils.checkCompletionContains(myFixture, "allElements()", "add()", "firstElement()")
  }

  void testResolveContentFieldsAndMethods() {
    myFixture.configureByText("PageWithContent.groovy", """
class PageWithContent extends geb.Page {
  static content = {
    button { \$('button') }
    formField { String name -> \$('input', name: name) }
  }
  
  def someMethod() {
    <caret>
  }
}
""")

    TestUtils.checkCompletionContains(myFixture, "button", "formField()")
  }

  void testContentElementsCompletionType() {
    myFixture.configureByText("PageWithContent.groovy", """
class PageWithContent extends geb.Page {
  static content = {
    button { \$('button') }
    formField { String name -> \$('input', name: name) }
  }
  
  def someMethod() {
    <caret>
  }
}
""")

    TestUtils.checkCompletionType(myFixture, "button", "geb.navigator.Navigator")
    TestUtils.checkCompletionType(myFixture, "formField", "geb.navigator.Navigator")
  }

  void testContentMethodReturnType() {
    myFixture.configureByText("PageWithContent.groovy", """
class PageWithContent extends geb.Page {
  static content = {
    formField { String name -> \$('input', name: name) }
  }
  
  def someMethod() {
    formField('username').<caret>
  }
}
""")

    TestUtils.checkCompletionContains(myFixture, "allElements()", "add()", "firstElement()")
  }

  void testCheckHighlighting() {
    myFixture.enableInspections(GroovyAssignabilityCheckInspection)

    myFixture.configureByText("A.groovy", """
class A extends geb.Page {

  static someField = "abc"

  static at = {
    int x = bbb
    Boolean s = bbb
  }

  static content = {
    someField<warning descr="'someField' cannot be applied to '()'">()</warning>
    aaa { "Aaa" }
    bbb { aaa.length() }
    ccc(required: false) { aaa.length() }
    eee(1, required: false) { aaa.length() }
  }
}
""")

    myFixture.checkHighlighting(true, false, true)
    TestUtils.checkResolve(myFixture.file, "eee")
  }

  void testRename() {
    def a = myFixture.addFileToProject("A.groovy", """
class A extends geb.Page {
  static at = {
    String x = aaa
  }

  static content = {
    aaa { "Aaa" }
    bbb { aaa.length() }
  }
}
""")

    myFixture.configureByText("B.groovy", """
class B extends A {
  static at = {
    def x = aaa<caret>
  }

  static content = {
    ttt { bbb + aaa.length() }
  }
}
""")

    myFixture.renameElementAtCaret("aaa777")

    myFixture.checkResult("""
class B extends A {
  static at = {
    def x = aaa777
  }

  static content = {
    ttt { bbb + aaa777.length() }
  }
}
""")

    assert a.text == """
class A extends geb.Page {
  static at = {
    String x = aaa777
  }

  static content = {
    aaa777 { "Aaa" }
    bbb { aaa777.length() }
  }
}
"""
  }

  void testRename2() {
    myFixture.configureByText("A.groovy", """
class A extends geb.Page {
  static at = {
    String x = aaa
  }

  static content = {
    aaa<caret> { "Aaa" }
    bbb { aaa.length() }
  }
}
""")

    def b = myFixture.addFileToProject("B.groovy", """
class B extends A {
  static at = {
    def x = aaa
  }

  static content = {
    ttt { bbb + aaa.length() }
  }
}
""")

    myFixture.renameElementAtCaret("aaa777")

    assert b.text == """
class B extends A {
  static at = {
    def x = aaa777
  }

  static content = {
    ttt { bbb + aaa777.length() }
  }
}
"""

    myFixture.checkResult("""
class A extends geb.Page {
  static at = {
    String x = aaa777
  }

  static content = {
    aaa777 { "Aaa" }
    bbb { aaa777.length() }
  }
}
""")
  }

}
