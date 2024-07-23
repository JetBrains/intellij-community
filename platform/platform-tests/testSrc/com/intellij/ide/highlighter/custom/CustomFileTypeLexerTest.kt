// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter.custom

import com.intellij.lang.cacheBuilder.WordOccurrence
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.PlainTextSyntaxHighlighterFactory
import com.intellij.openapi.util.IntRef
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding
import com.intellij.testFramework.LexerTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import junit.framework.TestCase
import org.jetbrains.annotations.NonNls

class CustomFileTypeLexerTest : TestCase() {

  private fun doTest(table: SyntaxTable, @NonNls text: String, expected: String?) {
    val lexer = CustomFileTypeLexer(table)
    doTest(lexer, text, expected)
  }

  private fun doTest(lexer: Lexer, text: String, expected: String?) {
    assertEquals(expected?.trimStart(), LexerTestCase.printTokens(text, 0, lexer))
  }

  private fun createGenericTable(): SyntaxTable {
    val table = SyntaxTable()

    table.lineComment = ";"
    table.lineCommentOnlyAtStart = true

    table.addKeyword1("if")
    table.addKeyword1("then")
    table.addKeyword2("return")
    table.addKeyword1("length")

    return table
  }

  fun testSpacesInsideKeywords() {
    val table = createGenericTable()
    table.addKeyword1("sysvar ")
    doTest(
      table, "if length(if_variable)then return 1 sysvar  ", """
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
"""
    )
  }

  fun testFortranComments() {
    doTest(createGenericTable(), """
foo;noncomment
;comment
  ;noncomment
""", """
WHITESPACE ('\n')
IDENTIFIER ('foo')
PUNCTUATION (';')
IDENTIFIER ('noncomment')
WHITESPACE ('\n')
LINE_COMMENT (';comment')
WHITESPACE ('\n  ')
PUNCTUATION (';')
IDENTIFIER ('noncomment')
WHITESPACE ('\n')
""")
  }

  fun `test punctuation keywords`() {
    val table = createGenericTable()
    table.addKeyword4("+")
    table.addKeyword4("-")
    table.addKeyword4("=")
    doTest(table, "i++; i--; i-100; i -100; i- 100; a=-1; a= -1; a+=1; a= +1;", """
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
""")
  }

