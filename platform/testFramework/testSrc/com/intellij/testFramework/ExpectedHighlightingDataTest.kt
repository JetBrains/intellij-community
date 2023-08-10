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

class ExpectedHighlightingDataTest {
  private val TYPES = mapOf(
    "err" to ExpectedHighlightingData.ExpectedHighlightingSet(HighlightSeverity.ERROR, false, true),
    "warn" to ExpectedHighlightingData.ExpectedHighlightingSet(HighlightSeverity.WARNING, false, true),
    "eol_err" to ExpectedHighlightingData.ExpectedHighlightingSet(HighlightSeverity.ERROR, true, true))

  @Test fun empty(): Unit = doTest("text", emptyList(), "text")

  @Test fun fullLength(): Unit =
    doTest("text", listOf(error(0, 4, "_")), """<err descr="_">text</err>""")

  @Test fun sequential(): Unit =
    doTest("_my text_",
           listOf(error(1, 3, "1"), error(4, 8, "2")),
           """_<err descr="1">my</err> <err descr="2">text</err>_""")

  @Test fun simpleNested(): Unit =
    doTest("[(nested)]",
           listOf(error(1, 9, "1"), error(2, 8, "2")),
           """[<err descr="1">(<err descr="2">nested</err>)</err>]""")

  @Test fun deepNested(): Unit =
    doTest("m1(m2(m3(m4(x))))",
           listOf(error(3, 16, "m1"), error(6, 15, "m2"), error(9, 14, "m3"), error(12, 13, "m4")),
           """m1(<err descr="m1">m2(<err descr="m2">m3(<err descr="m3">m4(<err descr="m4">x</err>)</err>)</err>)</err>)""")

  @Test fun sameStart(): Unit =
    doTest("same start",
           listOf(error(0, 4, "1"), error(0, 10, "2")),
           """<err descr="2"><err descr="1">same</err> start</err>""")

  @Test fun sameEnd(): Unit =
    doTest("same end",
           listOf(error(0, 8, "1"), error(5, 8, "2")),
           """<err descr="1">same <err descr="2">end</err></err>""")

  @Test fun sameBothBounds(): Unit =
    doTest("same",
           listOf(error(0, 4, "-"), warning(0, 4, "-")),
           """<err descr="-"><warn descr="-">same</warn></err>""")

  @Test fun samePriority(): Unit =
    doTest("_same_",
           listOf(warning(1, 5, "1"), warning(1, 5, "2")),
           """_<warn descr="1"><warn descr="2">same</warn></warn>_""")

  @Test fun twoNests(): Unit =
    doTest("(two nests)",
           listOf(error(0, 11, "-"), error(1, 4, "1"), error(5, 10, "2")),
           """<err descr="-">(<err descr="1">two</err> <err descr="2">nests</err>)</err>""")

  @Test fun realistic(): Unit =
    doTest("one and (two nests)",
           listOf(error(4, 7, "-"), error(8, 19, "-"), error(9, 12, "1"), error(13, 18, "2")),
           """one <err descr="-">and</err> <err descr="-">(<err descr="1">two</err> <err descr="2">nests</err>)</err>""")

  @Test fun twoEOLs(): Unit =
    doTest("text\nmore text",
           listOf(eolError(4, 4, "1"), eolError(4, 4, "2")),
           """
             text<eol_err descr="2"></eol_err><eol_err descr="1"></eol_err>
             more text""".trimIndent())

  @Test fun eolAfterError(): Unit =
    doTest("some error\nmore text",
           listOf(error(5, 10, "1"), eolError(10, 10, "2")),
           """
             some <err descr="1">error</err><eol_err descr="2"></eol_err>
             more text""".trimIndent())

  @Test fun consecutiveNests(): Unit =
    doTest(" ab ",
           listOf(error(1, 2, "a1"), error(1, 2, "a2"), error(2, 3, "b1"), error(2, 3, "b2")),
           """ <err descr="a1"><err descr="a2">a</err></err><err descr="b1"><err descr="b2">b</err></err> """)

  @Test fun zeroLengthAtZeroOffset(): Unit =
    doTest("text", listOf(error(0, 0, "_")), """<err descr="_"></err>text""")

  private fun doTest(original: String, highlighting: Collection<HighlightInfo>, expected: String) =
    assertEquals(expected, ExpectedHighlightingData.composeText(TYPES, highlighting, original))

  private fun error(start: Int, end: Int, description: String) =
    HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(start, end).descriptionAndTooltip(description).createUnconditionally()

  private fun warning(start: Int, end: Int, description: String) =
    HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(start, end).descriptionAndTooltip(description).createUnconditionally()

  private fun eolError(start: Int, end: Int, description: String) =
    HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(start, end).description(description).endOfLine().createUnconditionally()
}