/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.HighlightSeverity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*
import java.util.Arrays.asList

class ExpectedHighlightingDataTest {
  private val TYPES = mapOf(
    "err" to ExpectedHighlightingData.ExpectedHighlightingSet(HighlightSeverity.ERROR, false, true),
    "warn" to ExpectedHighlightingData.ExpectedHighlightingSet(HighlightSeverity.WARNING, false, true),
    "eol_err" to ExpectedHighlightingData.ExpectedHighlightingSet(HighlightSeverity.ERROR, true, true))

  private val TEST_BUNDLE = object : ListResourceBundle() {
    private val CONTENTS: Array<Array<Any>> = arrayOf(
      arrayOf<Any>("single.value", "My text"),
      arrayOf<Any>("one.param.start", "{0}foo"),
      arrayOf<Any>("one.param.end", "foo{0}"),
      arrayOf<Any>("two.params", "A {0} B {1} C"),
      arrayOf<Any>("special.chars", "\"|'"),
      arrayOf<Any>("expected.lbrace.or.semicolon", "expected { or ;")
    )

    override fun getContents(): Array<Array<Any>> = CONTENTS
  }

  private val TEST_BUNDLE_2 = object : ListResourceBundle() {
    private val CONTENTS: Array<Array<Any>> = arrayOf(
      arrayOf<Any>("single.value.2", "My text"),
      arrayOf<Any>("another.single.value", "Another text")
    )

    override fun getContents(): Array<Array<Any>> = CONTENTS
  }

  @Test fun empty() = doTest("text", emptyList(), "text")

  @Test fun fullLength() =
    doTest("text", listOf(error(0, 4, "_")), """<err descr="_">text</err>""")

  @Test fun sequential() =
    doTest("_my text_",
           asList(error(1, 3, "1"), error(4, 8, "2")),
           """_<err descr="1">my</err> <err descr="2">text</err>_""")

  @Test fun simpleNested() =
    doTest("[(nested)]",
           asList(error(1, 9, "1"), error(2, 8, "2")),
           """[<err descr="1">(<err descr="2">nested</err>)</err>]""")

  @Test fun deepNested() =
    doTest("m1(m2(m3(m4(x))))",
           asList(error(3, 16, "m1"), error(6, 15, "m2"), error(9, 14, "m3"), error(12, 13, "m4")),
           """m1(<err descr="m1">m2(<err descr="m2">m3(<err descr="m3">m4(<err descr="m4">x</err>)</err>)</err>)</err>)""")

  @Test fun sameStart() =
    doTest("same start",
           asList(error(0, 4, "1"), error(0, 10, "2")),
           """<err descr="2"><err descr="1">same</err> start</err>""")

  @Test fun sameEnd() =
    doTest("same end",
           asList(error(0, 8, "1"), error(5, 8, "2")),
           """<err descr="1">same <err descr="2">end</err></err>""")

  @Test fun sameBothBounds() =
    doTest("same",
           asList(error(0, 4, "-"), warning(0, 4, "-")),
           """<err descr="-"><warn descr="-">same</warn></err>""")

  @Test fun samePriority() =
    doTest("_same_",
           asList(warning(1, 5, "1"), warning(1, 5, "2")),
           """_<warn descr="1"><warn descr="2">same</warn></warn>_""")

  @Test fun twoNests() =
    doTest("(two nests)",
           asList(error(0, 11, "-"), error(1, 4, "1"), error(5, 10, "2")),
           """<err descr="-">(<err descr="1">two</err> <err descr="2">nests</err>)</err>""")

  @Test fun realistic() =
    doTest("one and (two nests)",
           asList(error(4, 7, "-"), error(8, 19, "-"), error(9, 12, "1"), error(13, 18, "2")),
           """one <err descr="-">and</err> <err descr="-">(<err descr="1">two</err> <err descr="2">nests</err>)</err>""")

  @Test fun twoEOLs() =
    doTest("text\nmore text",
           asList(eolError(4, 4, "1"), eolError(4, 4, "2")),
           """
             text<eol_err descr="2"></eol_err><eol_err descr="1"></eol_err>
             more text""".trimIndent())

  @Test fun eolAfterError() =
    doTest("some error\nmore text",
           asList(error(5, 10, "1"), eolError(10, 10, "2")),
           """
             some <err descr="1">error</err><eol_err descr="2"></eol_err>
             more text""".trimIndent())

  @Test fun consecutiveNests() =
    doTest(" ab ",
           asList(error(1, 2, "a1"), error(1, 2, "a2"), error(2, 3, "b1"), error(2, 3, "b2")),
           """ <err descr="a1"><err descr="a2">a</err></err><err descr="b1"><err descr="b2">b</err></err> """)

  @Test fun zeroLengthAtZeroOffset() =
    doTest("text", listOf(error(0, 0, "_")), """<err descr="_"></err>text""")

  @Test fun bundleMsgNoParams() =
    doTest("text", listOf(error(0, 4, "My text")), """<err bundleMsg="single.value">text</err>""", TEST_BUNDLE)

  @Test fun bundleMsgOneParamStart() =
    doTest("text", listOf(error(0, 4, "xfoo")), """<err bundleMsg="one.param.start|x">text</err>""", TEST_BUNDLE)

  @Test fun bundleMsgOneParamEnd() =
    doTest("text", listOf(error(0, 4, "foox")), """<err bundleMsg="one.param.end|x">text</err>""", TEST_BUNDLE)

  @Test fun bundleMsgOneParamMultipleMatch() =
    doTest("text", listOf(error(0, 4, "foo")), """<err descr="foo">text</err>""", TEST_BUNDLE)

  @Test fun bundleMsgTwoParams() =
    doTest("text", listOf(error(0, 4, "A 1 B 2 C")), """<err bundleMsg="two.params|1|2">text</err>""", TEST_BUNDLE)

  @Test fun bundleMsgSpecialChars() =
    doTest("text", listOf(error(0, 4, "\"|'")), """<err bundleMsg="special.chars">text</err>""", TEST_BUNDLE, TEST_BUNDLE_2)

  @Test fun bundleMsgUniqueFromTwoBundles() =
    doTest("text", listOf(error(0, 4, "My text")), """<err descr="My text">text</err>""", TEST_BUNDLE, TEST_BUNDLE_2)

  @Test fun bundleMsgMultipleMatchFromTwoBundles() =
    doTest("text", listOf(error(0, 4, "Another text")), """<err bundleMsg="another.single.value">text</err>""", TEST_BUNDLE, TEST_BUNDLE_2)

  @Test fun bundleMessageUnclosedBrace() =
    doTest("text", listOf(error(0, 4, "expected { or ;")), """<err bundleMsg="expected.lbrace.or.semicolon">text</err>""", TEST_BUNDLE)


  private fun doTest(original: String, highlighting: Collection<HighlightInfo>, expected: String, vararg bundles: ResourceBundle) =
    assertEquals(expected, ExpectedHighlightingData.composeText(TYPES, highlighting, original, *bundles))

  private fun error(start: Int, end: Int, description: String) =
    HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(start, end).descriptionAndTooltip(description).createUnconditionally()

  private fun warning(start: Int, end: Int, description: String) =
    HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(start, end).descriptionAndTooltip(description).createUnconditionally()

  private fun eolError(start: Int, end: Int, description: String) =
    HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(start, end).description(description).endOfLine().createUnconditionally()
}