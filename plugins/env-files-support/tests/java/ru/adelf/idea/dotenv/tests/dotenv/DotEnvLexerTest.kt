package ru.adelf.idea.dotenv.tests.dotenv

import ru.adelf.idea.dotenv.tests.DotEnvFileBasedTestCase

class DotEnvLexerTest : DotEnvFileBasedTestCase() {

  fun testLexerComments() = doLexerTest()

  fun testLexerProperties() = doLexerTest()

  fun testLexerQuotes() = doLexerTest()

  fun testLexerNestedVariables() = doLexerTest()

  fun testLexerCompletionTokens() = doLexerTest()

  fun testLexerAllowsEmptyNestedVariables() = doLexerTest()

  fun testLexerAllowsNonGrammaticalDollarSymbols() = doLexerTest()

}