  private fun createJavaSyntaxTable(): SyntaxTable {
    val table = SyntaxTable()

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

  fun testParseSampleCode() {
    doTest(createJavaSyntaxTable(),
           "private some text f b g\n\n\n//   1\n  public static void main(String[] args) {\n}\n-10 - 10\n\"dsfdfdf\"\n/* a\n *bc */", """
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
WHITESPACE ('\n\n\n')
LINE_COMMENT ('//   1')
WHITESPACE ('\n  ')
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
WHITESPACE ('\n')
CHARACTER ('}')
WHITESPACE ('\n')
CHARACTER ('-')
NUMBER ('10')
WHITESPACE (' ')
CHARACTER ('-')
WHITESPACE (' ')
NUMBER ('10')
WHITESPACE ('\n')
STRING ('"dsfdfdf"')
WHITESPACE ('\n')
MULTI_LINE_COMMENT ('/* a\n *bc */')
""")
  }

  fun testBlockCommentStart() {
    doTest(createJavaSyntaxTable(), "/*", "MULTI_LINE_COMMENT ('/*')\n")
  }

  fun testLineCommentStart() {
    doTest(createJavaSyntaxTable(), "//", "LINE_COMMENT ('//')\n")
  }

  fun `test block comment start overrides line comment start`() {
    val table = SyntaxTable()
    table.lineComment = "#"
    table.startComment = "#{"
    table.endComment = "}#"
    doTest(table, "#{ \nblock\n }#\n# line\nid", """
MULTI_LINE_COMMENT ('#{ \nblock\n }#')
WHITESPACE ('\n')
LINE_COMMENT ('# line')
WHITESPACE ('\n')
IDENTIFIER ('id')
""")
  }

  fun `test line comment start overrides block comment start`() {
    val table = SyntaxTable()
    table.lineComment = "##"
    table.startComment = "#"
    table.endComment = "/#"
    doTest(table, "#\nblock\n/#\n## line\nid", """
MULTI_LINE_COMMENT ('#\nblock\n/#')
WHITESPACE ('\n')
LINE_COMMENT ('## line')
WHITESPACE ('\n')
IDENTIFIER ('id')
""")
  }

  fun testEmpty() {
    doTest(createJavaSyntaxTable(), "", "")
  }

  fun testSpace() {
    doTest(createJavaSyntaxTable(), " ", "WHITESPACE (' ')\n")
  }

  fun testParseSampleCodeFromTo() {
    val sampleCode = "  int n=123;\n  float z=1;"
    val lexer = CustomFileTypeLexer(createJavaSyntaxTable())
    lexer.start(sampleCode, 5, 5)
    assertNull(lexer.tokenType)
    lexer.start(sampleCode, 5, 6)
    lexer.tokenType
    assertEquals(5, lexer.tokenStart)
    assertEquals(6, lexer.tokenEnd)
    assertEquals(6, lexer.bufferEnd)
  }

  private fun createPropTable(): SyntaxTable {
    val table = SyntaxTable()

    table.setLineComment("#")
    table.isIgnoreCase = true

    table.addKeyword1("value")
    table.addKeyword2("Value")
    table.setNumPostfixChars("LGH")
    return table
  }

  fun testSimple() {
    doTest(
      createPropTable(), """
# Comment
x.1.a=12.2L
   y.2.b=13.4 # comment
VALUE value VaLuE Value1 17.00h 11.0k
""".trimMargin(), """
LINE_COMMENT ('# Comment')
WHITESPACE ('\n')
IDENTIFIER ('x')
PUNCTUATION ('.')
NUMBER ('1')
PUNCTUATION ('.')
IDENTIFIER ('a')
CHARACTER ('=')
NUMBER ('12.2L')
WHITESPACE ('\n   ')
IDENTIFIER ('y')
PUNCTUATION ('.')
NUMBER ('2')
PUNCTUATION ('.')
IDENTIFIER ('b')
CHARACTER ('=')
NUMBER ('13.4')
WHITESPACE (' ')
LINE_COMMENT ('# comment')
WHITESPACE ('\n')
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
""")
  }

  fun testCpp() {
    val table = SyntaxTable()
    table.addKeyword1("->")
    doTest(table, "foo->bar", """
IDENTIFIER ('foo')
KEYWORD_1 ('->')
IDENTIFIER ('bar')
""")
  }

  fun testNumber() {
    doTest(createPropTable(), "1.23=1.24", """
NUMBER ('1.23')
CHARACTER ('=')
NUMBER ('1.24')
""")
  }

  fun testPostfix() {
    doTest(createPropTable(), "abc 1.2ltext", """
IDENTIFIER ('abc')
WHITESPACE (' ')
NUMBER ('1.2l')
IDENTIFIER ('text')
""")
  }

  fun testWeird() {
    doTest(createPropTable(), "test.1.", """
IDENTIFIER ('test')
PUNCTUATION ('.')
NUMBER ('1.')
""")
  }

  fun testParenths() {
    doTest(createPropTable(), "value(255)", """
KEYWORD_1 ('value')
CHARACTER ('(')
NUMBER ('255')
CHARACTER (')')
""")
  }

  fun testSpecialCharactersInKeywords() {
    val table = SyntaxTable()
    table.addKeyword1("a*")
    table.addKeyword1("b-c")
    table.addKeyword1(":")
    table.addKeyword2("d#")
    table.addKeyword2("e")
    table.addKeyword2("foo{}")
    table.addKeyword3("foldl")
    table.addKeyword3("foldl'")
    val text = "a* b-c d# e- e foo{} : foldl' foo"
    val expected = """
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
"""
    doTest(CustomFileTypeLexer(table), text, expected)
    doTest(CustomFileHighlighter(table).highlightingLexer, text, expected)
  }

  fun testWordsScanner() {
    val table = SyntaxTable()
    table.addKeyword1("a*")
    val scanner = IdTableBuilding.createCustomFileTypeScanner(table)
    val words = mutableListOf<String>()
    val text = "a* b-c d# e$ foo{}"
    val expectedWords = listOf("a", "b", "c", "d", "e$", "foo")

    scanner.processWords(text) { w: WordOccurrence ->
      words.add(w.baseText.subSequence(w.start, w.end).toString())
      true
    }
    assertEquals(expectedWords, words)

    // words searched by find usages should be the same as words produced by word scanner
    assertEquals(expectedWords, StringUtil.getWordsIn(text))
  }

  fun `test quote block comment`() {
    val table = SyntaxTable()
    table.startComment = "\""
    table.endComment = "x"
    doTest(table, "\"axa", """
MULTI_LINE_COMMENT ('"ax')
IDENTIFIER ('a')
""")
  }

  fun testPlainText() {
    doTest(
      PlainTextSyntaxHighlighterFactory.createPlainTextLexer(), "ab.@c  (<def>)", """
CHARACTER ('ab.@c')
WHITESPACE ('  ')
L_PARENTH ('(')
L_BROCKET ('<')
CHARACTER ('def')
R_BROCKET ('>')
R_PARENTH (')')
""")
  }

  fun `test hex literals`() {
    val table = SyntaxTable()
    table.hexPrefix = "0y"
    doTest(table, "1 0yabc0", """
NUMBER ('1')
WHITESPACE (' ')
NUMBER ('0yabc0')
""")
  }

  fun testKeywordLexerPerformance() {
    val count = 3000
    val keywords = mutableListOf<String>()
    for (i in 0 until count) {
      val start = ('a'.code + (i % 7)).toInt()
      keywords.add(start.toString().repeat(i))
    }
    val table = SyntaxTable()
    for (s in keywords) {
      table.addKeyword1(s)
    }

    var text = StringBuilder()
    for (i in 0..10) {
      keywords.shuffle()
      for (s in keywords.subList(0, 10)) {
        text.append("$s\n")
      }
    }

    Benchmark.newBenchmark(name) {
      val charAts = IntRef()
      LexerTestCase.printTokens(countingCharSequence(text, charAts), 0, CustomFileTypeLexer(table))
      assertTrue(charAts.get() < text.length * 4)
    }.start()
  }

  private fun countingCharSequence(text: CharSequence, charAts: IntRef): CharSequence {
    return object : CharSequence {
      override val length: Int
        get() = text.length

      override fun get(index: Int): Char {
        charAts.inc()
        return text[index]
      }

      override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return countingCharSequence(text.subSequence(startIndex, endIndex), charAts)
      }
    }
  }
}