// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath

// Core specification: https://goessner.net/articles/JsonPath/
class JsonPathGoessnerParserTest : JsonPathParsingTestCase("goessner") {
  fun testDottedSegments() {
    doCodeTest("\$.store.book[0].title")
  }

  fun testQuotedSegments() {
    doCodeTest("\$['store']['book'][0]['title']")
  }

  fun testQuotedEscaping() {
    doCodeTest("\$['store \\' name']")
  }

  fun testDottedSegmentsWithoutContext() {
    doCodeTest("x.store.book[0].title")
  }

  fun testQuotedSegmentsWithoutContext() {
    doCodeTest("x['store']['book'][0]['title']")
  }

  fun testWildcardIndexes() {
    doCodeTest("\$.store.book[*].author")
  }

  fun testRecursiveDescent() {
    doCodeTest("\$..author")
  }

  fun testWildcardEnding() {
    doCodeTest("\$.store.*")
  }

  fun testRecursiveDescentWithPath() {
    doCodeTest("\$.store..price")
  }

  fun testIndex() {
    doCodeTest("\$..book[2]")
  }

  fun testLastExpression() {
    doCodeTest("\$..book[(@.length - 1)]")
  }

  fun testLastIndex() {
    doCodeTest("\$..book[-1:]")
  }

  fun testTwoIndexes() {
    doCodeTest("\$..book[0,1]")
  }

  fun testSliceTo() {
    doCodeTest("\$..book[:2]")
  }

  fun testSliceFrom() {
    doCodeTest("\$..book[2:]")
  }

  fun testSliceWithStep() {
    doCodeTest("\$..book[0:3:1]")
  }

  fun testFilterWithAttribute() {
    doCodeTest("\$..book[?(@.isbn)]")
  }

  fun testFilterExpression() {
    doCodeTest("\$..book[?(@.price<10)]")
  }

  fun testAllStructure() {
    doCodeTest("\$..*")
  }
}