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
package com.intellij.ide.highlighter.custom

import com.intellij.lang.cacheBuilder.WordOccurrence
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.PlainTextSyntaxHighlighterFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding
import com.intellij.testFramework.LexerTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.Processor
import com.intellij.util.ThrowableRunnable
import groovy.transform.CompileStatic
import junit.framework.TestCase
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
/**
 * @author peter
 */
@CompileStatic
class CustomFileTypeLexerTest extends TestCase {

  private static void doTest(SyntaxTable table, @NonNls String text, @Nullable String expected) {
    def lexer = new CustomFileTypeLexer(table)
    doTest(lexer, text, expected)
  }

  private static void doTest(Lexer lexer, String text, String expected) {
    assertEquals(expected, LexerTestCase.printTokens(text, 0, lexer))
  }

  private static SyntaxTable createGenericTable() {
    SyntaxTable table = new SyntaxTable()

    table.lineComment = ';'
    table.lineCommentOnlyAtStart = true

    table.addKeyword1("if")
    table.addKeyword1("then")
    table.addKeyword2("return")
    table.addKeyword1("length")

    return table

  }

  void testSpacesInsideKeywords() {
    def table = createGenericTable()
    table.addKeyword1("sysvar ")
    doTest table, 'if length(if_variable)then return 1 sysvar  ', '''\
KEYWORD_1 ('if')
WHITESPACE (' ')
KEYWORD_1 ('length')
CHARACTER ('(')
IDENTIFIER ('if_variable')
CHARACTER (')')
KEYWORD_1 ('then')
WHITESPACE (' ')
KEYWORD_2 ('return')
WHITESPACE (' ')
NUMBER ('1')
WHITESPACE (' ')
KEYWORD_1 ('sysvar ')
WHITESPACE (' ')
'''
  }

  void testFortranComments() {
    doTest createGenericTable(), '''
foo;noncomment
;comment
  ;noncomment
''', '''\
WHITESPACE ('\\n')
IDENTIFIER ('foo')
PUNCTUATION (';')
IDENTIFIER ('noncomment')
WHITESPACE ('\\n')
LINE_COMMENT (';comment')
WHITESPACE ('\\n  ')
PUNCTUATION (';')
IDENTIFIER ('noncomment')
WHITESPACE ('\\n')
'''
  }

  void "test punctuation keywords"() {
    def table = createGenericTable()
    table.addKeyword4("+")
    table.addKeyword4("-")
    table.addKeyword4("=")
    doTest table, 'i++; i--; i-100; i -100; i- 100; a=-1; a= -1; a+=1; a= +1;', '''\
IDENTIFIER ('i')
KEYWORD_4 ('+')
KEYWORD_4 ('+')
PUNCTUATION (';')
WHITESPACE (' ')
IDENTIFIER ('i')
KEYWORD_4 ('-')
KEYWORD_4 ('-')
PUNCTUATION (';')
WHITESPACE (' ')
IDENTIFIER ('i')
KEYWORD_4 ('-')
NUMBER ('100')
PUNCTUATION (';')
WHITESPACE (' ')
IDENTIFIER ('i')
WHITESPACE (' ')
KEYWORD_4 ('-')
NUMBER ('100')
PUNCTUATION (';')
WHITESPACE (' ')
IDENTIFIER ('i')
KEYWORD_4 ('-')
WHITESPACE (' ')
NUMBER ('100')
PUNCTUATION (';')
WHITESPACE (' ')
IDENTIFIER ('a')
KEYWORD_4 ('=')
KEYWORD_4 ('-')
NUMBER ('1')
PUNCTUATION (';')
WHITESPACE (' ')
IDENTIFIER ('a')
KEYWORD_4 ('=')
WHITESPACE (' ')
KEYWORD_4 ('-')
NUMBER ('1')
PUNCTUATION (';')
WHITESPACE (' ')
IDENTIFIER ('a')
KEYWORD_4 ('+')
KEYWORD_4 ('=')
NUMBER ('1')
PUNCTUATION (';')
WHITESPACE (' ')
IDENTIFIER ('a')
KEYWORD_4 ('=')
WHITESPACE (' ')
KEYWORD_4 ('+')
NUMBER ('1')
PUNCTUATION (';')
'''
  }

