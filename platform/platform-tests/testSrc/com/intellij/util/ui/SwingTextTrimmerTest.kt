// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.openapi.util.text.StringUtil
import junit.framework.TestCase
import java.awt.Container
import java.awt.Font

internal class SwingTextTrimmerTest : TestCase() {
  private val metrics = Container().getFontMetrics(Font(Font.DIALOG, Font.PLAIN, 10))
  private val debug = false

  private fun SwingTextTrimmer.assertEmpty(text: String?, width: Int) {
    assertTrue(trim(text, metrics, width).isEmpty())
  }

  private fun SwingTextTrimmer.testNull() {
    assertEmpty(null, 0)
    assertEmpty(null, Int.MIN_VALUE)
    assertEmpty(null, Int.MAX_VALUE)
  }

  private fun SwingTextTrimmer.test(text: String = "0123456789") {
    val width = metrics.stringWidth(text)
    for (i in 1 until width) {
      val trim = trim(text, metrics, i)
      assertFalse(text == trim)
      assertTrue(trim.isNotEmpty())
      if (debug && trim != StringUtil.ELLIPSIS && trim != StringUtil.THREE_DOTS) {
        println(i.toString() + " >= " + metrics.stringWidth(trim) + ": " + trim)
      }
    }
    assertEmpty(text, 0)
    assertEmpty(text, Int.MIN_VALUE)
    assertEquals(text, trim(text, metrics, width))
    assertEquals(text, trim(text, metrics, Int.MAX_VALUE))
  }


  fun testEllipsisAtLeft(): Unit = SwingTextTrimmer.ELLIPSIS_AT_LEFT.test()
  fun testEllipsisAtRight(): Unit = SwingTextTrimmer.ELLIPSIS_AT_RIGHT.test()
  fun testEllipsisInCenter(): Unit = SwingTextTrimmer.ELLIPSIS_IN_CENTER.test()

  fun testThreeDotsAtLeft(): Unit = SwingTextTrimmer.THREE_DOTS_AT_LEFT.test()
  fun testThreeDotsAtRight(): Unit = SwingTextTrimmer.THREE_DOTS_AT_RIGHT.test()
  fun testThreeDotsInCenter(): Unit = SwingTextTrimmer.THREE_DOTS_IN_CENTER.test()


  fun testEllipsisAtLeftEmpty(): Unit = SwingTextTrimmer.ELLIPSIS_AT_LEFT.test("")
  fun testEllipsisAtRightEmpty(): Unit = SwingTextTrimmer.ELLIPSIS_AT_RIGHT.test("")
  fun testEllipsisInCenterEmpty(): Unit = SwingTextTrimmer.ELLIPSIS_IN_CENTER.test("")

  fun testThreeDotsAtLeftEmpty(): Unit = SwingTextTrimmer.THREE_DOTS_AT_LEFT.test("")
  fun testThreeDotsAtRightEmpty(): Unit = SwingTextTrimmer.THREE_DOTS_AT_RIGHT.test("")
  fun testThreeDotsInCenterEmpty(): Unit = SwingTextTrimmer.THREE_DOTS_IN_CENTER.test("")


  fun testEllipsisAtLeftNull(): Unit = SwingTextTrimmer.ELLIPSIS_AT_LEFT.testNull()
  fun testEllipsisAtRightNull(): Unit = SwingTextTrimmer.ELLIPSIS_AT_RIGHT.testNull()
  fun testEllipsisInCenterNull(): Unit = SwingTextTrimmer.ELLIPSIS_IN_CENTER.testNull()

  fun testThreeDotsAtLeftNull(): Unit = SwingTextTrimmer.THREE_DOTS_AT_LEFT.testNull()
  fun testThreeDotsAtRightNull(): Unit = SwingTextTrimmer.THREE_DOTS_AT_RIGHT.testNull()
  fun testThreeDotsInCenterNull(): Unit = SwingTextTrimmer.THREE_DOTS_IN_CENTER.testNull()
}
