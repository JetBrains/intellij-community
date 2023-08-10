/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package training.simple

import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import training.dsl.parseLessonSample

class SampleParserTest : UsefulTestCase() {
  fun testStartCaretTag1() {
    val sample = parseLessonSample("hello <caret>world")
    assertSameLines("hello world", sample.text)
    assertSame(6, sample.startOffset)
  }

  fun testStartCaretTag2() {
    val sample = parseLessonSample("<caret>At the start")
    assertSameLines("At the start", sample.text)
    assertSame(0, sample.startOffset)
  }

  fun testStartCaretTag3() {
    val sample = parseLessonSample("In the end<caret>")
    assertSameLines("In the end", sample.text)
    assertSame(10, sample.startOffset)
  }

  fun testSelectTag() {
    val sample = parseLessonSample("""
      hello <select>world
      next </select>line
    """.trimIndent())
    assertSameLines("""
      hello world
      next line
    """.trimIndent(), sample.text)
    assertNotNull(sample.selection)
    assertSame(17, sample.startOffset)
    assertSame(6, sample.selection?.first ?: -1)
    assertSame(17, sample.selection?.second ?: -1)
  }

  fun testMultipleTags() {
    val sample = parseLessonSample("a <caret id=2/>bb <select id=1>ccc</select> dddd <caret>eee")
    assertSameLines("a bb ccc dddd eee", sample.text)
    TestCase.assertEquals(14, sample.startOffset)
    TestCase.assertEquals(Pair(5, 8), sample.getPosition(1).selection)
    TestCase.assertEquals(2, sample.getPosition(2).startOffset)
  }

  fun testNoCaret() {
    val sample = parseLessonSample("hello world")
    assertSame(0, sample.startOffset)
  }
}
