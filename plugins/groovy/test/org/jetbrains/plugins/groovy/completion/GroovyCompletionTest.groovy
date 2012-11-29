/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.PsiTypeLookupItem
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Maxim.Medvedev
 */
public class GroovyCompletionTest extends GroovyCompletionTestBase {
  final String basePath = TestUtils.testDataPath + "groovy/completion/";

  @Override
  protected void setUp() {
    super.setUp()
    CamelHumpMatcher.forceStartMatching(testRootDisposable);
  }

  @Override
  protected void tearDown() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    super.tearDown()
  }

  public void testFinishMethodWithLParen() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "getBar", "getClass", "getFoo");
    myFixture.type('(');
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testNamedParametersForApplication() {
    doVariantableTest("abx", "aby");
  }

  public void testNamedParametersForMethodCall() {
    doVariantableTest("abx", "aby");
  }

  public void testNamedParameters1() {
    doVariantableTest("abx", "aby");
  }

  public void testNamedParameters2() {
    doVariantableTest("abx", "aby");
  }

  public void testNamedParametersInMap1() {
    doVariantableTest("abx", "aby");
  }

  public void testNamedParametersInMap2() {
    doVariantableTest("abx", "aby");
  }

  public void testNamedParametersInSecondMap1() {
    doVariantableTest();
  }

  public void testNamedParametersInSecondMap2() {
    doVariantableTest();
  }

  public void testNamedParametersExcludeExisted() {
    doVariantableTest("abx", "aby");
  }

  public void testNamedParametersExcludeExisted2() {
    doVariantableTest("abx", "aby", "abz");
  }

  public void testNamedParametersExcludeExistedMap() {
    doVariantableTest("abx", "aby");
  }

  public void testNamedParametersForNotMap() {
    doBasicTest();
  }

  public void testNamedParametersForConstructorCall() {
    doVariantableTest("hahaha", "hohoho", "hashCode");
  }

  public void testUnfinishedMethodTypeParameter() {
    doVariantableTest("MyParameter", "MySecondParameter");
  }

  public void testUnfinishedMethodTypeParameter2() {
    doVariantableTest("MyParameter", "MySecondParameter");
  }

  public void testInstanceofHelpsDetermineType() {
    doBasicTest();
  }

  public void testInstanceofHelpsDetermineTypeInBinaryAnd() { doBasicTest() }
  public void testInstanceofHelpsDetermineTypeInBinaryOr() { doBasicTest() }

  public void testNotInstanceofDoesntHelpDetermineType() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
  }

  public void testNotInstanceofDoesntHelpDetermineType2() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
  }

  public void testTypeParameterCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "put", "putAll");
  }

  public void testCatchClauseParameter() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "getCause", "getClass");
  }

  public void testFieldSuggestedOnce1() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
    assertNull(myFixture.lookupElements);
  }

  public void testFieldSuggestedOnce2() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
    assertNull(myFixture.lookupElements);
  }

  public void testFieldSuggestedOnce3() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
    assertNull(myFixture.lookupElements);
  }

  public void testFieldSuggestedOnce4() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
    assertNull(myFixture.lookupElements);
  }

  public void testFieldSuggestedOnce5() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
    assertNull(myFixture.lookupElements);
  }

  public void testFieldSuggestedInMethodCall() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy");
  }

  public void testMethodParameterNoSpace() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy");
  }

  public void testGroovyDocParameter() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "xx", "xy");
  }

  public void testInnerClassExtendsImplementsCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "extends", "implements");
  }

  public void testInnerClassCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "Inner1", "Inner2");
  }

  public void testQualifiedThisCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "foo1", "foo2");
  }

  public void testQualifiedSuperCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "foo1", "foo2");
  }

  public void testThisKeywordCompletionAfterClassName1() {
    doBasicTest();
  }

  public void testThisKeywordCompletionAfterClassName2() {
    doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.notContain, "this");
  }

  public void testWhileInstanceof() { doBasicTest() }

  public void testCompletionInParameterListInClosableBlock() { doBasicTest(); }
  public void testCompletionInParameterListInClosableBlock3() { doBasicTest(); }

  public void testCompletionInParameterListInClosableBlock2() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "aDouble");
  }

  public void testStaticMemberFromInstanceContext() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "var1", "var2");
  }

  public void testInstanceMemberFromStaticContext() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "var3", "var4");
  }

  public void testTypeCompletionInVariableDeclaration1() {
    doBasicTest();
  }

  public void testTypeCompletionInVariableDeclaration2() {
    doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.notContain, "ArrayList");
  }

  public void testTypeCompletionInParameter() {
    doBasicTest();
  }

  public void testGStringConcatenationCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "substring", "substring", "subSequence");
  }

  public void testPropertyWithSecondUpperLetter() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "geteMail", "getePost");
  }

  public void testInferredVariableType() {
    myFixture.configureByText "a.groovy", "def foo = 'xxx'; fo<caret>"
    def presentation = new LookupElementPresentation()
    myFixture.completeBasic()[0].renderElement(presentation)
    assert presentation.itemText == 'foo'
    assert presentation.typeText == 'String'
  }

  public void testSubstitutedMethodType() {
    myFixture.configureByText "a.groovy", "new HashMap<String, Integer>().put<caret>x"
    def presentation = new LookupElementPresentation()
    myFixture.completeBasic()[0].renderElement(presentation)
    assert presentation.itemText == 'put'
    assert presentation.tailText == '(String key, Integer value)'
    assert presentation.typeText == 'Integer'
  }

  public void testIntCompletionInPlusMethod() {doBasicTest();}
  public void testIntCompletionInGenericParameter() {doBasicTest();}

  public void testWhenSiblingIsStaticallyImported_Method() {
    myFixture.addFileToProject "foo/Foo.groovy", """package foo
      class Foo {
        static def abcMethod() {}
        static def defMethod() {}
      }
    """

    myFixture.configureByText("a.groovy", """
      import static foo.Foo.abcMethod

      abcMethod()
      defM<caret>
    """)
    myFixture.completeBasic()
    myFixture.checkResult """
      import static foo.Foo.abcMethod
      import static foo.Foo.defMethod

      abcMethod()
      defMethod()<caret>
    """
  }

  public void testWhenSiblingIsStaticallyImported_Field() {
    myFixture.addFileToProject "foo/Foo.groovy", """package foo
      class Foo {
        static def abcField = 4
        static def defField = 2
      }
    """

    myFixture.configureByText("a.groovy", """
      import static foo.Foo.abcField

      println abcField
      defF<caret>
    """)
    myFixture.completeBasic()
    myFixture.checkResult """
      import static foo.Foo.abcField
      import static foo.Foo.defField

      println abcField
      defField<caret>
    """
  }

  public void testCompletionNamedArgumentWithoutSpace() {
    def settings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(GroovyCodeStyleSettings.class)
    settings.SPACE_IN_NAMED_ARGUMENT = false

    try {
      myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
  class A {
   public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
   { m(arg11<caret>) }
  }
  """)
      myFixture.completeBasic()
      myFixture.checkResult """
  class A {
   public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
   { m(arg111:<caret>) }
  }
  """
    }
    finally {
      settings.SPACE_IN_NAMED_ARGUMENT = true
    }
  }

  public void testCompletionNamedArgument1() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m(arg111: 1, arg<caret>: 2,   arg333: 3) }
}
""")
    myFixture.completeBasic()

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m(arg111: 1, arg222: <caret>2,   arg333: 3) }
}
"""
  }

  public void testCompletionNamedArgument2() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m arg111: 1, arg<caret>: 2,   arg333: 3 }
}
""")
    myFixture.completeBasic()

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m arg111: 1, arg222: <caret>2,   arg333: 3 }
}
"""
  }

  public void testCompletionNamedArgument3() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m arg1<caret> }
}
""")
    myFixture.completeBasic()

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m arg111: <caret> }
}
"""
  }

  public void testCompletionNamedArgument4() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m (arg1<caret> zzz) }
}
""")
    myFixture.completeBasic()

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m (arg111: <caret> zzz) }
}
"""
  }

  public void testCompletionNamedArgument5() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m (arg1<caret>, {
      out << "asdasdas"
 } ) }
}
""")
    myFixture.completeBasic()

    myFixture.checkResult("""
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m (arg111: <caret>, {
      out << "asdasdas"
 } ) }
}
""")
  }

  public void testCompletionNamedArgument6() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m([arg1<caret>])}
}
""")
    myFixture.completeBasic()

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m([arg111: <caret>])}
}
"""
  }

  public void testCompletionNamedArgument7() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m(arg1<caret>)}
}
""")
    myFixture.completeBasic()

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m(arg111: <caret>)}
}
"""
  }

  public void testCompletionNamedArgument8() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m(arg1<caret>,)}
}
""")
    myFixture.completeBasic()

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m(arg111: <caret>,)}
}
"""
  }

  public void testCompletionNamedArgumentWithDD() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m(arg<caret>)}
}
""")
    myFixture.completeBasic()
    myFixture.type(':')

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m(arg111: <caret>)}
}
"""
  }

  public void testCompletionNamedArgumentReplace() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m(arg<caret>222: 'a')}
}
""")
    myFixture.completeBasic()
    myFixture.type("1\t")

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m(arg111: 'a')}
}
"""
  }

  public void testCompletionNamedArgumentWithSpace() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m(arg<caret>)}
}
""")
    myFixture.completeBasic()
    myFixture.type(' ')

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 { m(arg111: <caret>)}
}
"""
  }

  public void testCompletionNamedArgumentWithNewLine1() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 {
   m(arg<caret>
     arg222: 222,
   )
 }
}
""")
    myFixture.completeBasic()
    myFixture.type(' ')

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 {
   m(arg111: <caret>
     arg222: 222,
   )
 }
}
"""
  }

  public void "test finish method call with space in field initializer"() {
    checkCompletion 'class Foo { boolean b = eq<caret>x }', ' ', 'class Foo { boolean b = equals <caret>x }'
  }

  public void testCompletionNamedArgumentWithNewLine2() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 {
   m(arg<caret>,
     arg222: 222,
   )
 }
}
""")
    myFixture.completeBasic()
    myFixture.type(':')

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 {
   m(arg111: <caret>,
     arg222: 222,
   )
 }
}
"""
  }

  public void testCompletionNamedArgumentWithNewLine4() {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 {
   m(arg<caret>
     , arg222: 222,
     arg333: 333,
   )
 }
}
""")
    myFixture.completeBasic()

    myFixture.checkResult """
