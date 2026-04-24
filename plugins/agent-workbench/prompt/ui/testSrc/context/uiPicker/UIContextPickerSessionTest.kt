// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context.uiPicker

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.BalloonLayout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel

@TestApplication
class UIContextPickerSessionTest {
  @Test
  fun findFrameUnderScreenLocationReturnsFrameContainingCursor() {
    val firstFrame = testFrame(JPanel())
    val secondFrame = testFrame(JPanel())
    val bounds = mapOf(
      firstFrame.component to Rectangle(0, 0, 200, 200),
      secondFrame.component to Rectangle(250, 0, 200, 200),
    )

    val frame = findFrameUnderScreenLocation(listOf(firstFrame, secondFrame), Point(300, 50), boundsOnScreen = { component -> bounds[component] })

    assertThat(frame).isSameAs(secondFrame)
  }

  @Test
  fun findFrameUnderScreenLocationPrefersLaterFrameWhenBoundsOverlap() {
    val firstFrame = testFrame(JPanel())
    val secondFrame = testFrame(JPanel())
    val overlappingBounds = Rectangle(0, 0, 200, 200)

    val frame = findFrameUnderScreenLocation(listOf(firstFrame, secondFrame), Point(50, 50), boundsOnScreen = {
      when (it) {
        firstFrame.component, secondFrame.component -> overlappingBounds
        else -> null
      }
    })

    assertThat(frame).isSameAs(secondFrame)
  }

  @Test
  fun findFrameUnderScreenLocationPrefersFocusedFrameWhenBoundsOverlap() {
    val firstFrame = testFrame(JPanel())
    val secondFrame = testFrame(JPanel())
    val overlappingBounds = Rectangle(0, 0, 200, 200)

    val frame = findFrameUnderScreenLocation(
      listOf(firstFrame, secondFrame),
      Point(50, 50),
      boundsOnScreen = { overlappingBounds },
      isPreferred = { it === firstFrame },
    )

    assertThat(frame).isSameAs(firstFrame)
  }

  @Test
  fun findFrameUnderScreenLocationReturnsNullWhenNoFrameContainsCursor() {
    val frame = testFrame(JPanel())

    val result = findFrameUnderScreenLocation(listOf(frame), Point(500, 500), boundsOnScreen = { Rectangle(0, 0, 200, 200) })

    assertThat(result).isNull()
  }

  private fun testFrame(component: JComponent): IdeFrame {
    return object : IdeFrame {
      override fun getStatusBar(): StatusBar? = null

      override fun suggestChildFrameBounds(): Rectangle = Rectangle()

      override fun getProject(): Project? = null

      override fun setFrameTitle(title: String) {}

      override fun getComponent(): JComponent = component

      override fun getBalloonLayout(): BalloonLayout? = null
    }
  }
}
