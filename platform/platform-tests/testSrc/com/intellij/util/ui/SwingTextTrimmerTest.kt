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


  fun testEllipsisAtLeft() = SwingTextTrimmer.ELLIPSIS_AT_LEFT.test()
  fun testEllipsisAtRight() = SwingTextTrimmer.ELLIPSIS_AT_RIGHT.test()
  fun testEllipsisInCenter() = SwingTextTrimmer.ELLIPSIS_IN_CENTER.test()

  fun testThreeDotsAtLeft() = SwingTextTrimmer.THREE_DOTS_AT_LEFT.test()
  fun testThreeDotsAtRight() = SwingTextTrimmer.THREE_DOTS_AT_RIGHT.test()
  fun testThreeDotsInCenter() = SwingTextTrimmer.THREE_DOTS_IN_CENTER.test()


  fun testEllipsisAtLeftEmpty() = SwingTextTrimmer.ELLIPSIS_AT_LEFT.test("")
  fun testEllipsisAtRightEmpty() = SwingTextTrimmer.ELLIPSIS_AT_RIGHT.test("")
  fun testEllipsisInCenterEmpty() = SwingTextTrimmer.ELLIPSIS_IN_CENTER.test("")

  fun testThreeDotsAtLeftEmpty() = SwingTextTrimmer.THREE_DOTS_AT_LEFT.test("")
  fun testThreeDotsAtRightEmpty() = SwingTextTrimmer.THREE_DOTS_AT_RIGHT.test("")
  fun testThreeDotsInCenterEmpty() = SwingTextTrimmer.THREE_DOTS_IN_CENTER.test("")


  fun testEllipsisAtLeftNull() = SwingTextTrimmer.ELLIPSIS_AT_LEFT.testNull()
  fun testEllipsisAtRightNull() = SwingTextTrimmer.ELLIPSIS_AT_RIGHT.testNull()
  fun testEllipsisInCenterNull() = SwingTextTrimmer.ELLIPSIS_IN_CENTER.testNull()

  fun testThreeDotsAtLeftNull() = SwingTextTrimmer.THREE_DOTS_AT_LEFT.testNull()
  fun testThreeDotsAtRightNull() = SwingTextTrimmer.THREE_DOTS_AT_RIGHT.testNull()
  fun testThreeDotsInCenterNull() = SwingTextTrimmer.THREE_DOTS_IN_CENTER.testNull()
}