class A {
 public int m(arg) { return arg.arg111 + arg.arg222 + arg.arg333; }
 {
   m(arg111: <caret>
     , arg222: 222,
     arg333: 333,
   )
 }
}
"""
  }

  public void testSpreadOperator() {
    doVariantableTest("foo1", "foo2")
  }

  public void testGrvy945() {
    def file = myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """class MyCategory {
  static def plus(MyCategory t, MyCat<caret>) {
  }
}""")
    LookupElement[] lookupElements = myFixture.completeBasic()
    assertNull(lookupElements)

    assertEquals """class MyCategory {
  static def plus(MyCategory t, MyCategory) {
  }
}""", file.text
  }

  void configure(String text) {
    myFixture.configureByText("a.groovy", text)
  }

  public void testGenericsAfterNew() {
    configure "List<String> l = new ArrLi<caret>"
    myFixture.completeBasic()
    myFixture.type '\n'
    myFixture.checkResult "List<String> l = new ArrayList<>(<caret>)"
  }

  public void testAfterNewWithInner() {
    myFixture.addClass """class Zzoo {
        static class Impl {}
      }"""
    configure "Zzoo l = new Zz<caret>"
    myFixture.completeBasic()
    myFixture.type '\n'
    myFixture.checkResult "Zzoo l = new Zzoo()<caret>"
  }

  public void testNothingAfterIntegerLiteral() {
    configure "2f<caret>"
    assertEmpty myFixture.completeBasic()
  }

  public void testPackagedContainingClassNameAfterStatic() {
    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("foo/cls.groovy", """
    package foo
    class Zzzzzzz {
      static Zzz<caret>
    }
    """).virtualFile)
    myFixture.completeBasic()
    assert myFixture.editor.document.text.contains("static Zzzzzzz")
  }

  public void testDontCompleteSubpackageOfImplicitlyImported() {
    myFixture.addFileToProject "A.groovy", """
