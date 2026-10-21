// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

// Core specification: https://goessner.net/articles/JsonPath/
@TestApplication
class JsonPathGoessnerParserTest : JsonPathParsingTestCase("goessner") {
  @Test
  fun testDottedSegments() {
    parsing.doCodeTest("\$.store.book[0].title")
  }

  @Test
  fun testQuotedSegments() {
    parsing.doCodeTest("\$['store']['book'][0]['title']")
  }

  @Test
  fun testQuotedEscaping() {
    parsing.doCodeTest("\$['store \\' name']")
  }

  @Test
  fun testDottedSegmentsWithoutContext() {
    parsing.doCodeTest("x.store.book[0].title")
  }

  @Test
  fun testQuotedSegmentsWithoutContext() {
    parsing.doCodeTest("x['store']['book'][0]['title']")
  }

  @Test
  fun testWildcardIndexes() {
    parsing.doCodeTest("\$.store.book[*].author")
  }

  @Test
  fun testRecursiveDescent() {
    parsing.doCodeTest("\$..author")
  }

  @Test
  fun testWildcardEnding() {
    parsing.doCodeTest("\$.store.*")
  }

  @Test
  fun testRecursiveDescentWithPath() {
    parsing.doCodeTest("\$.store..price")
  }

  @Test
  fun testIndex() {
    parsing.doCodeTest("\$..book[2]")
  }

  @Test
  fun testLastExpression() {
    parsing.doCodeTest("\$..book[(@.length - 1)]")
  }

  @Test
  fun testLastIndex() {
    parsing.doCodeTest("\$..book[-1:]")
  }

  @Test
  fun testTwoIndexes() {
    parsing.doCodeTest("\$..book[0,1]")
  }

  @Test
  fun testSliceTo() {
    parsing.doCodeTest("\$..book[:2]")
  }

  @Test
  fun testSliceFrom() {
    parsing.doCodeTest("\$..book[2:]")
  }

  @Test
  fun testSliceWithStep() {
    parsing.doCodeTest("\$..book[0:3:1]")
  }

  @Test
  fun testFilterWithAttribute() {
    parsing.doCodeTest("\$..book[?(@.isbn)]")
  }

  @Test
  fun testFilterExpression() {
    parsing.doCodeTest("\$..book[?(@.price<10)]")
  }

  @Test
  fun testAllStructure() {
    parsing.doCodeTest("\$..*")
  }
}