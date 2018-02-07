// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion


import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.StaticallyImportable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
class GroovyClassNameCompletionTest extends LightCodeInsightFixtureTestCase {
  private boolean old

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/completion/classNameCompletion"
  }

  private void doTest() throws Exception {
    addClassToProject("a", "FooBar")
    myFixture.configureByFile(getTestName(false) + ".groovy")
    complete()
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }

  private void addClassToProject(@Nullable String packageName, @NotNull String name) throws IOException {
    myFixture.addClass("package $packageName; public class $name {}")
  }

  void testInFieldDeclaration() throws Exception { doTest() }

  void testInParameter() throws Exception { doTest() }

  void testInImport() throws Exception {
    addClassToProject("a", "FooBar")
    myFixture.configureByFile(getTestName(false) + ".groovy")
    complete()
    myFixture.type('\n')
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }

  void testWhenClassExistsInSamePackage() throws Exception {
    addClassToProject("a", "FooBar")
    myFixture.configureByFile(getTestName(false) + ".groovy")
    complete()
    def lookup = LookupManager.getActiveLookup(myFixture.editor)
    lookup.currentItem = lookup.items[1]
    myFixture.type('\n')
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }

  void testInComment() throws Exception { doTest() }

  void testInTypeElementPlace() throws Exception { doTest() }

  void testWhenImportExists() throws Exception { doTest() }

  void testFinishByDot() throws Exception {
    addClassToProject("a", "FooBar")
    myFixture.configureByText("a.groovy", "FB<caret>a")
    complete()
    myFixture.type '.'.charAt(0)
    myFixture.checkResult '''\
import a.FooBar

FooBar.<caret>a'''
  }

  private LookupElement[] complete() {
    myFixture.complete(CompletionType.BASIC, 2)
  }

  void testDelegateBasicToClassName() throws Exception {
    addClassToProject("a", "FooBarGooDoo")
    myFixture.configureByText("a.groovy", "FBGD<caret>a")
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.type '.'
    myFixture.checkResult '''\
import a.FooBarGooDoo

FooBarGooDoo.<caret>a'''
  }

  void testDelegateBasicToClassNameAutoinsert() throws Exception {
    addClassToProject("a", "FooBarGooDoo")
    myFixture.configureByText("a.groovy", "FBGD<caret>")
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.checkResult """\
import a.FooBarGooDoo

FooBarGooDoo<caret>"""
  }

  void testImportedStaticMethod() throws Exception {
    myFixture.addFileToProject("b.groovy", """
class Foo {
  static def abcmethod1(int a) {}
  static def abcmethod2(int a) {}
}""")
    myFixture.configureByText("a.groovy", """def foo() {
  abcme<caret>
}""")
    def item = complete()[0]

    LookupElementPresentation presentation = renderElement(item)
    assert "Foo.abcmethod1" == presentation.itemText
    assert presentation.tailText == "(int a)"

    ((StaticallyImportable) item).shouldBeImported = true
    myFixture.type('\n')
    myFixture.checkResult """import static Foo.abcmethod1

def foo() {
  abcmethod1(<caret>)
}"""

  }

  void testImportedStaticField() throws Exception {
    myFixture.addFileToProject("b.groovy", """
class Foo {
  static def abcfield1
  static def abcfield2
}""")
    myFixture.configureByText("a.groovy", """def foo() {
  abcfi<caret>
}""")
    def item = complete()[0]
    ((StaticallyImportable) item).shouldBeImported = true
    myFixture.type('\n')
    myFixture.checkResult """import static Foo.abcfield1

def foo() {
  abcfield1<caret>
}"""

  }

  void testImportedInterfaceConstant() throws Exception {
    myFixture.addFileToProject("b.groovy", """
interface Foo {
  static def abcfield1 = 2
  static def abcfield2 = 3
}""")
    myFixture.configureByText("a.groovy", """def foo() {
  abcfi<caret>
}""")
    def item = complete()[0]
    ((StaticallyImportable) item).shouldBeImported = true
    myFixture.type('\n')
    myFixture.checkResult """import static Foo.abcfield1

def foo() {
  abcfield1<caret>
}"""

  }

  void testQualifiedStaticMethod() throws Exception {
    myFixture.addFileToProject("foo/b.groovy", """package foo
class Foo {
  static def abcmethod(int a) {}
}""")
    myFixture.configureByText("a.groovy", """def foo() {
  abcme<caret>
}""")
    complete()
    myFixture.checkResult """import foo.Foo

def foo() {
  Foo.abcmethod(<caret>)
}"""

  }

  void testQualifiedStaticMethodIfThereAreAlreadyStaticImportsFromThatClass() throws Exception {
    myFixture.addFileToProject("foo/b.groovy", """package foo
class Foo {
  static def abcMethod() {}
  static def anotherMethod() {}
}""")
    myFixture.configureByText("a.groovy", """
import static foo.Foo.anotherMethod

anotherMethod()
abcme<caret>x""")
    def element = assertOneElement(complete()[0])

    LookupElementPresentation presentation = renderElement(element)
    assert "abcMethod" == presentation.itemText
    assert presentation.tailText == "() in Foo (foo)"

    myFixture.type('\t')
    myFixture.checkResult """\
import static foo.Foo.abcMethod
import static foo.Foo.anotherMethod

anotherMethod()
abcMethod()<caret>"""

  }

  private LookupElementPresentation renderElement(LookupElement element) {
    return LookupElementPresentation.renderElement(element)
  }

  void testNewClassName() {
    addClassToProject("foo", "Fxoo")
    myFixture.configureByText("a.groovy", "new Fxo<caret>\n")
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.checkResult """import foo.Fxoo

new Fxoo()<caret>\n"""

  }

  void testNewImportedClassName() {
    myFixture.configureByText("a.groovy", "new ArrayIndexOut<caret>\n")
    myFixture.completeBasic()
    myFixture.checkResult "new ArrayIndexOutOfBoundsException(<caret>)\n"
  }

  void testOnlyAnnotationsAfterAt() {
    myFixture.addClass "class AbcdClass {}; @interface AbcdAnno {}"
    myFixture.configureByText "a.groovy", "@Abcd<caret>"
    complete()
    assert myFixture.lookupElementStrings[0] == 'AbcdAnno'
  }

  void testOnlyExceptionsInCatch() {
    myFixture.addClass "class AbcdClass {}; class AbcdException extends Throwable {}"
    myFixture.configureByText "a.groovy", "try {} catch (Abcd<caret>"
    complete()
    assert myFixture.lookupElementStrings[0] == 'AbcdException'
  }

  void testClassNameInMultilineString() {
    myFixture.configureByText "a.groovy", 'def s = """a\nAIOOBE<caret>\na"""'
    complete()
    myFixture.checkResult 'def s = """a\njava.lang.ArrayIndexOutOfBoundsException<caret>\na"""'
  }

  void testDoubleClass() {
    myFixture.addClass "package foo; public class Zooooooo {}"
    myFixture.configureByText("a.groovy", """import foo.Zooooooo
Zoooo<caret>x""")
    assertOneElement(myFixture.completeBasic())
  }

  void testClassOnlyOnce() {
    myFixture.addClass('class FooBarGoo {}')
    myFixture.configureByText('a.groovy', 'FoBaGo<caret>')
    assert !complete()
    myFixture.checkResult('''FooBarGoo<caret>''')
  }

  void testMethodFromTheSameClass() {
    myFixture.configureByText("a.groovy", """
class A {
  static void foo() {}

  static void goo() {
    f<caret>
  }
}
""")
    def items = complete()
    def fooItem = items.find { renderElement(it).itemText == 'foo' }
    LookupManager.getActiveLookup(myFixture.editor).currentItem = fooItem
    myFixture.type '\n'
    myFixture.checkResult '''
class A {
  static void foo() {}

  static void goo() {
    foo()<caret>
  }
}
'''
  }

  void testInnerClassCompletion() {
    myFixture.addClass('''\
package foo;

public class Upper {
  public static class Inner {}
}
''')

    myFixture.configureByText('_.groovy', '''\
import foo.Upper
print new Inner<caret>
''')
    myFixture.complete(CompletionType.BASIC)
    myFixture.type('\n')

    myFixture.checkResult('''\
import foo.Upper
print new Upper.Inner()
''')
  }

  void "test complete class within 'in' package"() {
    myFixture.with {
      addClass '''\
package in.foo.com;
public class Foooo {}
'''
      configureByText '_.groovy', 'Fooo<caret>'
      complete CompletionType.BASIC
      type '\n'
      checkResult '''import in.foo.com.Foooo

Foooo<caret>'''
    }
  }

  void "test complete class within 'def' package"() {
    myFixture.with {
      addClass '''\
package def.foo.com;
public class Foooo {}
'''
      configureByText '_.groovy', 'Fooo<caret>'
      complete CompletionType.BASIC
      type '\n'
      checkResult '''import def.foo.com.Foooo

Foooo<caret>'''
    }
  }
}
