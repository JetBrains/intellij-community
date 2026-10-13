// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

// Behavior compatible with com.jayway.jsonpath
@TestApplication
class JsonPathJaywayParserTest : JsonPathParsingTestCase("jayway") {
  @Test
  fun testProgrammaticFilterPlaceholder() {
    parsing.doCodeTest("\$.programmatic_filter[?]") // ? will be replaced by programmatic filter at runtime
  }

  @Test
  fun testFunctionWithArgumentExpression() {
    parsing.doCodeTest("\$.sum(\$..timestamp)")
  }

  @Test
  fun testFunctionWithMultipleArgs() {
    parsing.doCodeTest("\$.max(\$..timestamp.avg(), 100)")
  }

  @Test
  fun testFunctionOnTopLevel() {
    parsing.doCodeTest("concat(\"/\", \$.key)")
  }

  @Test
  fun testFilterWithComparison() {
    parsing.doCodeTest("\$.batches.results[?(@.values.length() >= \$.batches.minBatchSize)].values.avg()")
  }

  @Test
  fun testNestedFunctionCall() {
    parsing.doCodeTest("\$.avg(\$.numbers.min(), \$.numbers.max())")
  }

  @Test
  fun testFunctionLiteralArg() {
    parsing.doCodeTest("\$.sum(50)")
  }

  @Test
  fun testStringLiteralsConcat() {
    parsing.doCodeTest("\$.text.concat(\"-\", \"some\")")
  }

  @Test
  fun testRegexMatch() {
    parsing.doCodeTest("\$.text[?(@ =~ /9.*9/)]")
  }

  @Test
  fun testRegexCaseInsensitive() {
    parsing.doCodeTest("\$.data_text[?(@ =~ /test/i)]")
  }

  @Test
  fun testRegexWithSlashes() {
    parsing.doCodeTest("\$.regex[?(@ =~ /^\\w+\$/U)]")
  }

  @Test
  fun testRegexWithEscapedSlash() {
    parsing.doCodeTest("\$[?(@ =~ /\\/|x/)]")
  }

  @Test
  fun testMultipleQuotedPaths() {
    parsing.doCodeTest("\$['a', 'x']")
  }

  @Test
  fun testMixedSegmentTypes() {
    parsing.doCodeTest("\$['a', 'c'].v")
  }

  @Test
  fun testDotBeforeQuotedSegment() {
    parsing.doCodeTest("\$.['store'].['book'][0]")
  }

  @Test
  fun testQuotedSegmentWithFilter() {
    parsing.doCodeTest("\$['a', 'c'][?(@.flag)].v")
  }

  @Test
  fun testQuotedSegmentInTheMiddle() {
    parsing.doCodeTest("\$.x[*]['a', 'c'].v")
  }

  @Test
  fun testDashInsideIdentifier() {
    parsing.doCodeTest("\$.int-max-property")
  }

  @Test
  fun testAndOperator() {
    parsing.doCodeTest("\$.text[?(@.name == 'name' && $.length() > 2)]")
  }

  @Test
  fun testOrOperator() {
    parsing.doCodeTest("\$.text[?(@.name == 'name' || $.length() < 2)]")
  }

  @Test
  fun testSubsequentArrayIndexes() {
    parsing.doCodeTest("\$..[2][3]")
  }

  @Test
  fun testScanWithFilter() {
    parsing.doCodeTest("\$..*[?(@.length() > 5)]")
  }

  @Test
  fun testMultiPropsInTheMiddle() {
    parsing.doCodeTest("\$[*][*]['a', 'c'].v")
  }

  @Test
  fun testInOperatorUpperCase() {
    parsing.doCodeTest("$[?(@.a IN @.b)]")
  }

  @Test
  fun testInOperatorLowerCase() {
    parsing.doCodeTest("$[?('demo' in @.array)]")
  }

  @Test
  fun testEmptyOperatorWithConstant() {
    parsing.doCodeTest("$[?(@.array empty true)]")
  }

  @Test
  fun testEmptyOperatorWithExpression() {
    parsing.doCodeTest("$[?(@.array empty @.flag)]")
  }

  @Test
  fun testArrayLiterals() {
    parsing.doCodeTest("\$.store.bicycle[?(@.gears == [23, 50])]")
  }

  @Test
  fun testArrayLiteralsFromBothSides() {
    parsing.doCodeTest("\$.bicycle[?([1, 2] contains [1])]")
  }

  @Test
  fun testObjectLiterals() {
    parsing.doCodeTest("\$.store.bicycle[?(@.extra == {\"x\":0})]")
  }

  @Test
  fun testComplexObjectLiterals() {
    parsing.doCodeTest("\$.store.bicycle[?(@.extra == { 'x': [{}, {'key' : 'value'}] })]")
  }

  @Test
  fun testEqOperators() {
    parsing.doCodeTest("@.demo[?(@.a == 1 && @.b === 'x')]")
  }

  @Test
  fun testNeOperators() {
    parsing.doCodeTest("@.demo[?(@.a != 2 && @.b !== 'x')]")
  }

  @Test
  fun testIndexWithoutContext() {
    parsing.doCodeTest("[0]")
  }

  @Test
  fun testWildcardIndex() {
    parsing.doCodeTest("*[0]")
  }

  @Test
  fun testQuotedPathAfterIndex() {
    parsing.doCodeTest("[0][\"id\"]")
  }

  @Test
  fun testDotAfterIndex() {
    parsing.doCodeTest("[0].id")
  }

  @Test
  fun testRepeatedWildcard() {
    parsing.doCodeTest("**")
  }

  @Test
  fun testRepeatedWildcardInTheMiddle() {
    parsing.doCodeTest("[1].**.created")
  }
}