// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToJava

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Maxim.Medvedev
 */
class CodeBlockGenerationTest extends LightGroovyTestCase {
  final String basePath = TestUtils.testDataPath + 'refactoring/convertGroovyToJava/codeBlock'

  private void doTest() {
    final String testName = getTestName(true)
    final PsiFile file = myFixture.configureByFile(testName + '.groovy')
    assertInstanceOf file, GroovyFile

    GrTopStatement[] statements = file.topStatements
    final StringBuilder builder = new StringBuilder()
    def generator = new CodeBlockGenerator(builder, new ExpressionContext(project))
    for (def statement : statements) {
      statement.accept(generator)
      builder.append('\n')
    }

    final PsiFile result = createLightFile(testName + '.java', JavaLanguage.INSTANCE, builder.toString())
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
    final String text = result.text
    final String expected = psiManager.findFile(myFixture.copyFileToProject(testName + '.java')).text
    assertEquals expected.trim(), text.trim()
  }

  private addFile(String text) {
    myFixture.addFileToProject("Bar.groovy", text)
  }

  void testSwitch1() { doTest() }

  void testSwitch2() { doTest() }

  void testSwitch3() { doTest() }

  void testSwitch4() { doTest() }

  void testSwitch5() { doTest() }
  void testSwitch6() { doTest() }

  void _testWhile1() { doTest() }

  void _testWhile2() { doTest() }

  void _testWhile3() { doTest() }

  void testRefExpr() {
    myFixture.addFileToProject 'Bar.groovy', '''
class Bar {
  def foo = 2

  def getBar() {3}
}
class MyCat {
  static getAbc(Bar b) {
    return 4
  }
}
'''

    doTest()
  }

  void testMemberPointer() { doTest() }

  void testCompareMethods() {
    addFile '''
class Bar {
  def compareTo(def other) {1}
}'''
  }

  void testPrefixInc() {
    addFile """
class Bar {
    def next() {
        new Bar(state:state+1)
    }
}

class Upper {
    private test=new Test()
    def getTest(){println "getTest"; test}
}

class Test {
    public state = new Bar()
    def getBar() {println "get"; state}
    void setBar(def bar) {println "set";state = bar}
}
"""
    doTest()
  }

  void testUnaryMethods() {
    addFile """
class Bar {
  def positive(){}
  def negative(){}
  def bitwiseNegate(){}
}"""
  }

  void testRegex() {
    myFixture.addFileToProject("java/util/regex/Pattern.java", """
package java.util.regex;

public final class Pattern {
  public static Pattern compile(String regex) {return new Pattern();}
  public Matcher matcher(CharSequence input){return new Matcher();}
  public static boolean matches(String regex, CharSequence input) {return true;}
}""")

    myFixture.addFileToProject("java/util/regex/Matcher.java", """
package java.util.regex;

public final class Matcher {
  public boolean matches() {return true;}
}

""")
    doTest()
  }

  void testAsBoolean() { doTest() }

  void testCharInitializer() { doTest() }

  void testAnonymousFromMap() { doTest() }

  void testClosure() { doTest() }

  void testListAsArray() { doTest() }

  void testUnresolvedArrayAccess() { doTest() }

  void testArrayAccess() { doTest() }

  void testCastWithEquality() {
    addBigDecimal()
    doTest()
  }

  void testAsserts() { doTest() }

  void testConditional() { doTest() }

  void testBinary() { doTest() }

  void testSafeCast() { doTest() }

  void testNewExpr() { doTest() }

  void testMapNameAlreadyused() { doTest() }

  void testEmptyMap() { doTest() }

  void testEmptyList() { doTest() }

  void testErasedArrayInitializer() { doTest() }

  void testTupleVariableDeclaration() { doTest() }

  void testEmptyVarargs() { doTest() }

  void testClassReference() { doTest() }

  void testClassMethod() { doTest() }

  void testEquals() { doTest() }

  void testSelfNavigatingOperator() { doTest() }

  void testComparisonToNull() { doTest() }

  void testStringConverting() { doTest() }

  void testInWitchClassCheck() { doTest() }

  void testSwitch() { doTest() }

  void testPropSelection() { doTest() }

  void testSafeChain() { doTest() }
}