  private static SyntaxTable createJavaSyntaxTable() {
    SyntaxTable table = new SyntaxTable()

    table.setLineComment("//")
    table.setStartComment("/*")
    table.setEndComment("*/")

    table.setHexPrefix("0x")
    table.setNumPostfixChars("cfdle")

    table.addKeyword1("package")
    table.addKeyword1("import")
    table.addKeyword1("this")
    table.addKeyword1("super")
    table.addKeyword1("public")
    table.addKeyword1("private")
    table.addKeyword1("protected")
    table.addKeyword1("null")
    table.addKeyword1("if")
    table.addKeyword1("else")
    table.addKeyword1("throws")
    table.addKeyword1("switch")
    table.addKeyword1("case")
    table.addKeyword1("break")
    table.addKeyword1("default")
    table.addKeyword1("continue")
    table.addKeyword1("goto")
    table.addKeyword1("boolean")
    table.addKeyword1("true")
    table.addKeyword1("false")
    table.addKeyword1("final")
    table.addKeyword1("class")
    table.addKeyword1("static")
    table.addKeyword1("final")
    table.addKeyword1("void")
    table.addKeyword1("int")
    table.addKeyword1("while")
    table.addKeyword1("new")
    table.addKeyword1("for")
    table.addKeyword1("byte")
    table.addKeyword1("float")
    table.addKeyword1("double")
    table.addKeyword1("short")
    table.addKeyword1("extends")
    table.addKeyword1("implements")
    table.addKeyword1("interface")
    table.addKeyword1("abstract")
    table.addKeyword1("char")
    table.addKeyword1("try")
    table.addKeyword1("catch")
    table.addKeyword1("finally")
    table.addKeyword1("synchronized")

    return table
  }

  void testParseSampleCode() {
    doTest createJavaSyntaxTable(), "private some text f b g\n\n\n//   1\n  public static void main(String[] args) {\n}\n-10 - 10\n\"dsfdfdf\"\n/* a\n *bc */", '''\
KEYWORD_1 ('private')
WHITESPACE (' ')
IDENTIFIER ('some')
WHITESPACE (' ')
IDENTIFIER ('text')
WHITESPACE (' ')
IDENTIFIER ('f')
WHITESPACE (' ')
IDENTIFIER ('b')
WHITESPACE (' ')
IDENTIFIER ('g')
WHITESPACE ('\\n\\n\\n')
LINE_COMMENT ('//   1')
WHITESPACE ('\\n  ')
KEYWORD_1 ('public')
WHITESPACE (' ')
KEYWORD_1 ('static')
WHITESPACE (' ')
KEYWORD_1 ('void')
WHITESPACE (' ')
IDENTIFIER ('main')
CHARACTER ('(')
IDENTIFIER ('String')
CHARACTER ('[')
CHARACTER (']')
WHITESPACE (' ')
IDENTIFIER ('args')
CHARACTER (')')
WHITESPACE (' ')
CHARACTER ('{')
WHITESPACE ('\\n')
CHARACTER ('}')
WHITESPACE ('\\n')
CHARACTER ('-')
NUMBER ('10')
WHITESPACE (' ')
CHARACTER ('-')
WHITESPACE (' ')
NUMBER ('10')
WHITESPACE ('\\n')
STRING ('"dsfdfdf"')
WHITESPACE ('\\n')
MULTI_LINE_COMMENT ('/* a\\n *bc */')
'''
  }

  void testBlockCommentStart() {
    doTest createJavaSyntaxTable(), "/*", 'MULTI_LINE_COMMENT (\'/*\')\n'
  }

