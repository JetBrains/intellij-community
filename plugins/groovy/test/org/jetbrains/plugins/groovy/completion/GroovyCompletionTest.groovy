/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.PsiTypeLookupItem
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Maxim.Medvedev
 */
class GroovyCompletionTest extends GroovyCompletionTestBase {
  final String basePath = TestUtils.testDataPath + "groovy/completion/"

  @Override
  protected void setUp() {
    super.setUp()
    CamelHumpMatcher.forceStartMatching(myFixture.testRootDisposable)
  }

  @Override
  protected void tearDown() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    super.tearDown()
  }

  void testFinishMethodWithLParen() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "getBar", "getClass", "getFoo")
    myFixture.type('(')
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }

  void testNamedParametersForApplication() {
    doVariantableTest("abx", "aby")
  }

  void testNamedParametersForMethodCall() {
    doVariantableTest("abx", "aby")
  }

  void testNamedParameters1() {
    doVariantableTest("abx", "aby")
  }

  void testNamedParameters2() {
    doVariantableTest("abx", "aby")
  }

  void testNamedParametersInMap1() {
    doVariantableTest("abx", "aby")
  }

  void testNamedParametersInMap2() {
    doVariantableTest("abx", "aby")
  }

  void testNamedParametersInSecondMap1() {
    doVariantableTest()
  }

  void testNamedParametersInSecondMap2() {
    doVariantableTest()
  }

  void testNamedParametersExcludeExisted() {
    doVariantableTest("abx", "aby")
  }

  void testNamedParametersExcludeExisted2() {
    doVariantableTest("abx", "aby", "abz")
  }

  void testNamedParametersExcludeExistedMap() {
    doVariantableTest("abx", "aby")
  }

  void testNamedParametersForNotMap() {
    doBasicTest()
  }

  void testNamedParametersForConstructorCall() {
    doVariantableTest("hahaha", "hohoho")
  }

  void testUnfinishedMethodTypeParameter() {
    doVariantableTest("MyParameter", "MySecondParameter")
  }

  void testUnfinishedMethodTypeParameter2() {
    doVariantableTest("MyParameter", "MySecondParameter")
  }

  void testInstanceofHelpsDetermineType() {
    doBasicTest()
  }

  void testInstanceofHelpsDetermineTypeInBinaryAnd() { doBasicTest() }

  void testInstanceofHelpsDetermineTypeInBinaryOr() { doBasicTest() }

  void testNotInstanceofDoesntHelpDetermineType() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy")
  }

  void testNotInstanceofDoesntHelpDetermineType2() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy")
  }

  void testTypeParameterCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "put", "putAll")
  }

  void testCompleteTypeParameter() {
    doVariantableTest('''\
class Foo<A, B> {
    public <C, D> void foo(<caret>)
}
''', '', CompletionType.BASIC, CompletionResult.contain, 'A', 'B', 'C', 'D')
  }

  void testCatchClauseParameter() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "getCause", "getClass")
  }

  void testFieldSuggestedOnce1() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy")
    assertNull(myFixture.lookupElements)
  }

  void testFieldSuggestedOnce2() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy")
    assertNull(myFixture.lookupElements)
  }

  void testFieldSuggestedOnce3() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy")
    assertNull(myFixture.lookupElements)
  }

  void testFieldSuggestedOnce4() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy")
    assertNull(myFixture.lookupElements)
  }

  void testFieldSuggestedOnce5() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy")
    assertNull(myFixture.lookupElements)
  }

  void testFieldSuggestedInMethodCall() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy")
  }

  void testMethodParameterNoSpace() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy")
  }

  void testGroovyDocParameter() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "xx", "xy")
  }

  void testInnerClassExtendsImplementsCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "extends", "implements")
  }

  void testInnerClassCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "Inner1", "Inner2")
  }

  void testQualifiedThisCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "foo1", "foo2")
  }

  void testQualifiedSuperCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "foo1", "foo2")
  }

  void testThisKeywordCompletionAfterClassName1() {
    doBasicTest()
  }

  void testThisKeywordCompletionAfterClassName2() {
    doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.notContain, "this")
  }

  void testWhileInstanceof() { doBasicTest() }

  void testCompletionInParameterListInClosableBlock() { doBasicTest() }

  void testCompletionInParameterListInClosableBlock3() { doBasicTest() }

  void testCompletionInParameterListInClosableBlock2() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "aDouble")
  }

  void testStaticMemberFromInstanceContext() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "var1", "var2")
  }

  void testInstanceMemberFromStaticContext() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "var3", "var4")
  }

  void testTypeCompletionInVariableDeclaration1() {
    doBasicTest()
  }

  void testTypeCompletionInVariableDeclaration2() {
    doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.notContain, "ArrayList")
  }

  void testTypeCompletionInParameter() {
    doBasicTest()
  }

  void testPropertyWithSecondUpperLetter() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "geteMail", "getePost")
  }

  void testInferredVariableType() {
    myFixture.configureByText "a.groovy", "def foo = 'xxx'; fo<caret>"
    def presentation = new LookupElementPresentation()
    myFixture.completeBasic()[0].renderElement(presentation)
    assert presentation.itemText == 'foo'
    assert presentation.typeText == 'String'
  }

  void testSubstitutedMethodType() {
    myFixture.configureByText "a.groovy", "new HashMap<String, Integer>().put<caret>x"
    def presentation = new LookupElementPresentation()
    myFixture.completeBasic()[0].renderElement(presentation)
    assert presentation.itemText == 'put'
    assert presentation.tailText == '(String key, Integer value)'
    assert presentation.typeText == 'Integer'
  }

  void testIntCompletionInPlusMethod() { doBasicTest() }

  void testIntCompletionInGenericParameter() { doBasicTest() }

  void testWhenSiblingIsStaticallyImported_Method() {
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

  void testWhenSiblingIsStaticallyImported_Field() {
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

  void testCompletionNamedArgumentWithoutSpace() {
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

  void testCompletionNamedArgument1() {
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

  void testCompletionNamedArgument2() {
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

  void testCompletionNamedArgument3() {
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

  void testCompletionNamedArgument4() {
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

  void testCompletionNamedArgument5() {
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

  void testCompletionNamedArgument6() {
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

  void testCompletionNamedArgument7() {
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

  void testCompletionNamedArgument8() {
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

  void testCompletionNamedArgumentWithDD() {
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

  void testCompletionNamedArgumentReplace() {
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

  void testCompletionNamedArgumentWithSpace() {
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

  void testCompletionNamedArgumentWithNewLine1() {
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

  void "test finish method call with space in field initializer"() {
    checkCompletion 'class Foo { boolean b = eq<caret>x }', ' ', 'class Foo { boolean b = equals <caret>x }'
  }

  void testCompletionNamedArgumentWithNewLine2() {
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

  void testCompletionNamedArgumentWithNewLine4() {
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

  void testSpreadOperator() {
    doVariantableTest("foo1", "foo2")
  }

  void testGrvy945() {
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

  void testGenericsAfterNew() {
    configure "List<String> l = new ArrLi<caret>"
    myFixture.completeBasic()
    myFixture.type '\n'
    myFixture.checkResult "List<String> l = new ArrayList<>(<caret>)"
  }

  void testFinishByClosingBracket() {
    doCompletionTest "int o1, o2; array[o<caret>", "int o1, o2; array[o1]<caret>", "]", CompletionType.BASIC
  }

  void testAfterNewWithInner() {
    myFixture.addClass """class Zzoo {
        static class Impl {}
      }"""
    configure "Zzoo l = new Zz<caret>"
    myFixture.completeBasic()
    myFixture.type '\n'
    myFixture.checkResult "Zzoo l = new Zzoo()<caret>"
  }

  void testNothingAfterIntegerLiteral() {
    configure "2f<caret>"
    assertEmpty myFixture.completeBasic()
  }

  void testPackagedContainingClassNameAfterStatic() {
    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("foo/cls.groovy", """
    package foo
    class Zzzzzzz {
      static Zzz<caret>
    }
    """).virtualFile)
    myFixture.completeBasic()
    assert myFixture.editor.document.text.contains("static Zzzzzzz")
  }

  void testDontCompleteSubpackageOfImplicitlyImported() {
    myFixture.addFileToProject "A.groovy", """
in<caret>"""
    myFixture.testCompletionVariants "A.groovy", "int", "interface" //don't complete 'instrument' from 'java.lang'
  }


  void testEatingThisReference() {
    configure "def x = []; x.<caret> this"
    myFixture.completeBasic()
    myFixture.type 'ad\t'
    myFixture.checkResult "def x = []; x.add(<caret>) this"
  }

  void testEatingClosingParenthesis() {
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

  void testDontAddStaticImportSecondTime() {
    configure """import static java.lang.String.format
form<caret>"""

    myFixture.completeBasic()
    myFixture.checkResult """import static java.lang.String.format
format(<caret>)"""
  }

  void testImportAsterisk() {
    myFixture.configureByText "a.groovy", "import java.lang.<caret>"
    myFixture.completeBasic()
    myFixture.type '*\n'
    myFixture.checkResult "import java.lang.*\n<caret>"
  }

  void testNoDotsInImport() {
    myFixture.configureByText "a.groovy", "import java.<caret>"
    myFixture.completeBasic()
    myFixture.type 'lan\n'
    myFixture.checkResult "import java.lang<caret>"
  }

  void testInvalidScriptClass() {
    myFixture.addFileToProject("b.groovy", "//comment")
    myFixture.configureByText "a.groovy", "def b<caret>"
    myFixture.completeBasic()
    myFixture.checkResult "def b<caret>"
  }

  void testSpacesAroundEq() {
    myFixture.configureByText "a.groovy", "int xxx, xxy; xx<caret>"
    myFixture.completeBasic()
    myFixture.type '='
    myFixture.checkResult "int xxx, xxy; xxx = <caret>"
  }

  void testOnlyAnnotationsAfterAt() {
    myFixture.addClass "class AbcdClass {}; @interface AbcdXAnno {}"
    myFixture.configureByText "a.groovy", "@Abcd<caret> class A {}"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings[0] == 'AbcdXAnno'
  }

  void testOnlyAnnotationsAfterAtInMethodParameters() {
    myFixture.addClass "class AbcdClass {}; @interface AbcdAnno {}"
    myFixture.configureByText "a.groovy", "def foo(@Abcd<caret> ) {}"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings[0] == 'AbcdAnno'
  }

  void testNoCompletionInClassBodyComments() {
    myFixture.configureByText "a.groovy", "class Foo { /* protec<caret> */ }"
    assertEmpty(myFixture.completeBasic())
  }

  void testNoCompletionInCodeBlockComments() {
    myFixture.configureByText "a.groovy", "def Foo() { /* whil<caret> */ }"
    assertEmpty(myFixture.completeBasic())
  }

  void testParenthesesForExpectedClassTypeRegardlessInners() {
    myFixture.addClass "class Fooooo { interface Bar {} }"
    myFixture.configureByText "a.groovy", "Fooooo f = new Foo<caret>"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['Fooooo', 'Fooooo.Bar']
    myFixture.type '\n'
    myFixture.checkResult "Fooooo f = new Fooooo()<caret>"
  }

  void testParenthesesForUnexpectedClassTypeRegardingInners() {
    myFixture.addClass "class Fooooo { interface Bar {} }"
    myFixture.configureByText "a.groovy", "Fooooo.Bar f = new Foo<caret>"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['Fooooo', 'Fooooo.Bar']
    myFixture.type '\n'
    myFixture.checkResult "Fooooo.Bar f = new Fooooo()<caret>"
  }

  void testOnlyExceptionsInCatch() {
    myFixture.addClass "package foo; public class AbcdClass {}; public class AbcdException extends Throwable {}"
    myFixture.configureByText "a.groovy", "try {} catch (Abcd<caret>"
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult """import foo.AbcdException

try {} catch (AbcdException"""
  }

  void testOnlyExceptionsInCatch2() {
    myFixture.addClass "class AbcdClass {}; class AbcdException extends Throwable {}"
    myFixture.configureByText "a.groovy", "try {} catch (Abcd<caret> e) {}"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings[0] == 'AbcdException'
    myFixture.type('\n')
    myFixture.checkResult "try {} catch (AbcdException<caret> e) {}"
  }

  void testTopLevelClassesFromPackaged() {
    myFixture.addClass "public class Fooooo {}"
    final text = "package foo; class Bar { Fooo<caret> }"
    def file = myFixture.addFileToProject("foo/Bar.groovy", text)
    myFixture.configureFromExistingVirtualFile file.virtualFile
    assertEmpty myFixture.completeBasic()
    myFixture.checkResult text
  }

  void testLocalVarOverlaysField() {
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

  void testParenthesesInMethodCompletion() {
    myFixture.configureByText "a.groovy", """
def foo(def a) {2}
return fo<caret>"""
    myFixture.completeBasic()
    myFixture.checkResult """
def foo(def a) {2}
return foo()"""
  }

  void testFinishClassNameWithSquareBracket() {
    myFixture.addClass("class AbcdClass {}; class AbcdeClass {}")
    checkCompletion("Abcd<caret>", '[', "AbcdClass[<caret>]")
  }

  void testFinishClassNameWithSquareBracketAfterNew() {
    myFixture.addClass("class AbcdClass {}; class AbcdeClass {}")
    checkCompletion("new Abcd<caret>", '[', "new AbcdClass[<caret>]")
  }

  void testFinishMethodNameWithSquareBracket() {
    myFixture.addClass("""class AbcdClass {
      static int[] foo(int x){}
      static int[] fobar(){}
    }""")
    checkCompletion("AbcdClass.fo<caret>", '[', "AbcdClass.fobar()[<caret>]")
  }

  void testFinishVariableNameWithSquareBracket() {
    checkCompletion("int[] fooo, foobar; foo<caret>", '[', "int[] fooo, foobar; foobar[<caret>]")
  }

  void testFinishClassNameWithLt() {
    myFixture.addClass("class AbcdClass {}; class AbcdeClass {}")
    checkCompletion("Abcd<caret>", '<', "AbcdClass<<caret>>")
  }

  void testFinishClassNameWithLtAfterNew() {
    myFixture.addClass("class AbcdClass<T> {}; class AbcdeClass {}")
    checkCompletion("new Abcd<caret>", '<', "new AbcdClass<<caret>>()")
  }

  void testMapKeysUsedInFile() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    doVariantableTest 'foo1', 'foo3', 'foo4', 'Foo5', 'Foo7'
  }

  void testNoClassesAsMapKeys() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    doVariantableTest()
  }

  void testNamedArgsUsedInFile() {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    doVariantableTest 'foo2', 'false', 'float', 'foo1', 'foo3', 'foo4', 'foo5'
  }

  void testSuggestMembersOfExpectedType() {
    myFixture.addClass("enum Foo { aaaaaaaaaaaaaaaaaaaaaa, bbbbbb }")
    checkCompletion("Foo f = aaaaaaaa<caret>", '\n', "Foo f = Foo.aaaaaaaaaaaaaaaaaaaaaa<caret>")
  }

  void testFieldTypeAfterModifier() {
    myFixture.addClass("package bar; public class Fooooooooooo { }")
    checkCompletion '''
class A {
  private Foooo<caret>
}''', '\n', '''import bar.Fooooooooooo

class A {
  private Fooooooooooo<caret>
}'''
  }

  void testSuperClassProperty() {
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

  void testDoubleSpace() {
    checkCompletion "asse<caret>x", ' ', 'assert <caret>x'
  }

  void testPreferInstanceof() {
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

  void testForFinal() {
    assert doContainsTest('final', '''
class Fopppp {
    def foo() {
        for(fin<caret>
    }
}
''')
  }

  void testExcludeStringBuffer() {
    assert doContainsTest('StringBuffer', 'StringBuff<caret>f')
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, StringBuffer.name)
    assert !doContainsTest('StringBuffer', 'StringBuff<caret>f')
  }

  void testStaticMethodOnExcludedClass() {
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, String.name)
    assert doContainsTest('valueOf', 'String.val<caret>f')
  }

  private doContainsTest(String itemToCheck, String text) {
    myFixture.configureByText "a.groovy", text

    final LookupElement[] completion = myFixture.completeBasic()
    return completion.find { itemToCheck == it.lookupString }
  }

  void testWordCompletionInLiterals() {
    checkSingleItemCompletion('def foo = "fo<caret>"', 'def foo = "foo<caret>"')
  }

  void testWordCompletionInLiterals2() {
    checkSingleItemCompletion('''
println "abcd"
"a<caret>"
''', '''
println "abcd"
"abcd<caret>"
''')
  }

  void testWordCompletionInComments() {
    checkSingleItemCompletion('''
println "abcd"
// a<caret>"
''', '''
println "abcd"
// abcd<caret>"
''')
  }

  void testNoModifiersAfterDef() {
    doVariantableTest('def priv<caret>', '', CompletionType.BASIC, CompletionResult.notContain, 'private')
  }

  void testIfSpace() { checkCompletion 'int iff; if<caret>', ' ', "int iff; if <caret>" }

  void testIfParenthesis() { checkCompletion 'int iff; if<caret>', '(', "int iff; if (<caret>)" }

  void testMakingDefFromAssignment() { checkCompletion 'int defInt; de<caret>foo = 2', 'f ', "int defInt; def <caret>foo = 2" }

  void testEnumPropertyType() {
    checkSingleItemCompletion 'enum Foo {a,b; static List<StringBui<caret>>', "enum Foo {a,b; static List<StringBuilder<caret>>"
  }

  void testEnumPropertyType2() {
    checkSingleItemCompletion 'enum Foo {a,b; static List<StringBui<caret>', "enum Foo {a,b; static List<StringBuilder<caret>"
  }

  void testShowAccessor() {
    assertNotNull doContainsTest("getFoo", """
class MyClass {
  def foo
}

def a = new MyClass()
a.g<caret>
""")
  }

  void testContinue() {
    assertNotNull doContainsTest("continue", """
def conti = 4
while(true) {
  if (tst) cont<caret>
}""")
  }

  void testPreferParametersToClasses() {
    caseSensitiveNone()

    myFixture.configureByText "a.groovy", "def foo(stryng) { println str<caret> }"
    myFixture.completeBasic()
    assertEquals 'stryng', myFixture.lookupElementStrings[0]
  }

  void testFieldVsPackage() {
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

  void testFieldVsPackage2() {
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

  void testClassNameBeforeParentheses() {
    doBasicTest()
  }

  void testNewClassGenerics() {
    checkSingleItemCompletion 'new ArrayLi<caret>', 'new ArrayList<<caret>>()'
  }

  void testInnerClassStart() {
    checkSingleItemCompletion 'class Foo { cl<caret> }', 'class Foo { class <caret> }'
  }

  void testPropertyBeforeAccessor() {
    doVariantableTest 'soSe', 'setSoSe'
  }

  void testSortOrder0() {
    doVariantableTest 'se', 'setProperty', 'setSe', 'setMetaClass'
  }

  void testPrimitiveCastOverwrite() {
    checkCompletion 'byte v1 = (by<caret>te) 0', '\t', 'byte v1 = (byte<caret>) 0'
  }

  void testInitializerMatters() {
    myFixture.configureByText("a.groovy", "class Foo {{ String f<caret>x = getFoo(); }; String getFoo() {}; }")
    myFixture.completeBasic()
    assertOrderedEquals(myFixture.lookupElementStrings, ["foo"])
  }

  void testFieldInitializerMatters() {
    myFixture.configureByText("a.groovy", "class Foo { String f<caret>x = getFoo(); String getFoo() {}; }")
    myFixture.completeBasic()
    assertOrderedEquals(myFixture.lookupElementStrings, ["foo"])
  }

  void testAccessStaticViaInstanceSecond() {
    myFixture.configureByText("a.groovy", """
public class KeyVO {
  { this.fo<caret>x }
  static void foo() {}
  static void foox() {}
}
""")
    myFixture.complete(CompletionType.BASIC, 1)
    assertOrderedEquals(myFixture.lookupElementStrings, ['foo', 'foox'])
  }

  void testPreferInstanceMethodViaInstanceSecond() {
    myFixture.configureByText("a.groovy", """
public class KeyVO {
  { this.fo<caret>x }
  static void foo() {}
  static void foox() {}

  void fooy() {}
  void fooz() {}
}
""")
    myFixture.complete(CompletionType.BASIC, 1)
    assertOrderedEquals(myFixture.lookupElementStrings, ['fooy', 'fooz'])
  }


  void testNoRepeatingModifiers() {
    myFixture.configureByText 'a.groovy', 'class A { public static <caret> }'
    myFixture.completeBasic()
    assert !('public' in myFixture.lookupElementStrings)
    assert !('static' in myFixture.lookupElementStrings)
    assert 'final' in myFixture.lookupElementStrings
  }

  void testSpaceTail1() {
    checkCompletion 'class A ex<caret> ArrayList {}', ' ', 'class A extends <caret> ArrayList {}'
  }

  void testSpaceTail3() {
    checkSingleItemCompletion 'class Foo impl<caret> {}', 'class Foo implements <caret> {}'
  }

  void testAmbiguousClassQualifier() {
    myFixture.addFileToProject("Util-invalid.groovy", "println 42")
    myFixture.addClass("package foo; public class Util { public static void foo() {} }")
    myFixture.addClass("package bar; public class Util { public static void bar() {} }")
    myFixture.configureByText 'a.groovy', 'Util.<caret>'
    myFixture.completeBasic()
    assertOrderedEquals myFixture.lookupElementStrings[0..1], ['Util.bar', 'Util.foo']

    def presentation = LookupElementPresentation.renderElement(myFixture.lookupElements[0])
    assertEquals 'Util.bar', presentation.itemText
    assertEquals '() (bar)', presentation.tailText
    assert !presentation.tailGrayed

    myFixture.type 'f\n'
    myFixture.checkResult '''import foo.Util

Util.foo()<caret>'''
  }

  void testUseDescendantStaticImport() { doBasicTest() }

  void testPreferInterfacesInImplements() {
    myFixture.addClass('interface FooIntf {}')
    myFixture.addClass('class FooClass {}')
    doVariantableTest('FooIntf', 'FooClass')
  }

  void testPropertyChain() { doBasicTest() }

  void testMethodPointer() {
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

  void testFieldPointer() {
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

  void testPrivateFieldOnSecondInvocation() {
    myFixture.configureByText('_a.groovy', '''\
class Base {
  private int field1
}

new Base().fie<caret>x''')
    myFixture.complete(CompletionType.BASIC, 2)
    assert myFixture.lookupElementStrings == ['field1']
  }

  void testForIn() {
    assert doContainsTest('in', 'for (int i i<caret>')
    assert doContainsTest('in', 'for (i i<caret>')
  }

  void testReturnInVoidMethod() {
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

  void testReturnInNotVoidMethod() {
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

  void testForSpace() {
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
def foo(List<? <caret>)''')
    myFixture.complete(CompletionType.BASIC)
    assertOrderedEquals(myFixture.lookupElementStrings, "extends", "super")
  }

  void testSuperExtendsInTypeParams2() {
    myFixture.configureByText("_.groovy", '''\
def foo(List<? <caret>> list)''')
    myFixture.complete(CompletionType.BASIC)
    assertOrderedEquals(myFixture.lookupElementStrings, "extends", "super")
  }

  void testMapDontCompleteProperties() {
    myFixture.configureByText("_.groovy", '''\
def map = [1:2]
print map.metc<caret>
''')
    myFixture.complete(CompletionType.BASIC)
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

  void testPreferApplicableAnnotations() {
    myFixture.addClass '''
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE})
@interface TMetaAnno {}

@Target({ElementType.LOCAL_VARIABLE})
@interface TLocalAnno {}'''

    configure('@T<caret> @interface Foo {}')
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'TMetaAnno', 'Target', 'TabLayoutPolicy', 'TabPlacement'
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

  void testCompleteInaccessibleConstructors() {
    doBasicTest('''\
class Foooo {
  private Foooo(int x) {}
}

new Fooo<caret>
''', '''\
class Foooo {
  private Foooo(int x) {}
}

new Foooo(<caret>)
''')
  }

  void testCompleteInaccessibleVsAccessibleConstructors() {
    doBasicTest('''\
class Foooo {
  private Foooo(int x) {}
  public Foooo() {}
}

new Fooo<caret>
''', '''\
class Foooo {
  private Foooo(int x) {}
  public Foooo() {}
}

new Foooo()<caret>
''')
  }

  void testBooleanInNewExpr() {
    doBasicTest('''\
def b = new boolea<caret>
''', '''\
def b = new boolean<caret>
''')
  }

  void testCompleteSameNameClassFromOtherPackage() {
    myFixture.addClass('''\
package foo;
public class Myclass{}
''')
    myFixture.addClass('''\
package bar;
public class Myclass{}
''')

    myFixture.configureByText('test.groovy', '''\
package bar

print new foo.My<caret>class()
''')

    final atCaret = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
    final ref = PsiTreeUtil.getParentOfType(atCaret, GrCodeReferenceElement)
    GrReferenceAdjuster.shortenReference(ref)

    myFixture.checkResult('''\
package bar

print new foo.Myclass()
''')
  }

  void "test def before assignment"() {
    assert doContainsTest("def", """
void foo() {
  <caret> = baz
}""")
  }

  void testAliasAnnotation() {
    myFixture.addClass '''\
package groovy.transform;

@java.lang.annotation.Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
public @interface AnnotationCollector {
    String processor() default "org.codehaus.groovy.transform.AnnotationCollectorTransform";
    Class[] value() default {};
}
'''

    doVariantableTest('''\
import groovy.transform.AnnotationCollector

@interface Bar {
    int xxx()
}

@interface Foo {
    int yyy()
}

@Foo @Bar
@AnnotationCollector()
@interface Alias1 {}

@Alias1(<caret>)
class Baz {}''', '', CompletionType.BASIC, CompletionResult.contain, 'xxx', 'yyy')
  }

  void "test honor statistics"() {
    ((StatisticsManagerImpl)StatisticsManager.instance).enableStatistics(myFixture.testRootDisposable)

    myFixture.addClass("class Foo { Object getMessage() {} }; class Bar extends Foo { Object getMessages(); }")
    configure "b = new Bar();\nb.mes<caret>"
    def items = myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, "messages", "message"
    myFixture.lookup.currentItem = items[1]
    myFixture.type('\n\nb.mes')
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, "message", "messages"
  }

  void testFieldCompletionFromJavaClass() {
    myFixture.addClass("""\
class Base {
    static public Base foo;
}

class Inheritor extends Base {
    static public Inheritor foo;
}
""")

    doVariantableTest('Inheritor.fo<caret>', '', CompletionType.BASIC, CompletionResult.equal, 'foo', 'forName', 'forName')
  }

  void testBinding1() {
    doCompletionTest('''\
aaa = 5
print aa<caret>
''', '''\
aaa = 5
print aaa<caret>
''', CompletionType.BASIC)
  }

  void testBinding2() {
    doCompletionTest('''\
def foo() {
  aaa = 5
}
print aa<caret>
''', '''\
def foo() {
  aaa = 5
}
print aaa<caret>
''', CompletionType.BASIC)
  }


  void testBinding3() {
    doVariantableTest('''\
def x() {
  aaa = 5
}

aaaa = 6
print aa<caret>
''', CompletionType.BASIC, 'aaa', 'aaaa')
  }

  void testCompleteRefInLValue() {
    myFixture.addClass('''\
public class Util {
    public int CONST = 4;
}
''')
    doVariantableTest('''\
def foo(Util util) {
  util.CONS<caret>T = 3
}
''', '', CompletionType.BASIC, CompletionResult.contain, 'CONST')
  }

  void testInnerClassOfAnonymous() {
    doCompletionTest(
      '''
        def r = new Runnable() {
          void run() {
            Data data = new <caret>
          }

          private static class Data {}
        }
      ''',
      '''
        def r = new Runnable() {
          void run() {
            Data data = new Data()<caret>
          }

          private static class Data {}
        }
      ''', CompletionType.SMART)
  }

  void testDollarInGString() {
    doCompletionTest('''\
class Autocompletion {
    def reportDir = '/'
    def reportDirectory = '/'
    def fileName = "$reportD<caret>${File.separator}"
}
''', '''\
class Autocompletion {
    def reportDir = '/'
    def reportDirectory = '/'
    def fileName = "$reportDir<caret>${File.separator}"
}
''', '\t', CompletionType.BASIC)
  }

  void testDollarInGString2() {
    doCompletionTest('''\
class Autocompletion {
    def reportDir = '/'
    def fileName = "$report<caret>D${File.separator}"
}
''', '''\
class Autocompletion {
    def reportDir = '/'
    def fileName = "$reportDir<caret>${File.separator}"
}
''', '\t', CompletionType.BASIC)
  }

  void testSpaceBeforeMethodCallParentheses() {
    def settings = CodeStyleSettingsManager.getSettings(myFixture.project).getCommonSettings(GroovyLanguage.INSTANCE)

    boolean old = settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES
    try {
      settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
      doCompletionTest('''\
def foooo() {}
fooo<caret>
''', '''\
def foooo() {}
foooo ()<caret>
''', '', CompletionType.BASIC)
    }
    finally {
      settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = old
    }
  }

  void testNoClassNamesInComments() {
    doVariantableTest("""\
class drop{}
class dropX{}

class A {
/*
    print dr<caret>
*/
}
""", "o", CompletionType.BASIC, CompletionResult.equal, 0)
  }

  void testIntellijIdeaRulezzzNotInCompletion() {
    doVariantableTest('''\
def foo() {
  def var
  va<caret>r = 'abc'
}
''', '', CompletionType.BASIC, CompletionResult.notContain, 1, 'vaIntellijIdeaRulezzzr')
  }

  void testTraitWithAsOperator1() {
    doVariantableTest('''
trait A {
  def foo(){}
}
class B {
  def bar() {}
}

def var = new B() as A
var.<caret>
''', '', CompletionType.BASIC, CompletionResult.contain, 1, 'foo', 'bar')
  }

  void testTraitWithAsOperator2() {
    doVariantableTest('''
trait A {
  public foo = 5
}
class B {
  def bar() {}
}

def var = new B() as A
var.<caret>
''', '', CompletionType.BASIC, CompletionResult.contain, 1, 'A__foo', 'bar')
  }

  void testCharsetName() {
    myFixture.addClass("package java.nio.charset; public class Charset { public static boolean isSupported(String s) {} }")
    doVariantableTest('import java.nio.charset.*; Charset.isSupported("<caret>")', '', CompletionType.BASIC, CompletionResult.contain, 1,
                      'UTF-8')
  }

  void "test override super methods completion"() {
    doVariantableTest('''
class A {
    def foo() {}
    def bar(a, b, List c) {}
    def baz() {}
}
class B extends A {
  <caret>
}
''', '', CompletionType.BASIC, CompletionResult.contain, 1, 'public Object bar', 'public Object baz', 'public Object foo')
  }

  void "test override trait method completion"() {
    doVariantableTest('''
trait T<X> {
  X quack() {}
}

class C implements T<String> {
  <caret>
}
''', '', CompletionType.BASIC, CompletionResult.contain, 1, 'public String quack')
  }

  void "test non-imported class after new"() {
    def uClass = myFixture.addClass('package foo; public class U {}')
    configure('new U<caret>x')
    myFixture.completeBasic()
    assert myFixture.lookupElements[0].object == uClass
  }
}
