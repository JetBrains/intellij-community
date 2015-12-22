/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.completion;


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
public class GroovyClassNameCompletionTest extends LightCodeInsightFixtureTestCase {
  private boolean old;

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/completion/classNameCompletion";
  }

  private void doTest() throws Exception {
    addClassToProject("a", "FooBar");
    myFixture.configureByFile(getTestName(false) + ".groovy");
    complete()
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  private void addClassToProject(@Nullable String packageName, @NotNull String name) throws IOException {
    myFixture.addClass("package $packageName; public class $name {}");
  }

  public void testInFieldDeclaration() throws Exception {doTest();}
  public void testInParameter() throws Exception {doTest();}
  public void testInImport() throws Exception {
    addClassToProject("a", "FooBar")
    myFixture.configureByFile(getTestName(false) + ".groovy")
    complete()
    myFixture.type('\n')
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testWhenClassExistsInSamePackage() throws Exception {
    addClassToProject("a", "FooBar")
    myFixture.configureByFile(getTestName(false) + ".groovy")
    complete()
    def lookup = LookupManager.getActiveLookup(myFixture.editor)
    lookup.currentItem = lookup.items[1]
    myFixture.type('\n')
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testInComment() throws Exception {doTest();}
  public void testInTypeElementPlace() throws Exception {doTest();}
  public void testWhenImportExists() throws Exception{doTest();}

  public void testFinishByDot() throws Exception{
    addClassToProject("a", "FooBar");
    myFixture.configureByText("a.groovy", "FB<caret>a")
    complete()
    myFixture.type '.'.charAt(0)
    myFixture.checkResult "a.FooBar.<caret>a"
  }

  private LookupElement[] complete() {
    myFixture.complete(CompletionType.BASIC, 2)
  }

  public void testDelegateBasicToClassName() throws Exception{
    addClassToProject("a", "FooBarGooDoo");
    myFixture.configureByText("a.groovy", "FBGD<caret>a")
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.type '.'
    myFixture.checkResult "a.FooBarGooDoo.<caret>a"
  }

  public void testDelegateBasicToClassNameAutoinsert() throws Exception{
    addClassToProject("a", "FooBarGooDoo");
    myFixture.configureByText("a.groovy", "FBGD<caret>")
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.checkResult """\
import a.FooBarGooDoo

FooBarGooDoo<caret>"""
  }

  public void testImportedStaticMethod() throws Exception {
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

  public void testImportedStaticField() throws Exception {
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

  public void testImportedInterfaceConstant() throws Exception {
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

  public void testQualifiedStaticMethod() throws Exception {
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

  public void testQualifiedStaticMethodIfThereAreAlreadyStaticImportsFromThatClass() throws Exception {
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

  public void testNewClassName() {
    addClassToProject("foo", "Fxoo")
    myFixture.configureByText("a.groovy", "new Fxo<caret>\n")
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.checkResult """import foo.Fxoo

new Fxoo()<caret>\n"""

  }

  public void testNewImportedClassName() {
    myFixture.configureByText("a.groovy", "new ArrayIndexOut<caret>\n")
    myFixture.completeBasic()
    myFixture.checkResult "new ArrayIndexOutOfBoundsException(<caret>)\n"
  }

  public void testOnlyAnnotationsAfterAt() {
    myFixture.addClass "class AbcdClass {}; @interface AbcdAnno {}"
    myFixture.configureByText "a.groovy", "@Abcd<caret>"
    complete()
    assert myFixture.lookupElementStrings[0] == 'AbcdAnno'
  }

  public void testOnlyExceptionsInCatch() {
    myFixture.addClass "class AbcdClass {}; class AbcdException extends Throwable {}"
    myFixture.configureByText "a.groovy", "try {} catch (Abcd<caret>"
    complete()
    assert myFixture.lookupElementStrings[0] == 'AbcdException'
  }

  public void testClassNameInMultilineString() {
    myFixture.configureByText "a.groovy", 'def s = """a\nAIOOBE<caret>\na"""'
    complete()
    myFixture.checkResult 'def s = """a\njava.lang.ArrayIndexOutOfBoundsException<caret>\na"""'
  }

  public void testDoubleClass() {
    myFixture.addClass "package foo; public class Zooooooo {}"
    myFixture.configureByText("a.groovy", """import foo.Zooooooo
Zoooo<caret>x""")
    assertOneElement(myFixture.completeBasic())
  }

  public void testClassOnlyOnce() {
    myFixture.addClass('class FooBarGoo {}')
    myFixture.configureByText('a.groovy', 'FoBaGo<caret>')
    assert !complete()
    myFixture.checkResult('''FooBarGoo<caret>''')
  }

  public void testMethodFromTheSameClass() {
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

  public void testInnerClassCompletion() {
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

}
