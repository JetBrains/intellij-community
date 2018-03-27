// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.StaticallyImportable
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
class GroovySmartCompletionTest extends GroovyCompletionTestBase {
  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}groovy/completion/smart"
  }

  void testSmartCompletionAfterNewInDeclaration() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    myFixture.complete(CompletionType.SMART)
    assertOrderedEquals(myFixture.lookupElementStrings, "Bar", "Foo")
  }

  void testCaretAfterSmartCompletionAfterNewInDeclaration() throws Throwable { doSmartTest() }

  void testSmartCompletionAfterNewInDeclarationWithArray() throws Throwable { doSmartTest() }

  void testSmartCompletionAfterNewInDeclarationWithIntArray() throws Throwable { doSmartTest() }

  void testShortenNamesInSmartCompletionAfterNewInDeclaration() throws Throwable { doSmartTest() }

  void testSmartAfterNewInCall() throws Throwable { doSmartTest() }

  void testInnerClassInStaticMethodCompletion() throws Throwable {
    doVariantableTest(null, "", CompletionType.SMART, CompletionResult.notContain, 'Inner')
  }

  void testSmartCompletionInAssignmentExpression() throws Throwable { doSmartTest() }

  void testSimpleMethodParameter() throws Throwable {
    doSmartCompletion("d1", "d2")
  }

  void testReturnStatement() throws Exception {
    doSmartCompletion("b", "b1", "b2", "foo")
  }

  void testIncSmartCompletion() throws Exception {
    doSmartCompletion("a", "b")
  }

  void testInheritConstructorsAnnotation() throws Throwable {
    myFixture.addFileToProject("groovy/transform/InheritConstructors.java", "package groovy.transform;\n" +
                                                                            "\n" +
                                                                            "import java.lang.annotation.ElementType;\n" +
                                                                            "import java.lang.annotation.Retention;\n" +
                                                                            "import java.lang.annotation.RetentionPolicy;\n" +
                                                                            "import java.lang.annotation.Target;@Retention(RetentionPolicy.SOURCE)\n" +
                                                                            "@Target({ElementType.TYPE})\n" +
                                                                            "public @interface InheritConstructors {\n" +
                                                                            "}")
    doSmartTest()
  }

  void testSmartCastCompletion() { doSmartTest() }

  void testSmartCastCompletionWithoutRParenth() { doSmartTest() }

  void testSmartCastCompletionWithRParenth() { doSmartTest() }

  void testDontCompletePrivateMembers() { doSmartCompletion "foo1", "foo2", "getFoo1", "getFoo2" }

  void testEnumMembersInAssignment() { doSmartCompletion "IN_STOCK", "NOWHERE", "ORDERED", "valueOf" }

  void testEnumMembersInAssignmentInsideEnum() { doSmartCompletion "IN_STOCK", "NOWHERE", "ORDERED", "next", "previous", "valueOf" }

  void testPreferVarargElement() {
    doCompletionTest(null, null, '\n', CompletionType.SMART)
  }

  void testGlobalStaticMembers() {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    myFixture.complete(CompletionType.SMART, 2)
    assertOrderedEquals(myFixture.lookupElementStrings, 'SUBSTRING', 'createExpected', 'createSubGeneric', 'SUB_RAW')
  }

  void testGlobalStaticMembersForString() {
    myFixture.configureByText('a.groovy', ''''\
class Foo {
  public static String string(){}
  public static Comparator<String> comparator(){}
  public static String foo(){}
}

String s = <caret>
''')

    myFixture.complete(CompletionType.SMART, 2)
    assertOrderedEquals(myFixture.lookupElementStrings,
                        'foo',
                        'string')
  }

  void testGlobalListCreators() {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    myFixture.complete(CompletionType.SMART, 2)
    assertOrderedEquals(myFixture.lookupElementStrings, 'createGenericList', 'createStringList')
  }

  void testNativeList() {doSmartCompletion('a1', 'a2')}

  void testMembersImportStatically() {
    myFixture.addClass("""
class Expected {
  public static final Expected fooField;
  public static Expected fooMethod() {}
}
""")
    myFixture.configureByText 'a.groovy', 'Expected exp = fo<caret>'
    def items = myFixture.complete(CompletionType.SMART)
    assert myFixture.lookupElementStrings == ['fooField', 'fooMethod']
    assert items[0].as(StaticallyImportable.CLASS_CONDITION_KEY).canBeImported()
    assert items[1].as(StaticallyImportable.CLASS_CONDITION_KEY).canBeImported()
    items[0].as(StaticallyImportable.CLASS_CONDITION_KEY).setShouldBeImported(true)
    myFixture.type('\n')
    myFixture.checkResult '''import static Expected.fooField

Expected exp = fooField'''
  }

  void testThrow() {
    myFixture.configureByText('_a.groovy', '''\
throw new RunEx<caret>
''')
    myFixture.complete(CompletionType.SMART)
    myFixture.checkResult('''\
throw new RuntimeException()
''')
  }
  
  void testInnerClassReferenceWithoutQualifier() {
    doSmartTest()
  }

  void testAnonymousClassCompletion() {
    doSmartTest('''\
Runnable r = new Run<caret>
''', '''\
Runnable r = new Runnable() {
    @Override
    void run() {<caret><selection></selection>

    }
}
''')
  }

  void testReassignedVar() {
    doSmartTest('''\
def foo(int x){}
def aaaa = '123'
aaaa = 123

foo(aaa<caret>)
''', '''\
def foo(int x){}
def aaaa = '123'
aaaa = 123

foo(aaaa<caret>)
''')
  }

  void testFieldWithInstanceof() {
    addCompileStatic()
    doSmartTest('''\
@CompileStatic
class A {
  def field
  def foo(int x) {}
  def bar() {
    if (field instanceof Integer) {
      foo(fiel<caret>)
    }
  }
}
''', '''\
@CompileStatic
class A {
  def field
  def foo(int x) {}
  def bar() {
    if (field instanceof Integer) {
      foo(field)
    }
  }
}
''')
  }

  void testFieldWithAssignment() {
    addCompileStatic()
    myFixture.configureByText('a.groovy', '''\
import groovy.transform.CompileStatic
@CompileStatic
class A {
  def field
  def foo(int x) {}
  
  def bar() {
    field = 1
    foo(fiel<caret>)
  }
}
''')

    myFixture.complete(CompletionType.SMART)
    assertEquals([], myFixture.lookupElementStrings)
  }

  void testQualifiedNameAfterNew() {
    myFixture.addClass('''\
package foo;
public class User<T> {}
''')
    doSmartTest('''\
class User {
}
def a(foo.User<String> f){}

a(new Us<caret>)
''', '''\
class User {
}
def a(foo.User<String> f){}

a(new foo.User<String>()<caret>)
''')
  }

  void testBinaryExpr() {
    doSmartTest('''
class A {
  def plus(String s) {}
}

print new A() + new <caret>
''', '''
class A {
  def plus(String s) {}
}

print new A() + new String(<caret>)
'''
)
  }
}
