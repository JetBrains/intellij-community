// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.uploader

import com.intellij.internal.statistic.StatisticsEventLogUtil
import com.intellij.testFramework.UsefulTestCase
import org.jdom.Element
import org.jdom.JDOMException
import java.io.ByteArrayInputStream


class EventLogUtilTest : UsefulTestCase() {

  private fun doTestMergeArray(a1: Array<String>, a2: Array<String>, expected: Array<String>) {
    val actual = StatisticsEventLogUtil.mergeArrays(a1, a2)

    assertEquals(expected.size, actual.size)
    for ((index, _) in actual.withIndex()) {
      assertEquals(expected[index], actual[index])
    }
  }

  private fun doTestParseXml(text: String, expected: Element) {
    val stream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
    val actual = StatisticsEventLogUtil.parseXml(stream)
    assertElementsEquals(expected, actual)
  }

  private fun doTestParseInvalidXml(text: String) {
    val stream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
    try {
      StatisticsEventLogUtil.parseXml(stream)
      //should be unreachable
      assertFalse(true)
    }
    catch (e: JDOMException) {
      // ignore
    }
  }

  private fun doTestStringSplit(str: String, separator: Char, expected: List<String>) {
    val actual = StatisticsEventLogUtil.split(str, separator)
    assertEquals(expected, actual)
  }

  fun test_merge_empty_arrays() {
    doTestMergeArray(arrayOf(), arrayOf(), arrayOf())
  }

  fun test_merge_first_empty_array() {
    doTestMergeArray(arrayOf(), arrayOf("A"), arrayOf("A"))
  }

  fun test_merge_second_empty_array() {
    doTestMergeArray(arrayOf("A"), arrayOf(), arrayOf("A"))
  }

  fun test_merge_arrays() {
    doTestMergeArray(arrayOf("A"), arrayOf("B"), arrayOf("A", "B"))
  }

  fun test_merge_long_arrays() {
    doTestMergeArray(arrayOf("A", "B", "C"), arrayOf("D", "E", "F", "G"),
                     arrayOf("A", "B", "C", "D", "E", "F", "G"))
    doTestMergeArray(arrayOf("A", "B", "C", "X", "Y", "Z"), arrayOf("D", "E", "F", "G"),
                     arrayOf("A", "B", "C", "X", "Y", "Z", "D", "E", "F", "G"))
  }

  fun test_parse_valid_xml() {
    doTestParseXml(
      "<service url=\"http://127.0.0.1\" report=\"10\"/>",
      newElement("url" to "http://127.0.0.1", "report" to "10")
    )
  }

  fun test_parse_empty_xml() {
    doTestParseXml("<service />", newElement())
  }

  fun test_parse_valid_xml_with_ending_tags() {
    doTestParseXml(
      "<service url=\"http://127.0.0.1\" report=\"10\"></service>",
      newElement("url" to "http://127.0.0.1", "report" to "10")
    )
  }

  fun test_parse_invalid_xml_without_closing_tag() {
    doTestParseInvalidXml(
      "<service url=\"http://127.0.0.1\" report=\"10\">"
    )
  }

  fun test_parse_invalid_xml_trimmed() {
    doTestParseInvalidXml(
      "<service url=\"http://127.0.0.1\" report=\"10\"/"
    )
  }

  fun test_parse_invalid_without_closing_quote() {
    doTestParseInvalidXml(
      "<service url=\"http://127.0.0.1\" report=\"10/>"
    )
  }

  fun test_parse_invalid_xml() {
    doTestParseInvalidXml(
      "service"
    )
  }

  fun test_split_build_number() {
    doTestStringSplit("193.12345.124", '.', arrayListOf("193", "12345", "124"))
  }

  fun test_split_build_number_with_ending_dot() {
    doTestStringSplit("193.12345.", '.', arrayListOf("193", "12345"))
  }

  fun test_split_build_number_with_start_dot() {
    doTestStringSplit(".193.12345", '.', arrayListOf("193", "12345"))
  }

  fun test_split_build_number_without_dot() {
    doTestStringSplit("19312345", '.', arrayListOf("19312345"))
  }

  fun test_split_empty_string() {
    doTestStringSplit("", '.', arrayListOf())
  }

  fun test_split_only_separator_string() {
    doTestStringSplit(".", '.', arrayListOf())
  }

  fun test_split_files() {
    doTestStringSplit("/a/b/c:/d/e", ':', arrayListOf("/a/b/c", "/d/e"))
  }

  fun test_split_files_win() {
    doTestStringSplit("/a/b/c;/d/e", ';', arrayListOf("/a/b/c", "/d/e"))
  }

  private fun newElement(vararg attributes: Pair<String, String>): Element {
    val element = Element("service")
    for (attribute in attributes) {
      element.setAttribute(attribute.first, attribute.second)
    }
    return element
  }

  private fun assertElementsEquals(first: Element, second: Element) {
    assertEquals(first.name, second.name)
    val firstAttributes = first.attributes
    val secondAttributes = second.attributes
    assertEquals(firstAttributes.size, secondAttributes.size)

    for (firstAttr in firstAttributes) {
      val secondAttr = secondAttributes.find { it.name == firstAttr.name }
      assertNotNull(secondAttr)
      assertEquals(firstAttr.value, secondAttr!!.value)
    }

    val firstChildren = first.children
    val secondChildren = second.children
    assertEquals(firstChildren.size, secondChildren.size)

    for ((index, firstChild) in firstChildren.withIndex()) {
      assertElementsEquals(firstChild, secondChildren[index])
    }
  }
}