in<caret>"""
    myFixture.testCompletionVariants "A.groovy", "int", "interface" //don't complete 'instrument' from 'java.lang'
  }


  public void testEatingThisReference() {
    configure "def x = []; x.<caret> this"
    myFixture.completeBasic()
    myFixture.type 'ad\t'
    myFixture.checkResult "def x = []; x.add(<caret>) this"
  }

  public void testEatingClosingParenthesis() {
    checkCompletion """
def xxxx = []
def xxxy = []
foo((xx<caret>):2)
""", '\t', """
def xxxx = []
def xxxy = []
foo((xxxx<caret>):2)
"""
  }

  public void testDontAddStaticImportSecondTime() {
    configure """import static java.lang.String.format
form<caret>"""

    myFixture.completeBasic()
    myFixture.checkResult """import static java.lang.String.format
format(<caret>)"""
  }
  
  public void testImportAsterisk() {
    myFixture.configureByText "a.groovy", "import java.lang.<caret>"
    myFixture.completeBasic()
    myFixture.type '*\n'
    myFixture.checkResult "import java.lang.*\n<caret>"
  }

  public void testNoDotsInImport() {
    myFixture.configureByText "a.groovy", "import java.<caret>"
    myFixture.completeBasic()
    myFixture.type 'lan\n'
    myFixture.checkResult "import java.lang<caret>"
  }

  public void testInvalidScriptClass() {
    myFixture.addFileToProject("b.groovy", "//comment")
    myFixture.configureByText "a.groovy", "def b<caret>"
    myFixture.completeBasic()
    myFixture.checkResult "def b<caret>"
  }

  public void testSpacesAroundEq() {
    myFixture.configureByText "a.groovy", "int xxx, xxy; xx<caret>"
    myFixture.completeBasic()
    myFixture.type '='
    myFixture.checkResult "int xxx, xxy; xxx = <caret>"
  }

  public void testOnlyAnnotationsAfterAt() {
    myFixture.addClass "class AbcdClass {}; @interface AbcdXAnno {}"
    myFixture.configureByText "a.groovy", "@Abcd<caret> class A {}"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings[0] == 'AbcdXAnno'
  }

  public void testOnlyAnnotationsAfterAtInMethodParameters() {
    myFixture.addClass "class AbcdClass {}; @interface AbcdAnno {}"
    myFixture.configureByText "a.groovy", "def foo(@Abcd<caret> ) {}"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings[0] == 'AbcdAnno'
  }

  public void testNoCompletionInClassBodyComments() {
    myFixture.configureByText "a.groovy", "class Foo { /* protec<caret> */ }"
    assertEmpty(myFixture.completeBasic())
  }

  public void testNoCompletionInCodeBlockComments() {
    myFixture.configureByText "a.groovy", "def Foo() { /* whil<caret> */ }"
    assertEmpty(myFixture.completeBasic())
  }

  public void testParenthesesForExpectedClassTypeRegardlessInners() {
    myFixture.addClass "class Fooooo { interface Bar {} }"
    myFixture.configureByText "a.groovy", "Fooooo f = new Foo<caret>"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['Fooooo', 'Fooooo.Bar']
    myFixture.type '\n'
    myFixture.checkResult "Fooooo f = new Fooooo()<caret>"
  }

  public void testParenthesesForUnexpectedClassTypeRegardingInners() {
    myFixture.addClass "class Fooooo { interface Bar {} }"
    myFixture.configureByText "a.groovy", "Fooooo.Bar f = new Foo<caret>"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['Fooooo', 'Fooooo.Bar']
    myFixture.type '\n'
    myFixture.checkResult "Fooooo.Bar f = new Fooooo()<caret>"
  }

  public void testOnlyExceptionsInCatch() {
    myFixture.addClass "package foo; public class AbcdClass {}; public class AbcdException extends Throwable {}"
    myFixture.configureByText "a.groovy", "try {} catch (Abcd<caret>"
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult """import foo.AbcdException

