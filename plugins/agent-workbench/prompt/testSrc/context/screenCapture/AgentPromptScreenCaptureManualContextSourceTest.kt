// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context.screenCapture

import com.intellij.agent.workbench.prompt.context.AgentPromptScreenshotContextItem.buildScreenshotContextItem
import com.intellij.agent.workbench.sessions.core.prompt.number
import com.intellij.agent.workbench.sessions.core.prompt.objOrNull
import com.intellij.agent.workbench.sessions.core.prompt.string
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class AgentPromptScreenCaptureManualContextSourceTest {
  @Test
  fun normalizeSelectionBoundsOrdersCorners() {
    val selection = normalizeSelectionBounds(Point(20, 30), Point(5, 10))

    assertThat(selection).isEqualTo(Rectangle(5, 10, 15, 20))
  }

  @Test
  fun captureSelectionFromSnapshotsStitchesMultipleScreens() {
    val left = filledImage(2, 2, Color.RED)
    val right = filledImage(2, 2, Color.BLUE)

    val screenshot = captureSelectionFromSnapshots(
      snapshots = listOf(
        ScreenSnapshot(Rectangle(0, 0, 2, 2), left),
        ScreenSnapshot(Rectangle(2, 0, 2, 2), right),
      ),
      selection = Rectangle(1, 0, 3, 2),
    )

    assertThat(screenshot).isNotNull
    assertThat(screenshot!!.width).isEqualTo(3)
    assertThat(screenshot.height).isEqualTo(2)
    assertThat(Color(screenshot.getRGB(0, 0), true)).isEqualTo(Color.RED)
    assertThat(Color(screenshot.getRGB(1, 0), true)).isEqualTo(Color.BLUE)
    assertThat(Color(screenshot.getRGB(2, 0), true)).isEqualTo(Color.BLUE)
  }

  @Test
  fun buildScreenshotContextItemWritesScreenshotPayload() {
    val item = buildScreenshotContextItem(
      title = "Screen Capture",
      screenshot = filledImage(3, 4, Color.GREEN),
      sourceId = "manual.screen.capture",
      source = "manualScreenCapture",
      tempFilePrefix = "screen-capture-test-",
    )

    val payload = item.payload.objOrNull()
    val filePath = checkNotNull(payload?.string("filePath"))

    assertThat(item.title).isEqualTo("Screen Capture")
    assertThat(payload.string("type")).isEqualTo("screenshot")
    assertThat(payload.number("width")).isEqualTo("3")
    assertThat(payload.number("height")).isEqualTo("4")
    assertThat(filePath).isNotBlank()
    assertThat(Files.exists(Path.of(filePath))).isTrue()

    Files.deleteIfExists(Path.of(filePath))
  }

  private fun filledImage(width: Int, height: Int, color: Color): BufferedImage {
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { image ->
      val graphics = image.createGraphics()
      try {
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
      }
      finally {
        graphics.dispose()
      }
    }
  }
}