  void testLineCommentStart() {
    doTest createJavaSyntaxTable(), "//", 'LINE_COMMENT (\'//\')\n'
  }

  void testEmpty() {
    doTest createJavaSyntaxTable(), "", ''
  }

  void testSpace() {
    doTest createJavaSyntaxTable(), " ", 'WHITESPACE (\' \')\n'
  }

  void testParseSampleCodeFromTo() {
    String sampleCode = "  int n=123;\n  float z=1;"
    def lexer = new CustomFileTypeLexer(createJavaSyntaxTable())
    lexer.start(sampleCode, 5, 5)
    assertEquals(lexer.getTokenType(), null)
    lexer.start(sampleCode, 5, 6)
    lexer.getTokenType()
    assertEquals(5, lexer.getTokenStart())
    assertEquals(6, lexer.getTokenEnd())
    assertEquals(6, lexer.getBufferEnd())
  }

  private static SyntaxTable createPropTable() {
    SyntaxTable table = new SyntaxTable()

    table.setLineComment("#")
    table.setIgnoreCase(true)

    table.addKeyword1("value")
    table.addKeyword2("Value")
    table.setNumPostfixChars("LGH")
    return table
  }

  void testSimple() {
    doTest createPropTable(), "# Comment\n" +
                              "x.1.a=12.2L\n" +
                              "   y.2.b=13.4 # comment\n" +
                              "VALUE value VaLuE Value1 17.00h 11.0k", '''\
LINE_COMMENT ('# Comment')
WHITESPACE ('\\n')
IDENTIFIER ('x')
PUNCTUATION ('.')
NUMBER ('1')
PUNCTUATION ('.')
IDENTIFIER ('a')
CHARACTER ('=')
NUMBER ('12.2L')
WHITESPACE ('\\n   ')
IDENTIFIER ('y')
PUNCTUATION ('.')
NUMBER ('2')
PUNCTUATION ('.')
IDENTIFIER ('b')
CHARACTER ('=')
NUMBER ('13.4')
WHITESPACE (' ')
LINE_COMMENT ('# comment')
WHITESPACE ('\\n')
KEYWORD_1 ('VALUE')
WHITESPACE (' ')
KEYWORD_1 ('value')
WHITESPACE (' ')
KEYWORD_1 ('VaLuE')
WHITESPACE (' ')
IDENTIFIER ('Value1')
WHITESPACE (' ')
NUMBER ('17.00h')
WHITESPACE (' ')
NUMBER ('11')
PUNCTUATION ('.')
NUMBER ('0')
IDENTIFIER ('k')
'''
  }

  void testCpp() {
    SyntaxTable table = new SyntaxTable()
    table.addKeyword1('->')
    doTest table, "foo->bar", '''\
IDENTIFIER ('foo')
KEYWORD_1 ('->')
IDENTIFIER ('bar')
'''
  }

  void testNumber() {
    doTest createPropTable(), "1.23=1.24", '''\
NUMBER ('1.23')
CHARACTER ('=')
NUMBER ('1.24')
'''
  }

  void testPostfix() {
    doTest createPropTable(), "abc 1.2ltext", '''\
IDENTIFIER ('abc')
WHITESPACE (' ')
NUMBER ('1.2l')
IDENTIFIER ('text')
'''
  }

  void testWeird() {
    doTest createPropTable(), "test.1.", '''\
IDENTIFIER ('test')
PUNCTUATION ('.')
NUMBER ('1.')
'''
  }

  void testParenths() throws Exception {
    doTest createPropTable(),"value(255)", '''\
KEYWORD_1 ('value')
CHARACTER ('(')
NUMBER ('255')
CHARACTER (')')
'''
  }