try {} catch (AbcdException"""
  }

  public void testOnlyExceptionsInCatch2() {
    myFixture.addClass "class AbcdClass {}; class AbcdException extends Throwable {}"
    myFixture.configureByText "a.groovy", "try {} catch (Abcd<caret> e) {}"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings[0] == 'AbcdException'
    myFixture.type('\n')
    myFixture.checkResult "try {} catch (AbcdException<caret> e) {}"
  }

  public void testTopLevelClassesFromPackaged() {
    myFixture.addClass "public class Fooooo {}"
    final text = "package foo; class Bar { Fooo<caret> }"
    def file = myFixture.addFileToProject("foo/Bar.groovy", text)
    myFixture.configureFromExistingVirtualFile file.virtualFile
    assertEmpty myFixture.completeBasic()
    myFixture.checkResult text
  }

  public void testLocalVarOverlaysField() {
    myFixture.configureByText "a.groovy", """
class A {
  def myVar = 2

  def foo() {
    def myVar = 3
    print myVa<caret>
  }
}"""
    myFixture.completeBasic()

    myFixture.checkResult """
class A {
  def myVar = 2

  def foo() {
    def myVar = 3
    print myVar<caret>
  }
}"""

  }

  public void testParenthesesInMethodCompletion() {
    myFixture.configureByText "a.groovy", """
def foo(def a) {2}
return fo<caret>"""
    myFixture.completeBasic()
    myFixture.checkResult """
def foo(def a) {2}
return foo()"""
  }

  public void testFinishClassNameWithSquareBracket() {
    myFixture.addClass("class AbcdClass {}; class AbcdeClass {}")
    checkCompletion("Abcd<caret>", '[', "AbcdClass[<caret>]")
  }

  public void testFinishClassNameWithSquareBracketAfterNew() {
    myFixture.addClass("class AbcdClass {}; class AbcdeClass {}")
    checkCompletion("new Abcd<caret>", '[', "new AbcdClass[<caret>]")
  }

  public void testFinishMethodNameWithSquareBracket() {
    myFixture.addClass("""class AbcdClass {
      static int[] foo(int x){}
      static int[] fobar(){}
    }""")
    checkCompletion("AbcdClass.fo<caret>", '[', "AbcdClass.fobar()[<caret>]")
  }

  public void testFinishVariableNameWithSquareBracket() {
    checkCompletion("int[] fooo, foobar; foo<caret>", '[', "int[] fooo, foobar; foobar[<caret>]")
  }

  public void testFinishClassNameWithLt() {
    myFixture.addClass("class AbcdClass {}; class AbcdeClass {}")
    checkCompletion("Abcd<caret>", '<', "AbcdClass<<caret>>")
  }

  public void testFinishClassNameWithLtAfterNew() {
    myFixture.addClass("class AbcdClass<T> {}; class AbcdeClass {}")
    checkCompletion("new Abcd<caret>", '<', "new AbcdClass<<caret>>()")
  }

  public void testMapKeysUsedInFile() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    doVariantableTest 'foo1', 'foo3', 'foo4', 'Foo5', 'Foo7'
  }

  public void testNoClassesAsMapKeys() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    doVariantableTest()
  }

  public void testNamedArgsUsedInFile() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    doVariantableTest 'false', 'foo2', 'float', 'foo1', 'foo3', 'foo4', 'foo5'
  }

  public void testSuggestMembersOfExpectedType() {
    myFixture.addClass("enum Foo { aaaaaaaaaaaaaaaaaaaaaa, bbbbbb }")
    checkCompletion("Foo f = aaaaaaaa<caret>", '\n', "Foo f = Foo.aaaaaaaaaaaaaaaaaaaaaa<caret>")
  }

  public void testFieldTypeAfterModifier() {
    myFixture.addClass("package bar; public class Fooooooooooo { }")
    checkCompletion '''
class A {
  private Foooo<caret>
}''', '\n', '''import bar.Fooooooooooo

class A {
  private Fooooooooooo<caret>
}'''
  }

  public void testSuperClassProperty() {
    checkSingleItemCompletion """
class A { def foooooooooooo }

class B extends A {
  def bar() {
    foooo<caret>
  }
}
""", """
class A { def foooooooooooo }

class B extends A {
  def bar() {
    foooooooooooo<caret>
  }
}
"""
  }

  public void testDoubleSpace() {
    checkCompletion "asse<caret>x", ' ', 'assert <caret>x'
  }

  public void testPreferInstanceof() {
    caseSensitiveNone()

    configure '''
class Fopppp {
    def foo() {
        assert x ins<caret>
    }
}
class Instantiation {}
'''
    myFixture.completeBasic()
    assertEquals 'instanceof', myFixture.lookupElementStrings[0]
  }

  public void testForFinal() {
    assert doContainsTest('final', '''
class Fopppp {
    def foo() {
        for(fin<caret>
    }
}
''')
  }

  public void testExcludeStringBuffer() {
    assert doContainsTest('StringBuffer', 'StringBuff<caret>f')
    CodeInsightSettings.instance.EXCLUDED_PACKAGES = [StringBuffer.name] as String[]
    try {
      assert !doContainsTest('StringBuffer', 'StringBuff<caret>f')
    }
    finally {
      CodeInsightSettings.instance.EXCLUDED_PACKAGES = new String[0]
    }
  }

  private doContainsTest(String itemToCheck, String text) {
    myFixture.configureByText "a.groovy", text

    final LookupElement[] completion = myFixture.completeBasic()
    return completion.find {println it.lookupString;itemToCheck == it.lookupString}
  }

  public void testWordCompletionInLiterals() {
    checkSingleItemCompletion('def foo = "fo<caret>"', 'def foo = "foo<caret>"')
  }
  public void testWordCompletionInLiterals2() {
    checkSingleItemCompletion('''
println "abcd"
"a<caret>"
''', '''
println "abcd"
"abcd<caret>"
''')
  }

  public void testWordCompletionInComments() {
    checkSingleItemCompletion('''
println "abcd"
// a<caret>"
''', '''
println "abcd"
// abcd<caret>"
''')
  }

  public void testNoModifiersAfterDef() {
    doVariantableTest('def priv<caret>', '', CompletionType.BASIC, CompletionResult.notContain, 'private')
  }

  public void testIfSpace() { checkCompletion 'int iff; if<caret>', ' ', "int iff; if <caret>" }

  public void testIfParenthesis() { checkCompletion 'int iff; if<caret>', '(', "int iff; if (<caret>)" }

  public void testMakingDefFromAssignment() { checkCompletion 'int defInt; de<caret>foo = 2', 'f ', "int defInt; def <caret>foo = 2" }

  public void testEnumPropertyType() {
    checkSingleItemCompletion 'enum Foo {a,b; static List<StringBui<caret>>', "enum Foo {a,b; static List<StringBuilder<caret>>"
  }

  public void testEnumPropertyType2() {
    checkSingleItemCompletion 'enum Foo {a,b; static List<StringBui<caret>', "enum Foo {a,b; static List<StringBuilder<caret>"
  }

  public void testShowAccessor() {
    assertNotNull doContainsTest("getFoo", """
class MyClass {
  def foo
}

def a = new MyClass()
a.g<caret>
""")
  }

  public void testContinue() {
    assertNotNull doContainsTest("continue", """
def conti = 4
while(true) {
  if (tst) cont<caret>
}""")

  }

  public void testPreferParametersToClasses() {
    caseSensitiveNone()

    myFixture.configureByText "a.groovy", "def foo(stryng) { println str<caret> }"
    myFixture.completeBasic()
    assertEquals 'stryng', myFixture.lookupElementStrings[0]
  }

  public void testFieldVsPackage() {
    myFixture.addFileToProject 'aaa/bbb/Foo.groovy', 'package aaa.bbb; class Foo{}'
    def file = myFixture.addFileToProject('aaa/bar.groovy', '''
package aaa

import aaa.*

class X {
  def bbb = 'text'

  def foo() {
    bbb.<caret>toString()
  }
}
''')
    myFixture.configureFromExistingVirtualFile file.virtualFile
    myFixture.completeBasic()
    assertFalse(myFixture.lookupElementStrings.contains('Foo'))
  }

  public void testFieldVsPackage2() {
    myFixture.addFileToProject 'aaa/bbb/Foo.groovy', 'package aaa.bbb; class Foo{}'
    def file = myFixture.addFileToProject('aaa/bar.groovy', '''
package aaa

import aaa.*

class X {
  def bbb = 'text'

  def foo() {
    bbb.<caret>
  }
}
''')
    myFixture.configureFromExistingVirtualFile file.virtualFile
    myFixture.completeBasic()
    assertFalse(myFixture.lookupElementStrings.contains('Foo'))
  }

  public void testClassNameBeforeParentheses(){
    doBasicTest()
  }

  public void testNewClassGenerics() {
    checkSingleItemCompletion 'new ArrayLi<caret>', 'new ArrayList<<caret>>()'
  }

  public void testInnerClassStart() {
    checkSingleItemCompletion 'class Foo { cl<caret> }', 'class Foo { class <caret> }'
  }

  public void testPropertyBeforeAccessor() {
    doVariantableTest 'soSe', 'setSoSe'
  }

  public void testSortOrder0() {
    doVariantableTest 'se', 'setProperty', 'setMetaClass', 'setSe'
  }

  public void testPrimitiveCastOverwrite() {
    checkCompletion 'byte v1 = (by<caret>te) 0', '\t', 'byte v1 = (byte<caret>) 0'
  }

  public void testInitializerMatters() {
    myFixture.configureByText("a.groovy", "class Foo {{ String f<caret>x = getFoo(); }; String getFoo() {}; }");
    myFixture.completeBasic()
    assertOrderedEquals(myFixture.lookupElementStrings, ["foo"])
  }

  public void testFieldInitializerMatters() {
    myFixture.configureByText("a.groovy", "class Foo { String f<caret>x = getFoo(); String getFoo() {}; }");
    myFixture.completeBasic()
    assertOrderedEquals(myFixture.lookupElementStrings, ["foo"])
  }

  public void testAccessStaticViaInstanceSecond() {
    myFixture.configureByText("a.groovy", """
public class KeyVO {
  { this.fo<caret>x }
  static void foo() {}
}
""");
    myFixture.complete(CompletionType.BASIC, 1)
    assert !myFixture.lookupElementStrings
    myFixture.complete(CompletionType.BASIC, 2)
    assertOrderedEquals(myFixture.lookupElementStrings, ["foo"])
  }

  public void testNoRepeatingModifiers() {
    myFixture.configureByText 'a.groovy', 'class A { public static <caret> }'
    myFixture.completeBasic()
    assert !('public' in myFixture.lookupElementStrings)
    assert !('static' in myFixture.lookupElementStrings)
    assert 'final' in myFixture.lookupElementStrings
  }

  public void testSpaceTail() {
    checkCompletion 'class A <caret> ArrayList {}', ' ', 'class A extends <caret> ArrayList {}'
    checkCompletion 'class A <caret> ArrayList {}', '\n', 'class A extends<caret> ArrayList {}'
    checkSingleItemCompletion 'class Foo impl<caret> {}', 'class Foo implements <caret> {}'
  }

  public void testAmbiguousClassQualifier() {
    myFixture.addClass("package foo; public class Util { public static void foo() {} }")
    myFixture.addClass("package bar; public class Util { public static void bar() {} }")
    myFixture.configureByText 'a.groovy', 'Util.<caret>'
    myFixture.completeBasic()
    assertOrderedEquals myFixture.lookupElementStrings[0..1] , ['Util.bar', 'Util.foo']

    def presentation = LookupElementPresentation.renderElement(myFixture.lookupElements[0])
    assertEquals 'Util.bar', presentation.itemText
    assertEquals '() (bar)', presentation.tailText
    assert !presentation.tailGrayed

    myFixture.type 'f\n'
    myFixture.checkResult '''import foo.Util

Util.foo()<caret>'''
  }

  public void testUseDescendantStaticImport() { doBasicTest() }

  public void testPreferInterfacesInImplements() {
    myFixture.addClass('interface FooIntf {}')
    myFixture.addClass('class FooClass {}')
    doVariantableTest('FooIntf', 'FooClass')
  }

  public void testPropertyChain() { doBasicTest() }

  public void testMethodPointer() {
    doBasicTest('''\
class Base {
  def prefixMethod(){}
  def prefixField
}

new Base().&prefix<caret>''', '''\
class Base {
  def prefixMethod(){}
  def prefixField
}

new Base().&prefixMethod<caret>''')
  }

  public void testFieldPointer() {
    doBasicTest '''\
class Base {
  def prefixMethod(){}
  def prefixField
}

new Base().@prefix<caret>''', '''\
class Base {
  def prefixMethod(){}
  def prefixField
}

new Base().@prefixField<caret>'''
  }

  public void testPrivateFieldOnSecondInvocation() {
    myFixture.configureByText('_a.groovy', '''\
class Base {
  private int field1
}

new Base().fie<caret>x''')
    myFixture.complete(CompletionType.BASIC, 2)
    assert myFixture.lookupElementStrings == ['field1']
  }

  public void testForIn() {
    assert doContainsTest('in', 'for (int i i<caret>')
    assert doContainsTest('in', 'for (i i<caret>')
  }

  public void testReturnInVoidMethod() {
    doBasicTest('''\
void foo() {
  retur<caret>
}
''', '''\
void foo() {
  return<caret>
}
''')
  }

  public void testReturnInNotVoidMethod() {
    doBasicTest('''\
String foo() {
  retur<caret>
}
''', '''\
String foo() {
  return <caret>
}
''')
  }

  void testInferArgumentTypeFromMethod1() {
    doBasicTest('''\
def bar(String s) {}

def foo(Integer a) {
    bar(a)
    a.subSequen<caret>()
}
''', '''\
def bar(String s) {}

def foo(Integer a) {
    bar(a)
    a.subSequence(<caret>)
}
''')
  }

  void testInferArgumentTypeFromMethod2() {
    doBasicTest('''\
def bar(String s) {}

def foo(Integer a) {
  while(true) {
    bar(a)
    a.subSequen<caret>()
  }
}
''', '''\
def bar(String s) {}

def foo(Integer a) {
  while(true) {
    bar(a)
    a.subSequence(<caret>)
  }
}
''')
  }

  public void testForSpace() {
    configure('f<caret>')
    myFixture.completeBasic()
    myFixture.type 'or '
    myFixture.checkResult "for <caret>"
  }

  void testInferArgumentTypeFromMethod3() {
    doBasicTest('''\
def bar(String s) {}

def foo(Integer a) {
    bar(a)
    print a
    a.subSequen<caret>()
}
''', '''\
def bar(String s) {}

def foo(Integer a) {
    bar(a)
    print a
    a.subSequence(<caret>)
}
''')
  }

  void testInferArgumentTypeFromMethod4() {
    doBasicTest('''\
def bar(String s) {}

def foo(Integer a) {
  while(true) {
    bar(a)
    print a
    a.subSequen<caret>()
  }
}
''', '''\
def bar(String s) {}

def foo(Integer a) {
  while(true) {
    bar(a)
    print a
    a.subSequence(<caret>)
  }
}
''')
  }

  void testSuperExtendsInTypeParams() {
    myFixture.configureByText("_.groovy", '''\
def foo(List<? <caret>)''');
    myFixture.complete(CompletionType.BASIC);
    assertOrderedEquals(myFixture.lookupElementStrings, "extends", "super");
  }

  void testSuperExtendsInTypeParams2() {
    myFixture.configureByText("_.groovy", '''\
def foo(List<? <caret>> list)''');
    myFixture.complete(CompletionType.BASIC);
    assertOrderedEquals(myFixture.lookupElementStrings, "extends", "super");
  }

  void testMapDontCompleteProperties() {
    myFixture.configureByText("_.groovy", '''\
def map = [1:2]
print map.metc<caret>
''');
    myFixture.complete(CompletionType.BASIC);
    assertEmpty myFixture.lookupElements
  }

  void testAnnotationCompletion0() {
    myFixture.configureByText('_.groovy', '''\
@interface A {
  String fooo()
}

@A(foo<caret>)
def bar(){}''')
    myFixture.complete(CompletionType.BASIC)
    myFixture.checkResult('''\
@interface A {
  String fooo()
}

@A(fooo = <caret>)
def bar(){}''')
  }

  void testAnnotationCompletion1() {
    myFixture.configureByText('_.groovy', '''\
@interface A {
  String fooo()
}

def abcde() {}

@A(abc<caret>)
def bar(){}''')
    myFixture.complete(CompletionType.BASIC)
    myFixture.checkResult('''\
@interface A {
  String fooo()
}

def abcde() {}

@A(abcde()<caret>)
def bar(){}''')
  }

  void testAnnotationCompletion2() {
    myFixture.configureByText('_.groovy', '''\
@interface A {
  String fooo()
  String fooo1()
}

@A(foo<caret> = 'a')
def bar(){}''')
    myFixture.complete(CompletionType.BASIC)
    assertOrderedEquals(myFixture.lookupElementStrings, ['fooo', 'fooo1'])
  }

  public void testPreferApplicableAnnotations() {
    myFixture.addClass '''
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE})
@interface TMetaAnno {}

@Target({ElementType.LOCAL_VARIABLE})
@interface TLocalAnno {}'''

    configure('@T<caret> @interface Foo {}')
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems  0, 'TMetaAnno', 'Target', 'TreeSelectionMode', 'TLocalAnno'
  }

  void testDiamondCompletion1() {
    doSmartTest('''\
interface Base<T>{}

class Inh<T> implements Base<T>{}

Base<String> b = new In<caret>
''', '''\
interface Base<T>{}

class Inh<T> implements Base<T>{}

Base<String> b = new Inh<>()<caret>
''')
  }

  void testDiamondCompletion2() {
    doCompletionTest('''\
interface Base<T>{}

class Inh<T> implements Base<T>{}

def foo(Base<String> b){}

foo(new In<caret>)
''', '''\
interface Base<T>{}

class Inh<T> implements Base<T>{}

def foo(Base<String> b){}

foo(new Inh<String>()<caret>)
''', CompletionType.SMART)
  }

  void testPropertiesOfBaseClass() {
    myFixture.addFileToProject('Base.groovy', '''\
class Base {
  protected String foooo = 'field'

  public String getFoooo() {'getter'}
}
''')
    doBasicTest('''\
class Inheritor extends Base {
  def test() {
    assert fooo<caret> == 'getter'
  }
}
''', '''\
class Inheritor extends Base {
  def test() {
    assert foooo<caret> == 'getter'
  }
}
''')
  }

  void testDiamondCompletionInAssignmentCompletion() {
    doCompletionTest('''\
class Foo<T> {}

Foo<String> var
var = new <caret>
''', '''\
class Foo<T> {}

Foo<String> var
var = new Foo<>()<caret>
''', CompletionType.SMART)
  }

  void testDiamondCompletionInAssignmentCompletion2() {
    myFixture.with {
      configureByText('_a.groovy', '''\
class Foo<T> {}

Foo<String> var
var = new <caret>Foo<String>()
''')
      complete(CompletionType.SMART)
      assertEquals(1, lookupElements.length)

      assertInstanceOf(lookupElements[0], PsiTypeLookupItem)
      assertTrue((lookupElements[0] as PsiTypeLookupItem).myDiamond)
    }
  }

  void testStaticallyImportedProperty1() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static def foo
}
''')
    doBasicTest('''\
import static Foo.foo

print getFo<caret>
''', '''\
import static Foo.foo

print getFoo()
''')
  }

  void testStaticallyImportedProperty2() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static def foo
}
''')
    doBasicTest('''\
import static Foo.foo

setFo<caret>
''', '''\
import static Foo.foo

setFoo(<caret>)
''')
  }

  void testStaticallyImportedProperty3() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static def foo
}
''')
    doBasicTest('''\
import static Foo.foo as barrr

print getBarr<caret>
''', '''\
import static Foo.foo as barrr

print getBarrr()
''')
  }

  void testStaticallyImportedProperty4() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static def foo
}
''')
    doBasicTest('''\
import static Foo.foo as barrr

setBarr<caret>
''', '''\
import static Foo.foo as barrr

setBarrr(<caret>)
''')
  }

  void testParenthesesAfterDot() {
    myFixture.testCompletionTyping(getTestName(false) + '.groovy', '\t', getTestName(false) + '_after.groovy')
  }

  void testNewExprDoesntCompleteDef() {
    doNoVariantsTest('def a = \new <caret>', 'def', 'final')
  }

  void testThisInScriptCompletion() {
    doVariantableTest('''\
def foo() {}
this.<caret>
''', "", CompletionType.BASIC, CompletionResult.contain, 'foo')
  }

  void testPrimitiveTypeTailTextInSafeCast() {
    doBasicTest('print(a as boolea<caret>)', 'print(a as boolean<caret>)')
  }
}