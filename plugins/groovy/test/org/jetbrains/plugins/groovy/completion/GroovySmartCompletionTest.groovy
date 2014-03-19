/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.completion
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.StaticallyImportable
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author peter
 */
public class GroovySmartCompletionTest extends GroovyCompletionTestBase {
  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}groovy/completion/smart";
  }

  public void testSmartCompletionAfterNewInDeclaration() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    assertOrderedEquals(myFixture.lookupElementStrings, "Bar", "Foo");
  }

  public void testCaretAfterSmartCompletionAfterNewInDeclaration() throws Throwable { doSmartTest(); }

  public void testSmartCompletionAfterNewInDeclarationWithArray() throws Throwable { doSmartTest(); }

  public void testSmartCompletionAfterNewInDeclarationWithIntArray() throws Throwable { doSmartTest(); }

  public void testShortenNamesInSmartCompletionAfterNewInDeclaration() throws Throwable { doSmartTest(); }

  public void testSmartAfterNewInCall() throws Throwable { doSmartTest(); }

  public void testInnerClassInStaticMethodCompletion() throws Throwable { doVariantableTest(null, "", CompletionType.SMART, CompletionResult.notContain, 'Inner'); }

  public void testSmartCompletionInAssignmentExpression() throws Throwable { doSmartTest(); }

  public void testSimpleMethodParameter() throws Throwable {
    doSmartCompletion("d1", "d2");
  }

  public void testReturnStatement() throws Exception {
    doSmartCompletion("b", "b1", "b2", "foo");
  }

  public void testIncSmartCompletion() throws Exception {
    doSmartCompletion("a", "b");
  }

  public void testInheritConstructorsAnnotation() throws Throwable {
    myFixture.addFileToProject("groovy/transform/InheritConstructors.java", "package groovy.transform;\n" +
                                                                            "\n" +
                                                                            "import java.lang.annotation.ElementType;\n" +
                                                                            "import java.lang.annotation.Retention;\n" +
                                                                            "import java.lang.annotation.RetentionPolicy;\n" +
                                                                            "import java.lang.annotation.Target;@Retention(RetentionPolicy.SOURCE)\n" +
                                                                            "@Target({ElementType.TYPE})\n" +
                                                                            "public @interface InheritConstructors {\n" +
                                                                            "}");
    doSmartTest();
  }

  public void testSmartCastCompletion() {doSmartTest();}
  public void testSmartCastCompletionWithoutRParenth() {doSmartTest();}
  public void testSmartCastCompletionWithRParenth() {doSmartTest();}

  public void testDontCompletePrivateMembers() {doSmartCompletion "foo1", "foo2", "getFoo1", "getFoo2"}

  public void testEnumMembersInAssignment() {doSmartCompletion "IN_STOCK", "NOWHERE", "ORDERED", "valueOf" }
  public void testEnumMembersInAssignmentInsideEnum() {doSmartCompletion "IN_STOCK", "NOWHERE", "ORDERED", "next", "previous", "valueOf" }

  public void testPreferVarargElement() {
    doCompletionTest(null, null, '\n', CompletionType.SMART)
  }

  public void testGlobalStaticMembers() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART, 2);
    assertOrderedEquals(myFixture.lookupElementStrings, 'SUBSTRING', 'createExpected', 'createSubGeneric', 'SUB_RAW')
  }

  public void testGlobalStaticMembersForString() {
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

  public void testGlobalListCreators() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART, 2);
    assertOrderedEquals(myFixture.lookupElementStrings, 'createGenericList', 'createStringList')
  }

  void testNativeList() {doSmartCompletion('a1', 'a2')};

  public void testMembersImportStatically() {
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

  public void testThrow() {
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