  void testSpecialCharactersInKeywords() {
    SyntaxTable table = new SyntaxTable()
    table.addKeyword1("a*")
    table.addKeyword1("b-c")
    table.addKeyword1(":")
    table.addKeyword2("d#")
    table.addKeyword2("e")
    table.addKeyword2("foo{}")
    table.addKeyword3("foldl'")
    doTest table, "a* b-c d# e- e foo{} : foldl' foo", '''\
KEYWORD_1 ('a*')
WHITESPACE (' ')
KEYWORD_1 ('b-c')
WHITESPACE (' ')
KEYWORD_2 ('d#')
WHITESPACE (' ')
KEYWORD_2 ('e')
CHARACTER ('-')
WHITESPACE (' ')
KEYWORD_2 ('e')
WHITESPACE (' ')
KEYWORD_2 ('foo{}')
WHITESPACE (' ')
KEYWORD_1 (':')
WHITESPACE (' ')
KEYWORD_3 ('foldl'')
WHITESPACE (' ')
IDENTIFIER ('foo')
'''
  }

  void testWordsScanner() {
    SyntaxTable table = new SyntaxTable()
    table.addKeyword1("a*")
    def scanner = IdTableBuilding.createCustomFileTypeScanner(table)
    def words = []
    String text = 'a* b-c d# e$ foo{}'
    def expectedWords = ['a', 'b', 'c', 'd', 'e$', 'foo']

    scanner.processWords(text, { WordOccurrence w ->
      words.add(w.baseText.subSequence(w.start, w.end))
    } as Processor)
    assert words == expectedWords

    // words searched by find usages should be the same as words produced by word scanner
    assert StringUtil.getWordsIn(text) == expectedWords 
  }

  void "test quote block comment"() {
    SyntaxTable table = new SyntaxTable()
    table.startComment = '"'
    table.endComment = 'x'
    doTest table, '"axa', '''\
MULTI_LINE_COMMENT ('"ax')
IDENTIFIER ('a')
'''
  }

  void testPlainText() {
    doTest PlainTextSyntaxHighlighterFactory.createPlainTextLexer(), 'ab.@c  (<def>)', '''\
CHARACTER ('ab.@c')
WHITESPACE ('  ')
L_PARENTH ('(')
L_BROCKET ('<')
CHARACTER ('def')
R_BROCKET ('>')
R_PARENTH (')')
'''
  }

  void "test hex literals"() {
    SyntaxTable table = new SyntaxTable()
    table.hexPrefix = '0y'
    doTest table, '1 0yabc0', '''\
NUMBER ('1')
WHITESPACE (' ')
NUMBER ('0yabc0')
'''
  }

  void testKeywordLexerPerformance() {
    int count = 3000
    List<String> keywords = []
    for (i in 0..<count) {
      char start = (('a' as char) + (i % 7)).intValue()
      keywords.add((start as String) * i)
    }
    SyntaxTable table = new SyntaxTable()
    for (s in keywords) {
      table.addKeyword1(s)
    }
    
    StringBuilder text = new StringBuilder()
    for (i in 0..10) {
      Collections.shuffle(keywords)
      for (s in keywords[0..10]) {
        text.append(s + "\n")
      }
    }
    
    CharSequence bombed = new SlowCharSequence(text)
    ThrowableRunnable cl = { LexerTestCase.printTokens(bombed, 0, new CustomFileTypeLexer(table)) } as ThrowableRunnable
    PlatformTestUtil.startPerformanceTest(name, 4500, cl).assertTiming()
  }

}

class SlowCharSequence extends StringUtil.BombedCharSequence {
  SlowCharSequence(CharSequence sequence) {
    super(sequence)
  }

  @NotNull
  @Override
  CharSequence subSequence(int i, int i1) {
    return new SlowCharSequence(super.subSequence(i, i1))
  }

  @Override
  char charAt(int i) {
    (0..100).each {  }
    return super.charAt(i)
  }

  @Override
  protected void checkCanceled() {
  }
  
}