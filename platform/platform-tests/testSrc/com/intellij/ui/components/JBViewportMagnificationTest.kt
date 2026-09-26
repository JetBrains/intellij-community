// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.application.UI
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.Point
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * What a [JBViewport] paints while a magnification gesture runs.
 *
 * During the gesture the viewport paints a snapshot of itself, scaled, instead of its content ([ZoomingDelegate]). The snapshot
 * must show what the live paint shows. A non-opaque viewport paints nothing where its view is transparent, and the live paint
 * shows the nearest opaque ancestor's background there. The image editor opened on a transparent background is such a
 * viewport: its snapshot was black around the image, and a pinch flicked the background between light and black.
 *
 * The scene is built the way that editor builds it: the viewport is in a non-opaque scroll pane, and the scroll pane is in an
 * opaque panel. The scroll pane has a background of its own, which nothing paints, so a reading of it names a snapshot laid on
 * the viewport's immediate parent rather than on the nearest opaque ancestor.
 *
 * Every reading is taken in a place where the view paints nothing: the four corners of the view are transparent, and only a
 * small square in the middle is painted. Each colour in the scene is distinct, and none is black or white, so a reading names
 * the component it came from.
 */
@TestApplication
internal class JBViewportMagnificationTest {

  @Test
  fun `a non-opaque viewport shows its nearest opaque ancestor's background while it is magnified`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UI) {
      val scene = Scene(viewportOpaque = false)

      assertEquals(
        listOf(PARENT, PARENT, PARENT, PARENT).map(::hex),
        scene.readings().map(::hex),
        "live, magnified at its corner, scaled down at its corner, and scaled down inside the snapshot",
      )
    }

  @Test
  fun `an opaque viewport paints as before while it is magnified`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UI) {
      // An opaque viewport paints its own background everywhere, and the snapshot shows it. Around a snapshot scaled down,
      // the view's background is painted, as it always was.
      val scene = Scene(viewportOpaque = true)

      assertEquals(
        listOf(VIEWPORT, VIEWPORT, VIEW, VIEWPORT).map(::hex),
        scene.readings().map(::hex),
        "live, magnified at its corner, scaled down at its corner, and scaled down inside the snapshot",
      )
    }

  /**
   * An opaque parent, a non-opaque scroll pane in it, and the scroll pane's viewport holding a view whose only painted area is
   * a square in its middle.
   */
  private class Scene(viewportOpaque: Boolean) {
    private val parent = JPanel(BorderLayout()).apply {
      isOpaque = true
      background = Color(PARENT)
    }
    private val viewport: JBViewport

    init {
      val view = JPanel(null).apply {
        isOpaque = false
        background = Color(VIEW)
        add(JPanel().apply {
          isOpaque = true
          background = Color(CONTENT)
          setBounds(CONTENT_AT, CONTENT_AT, CONTENT_SIDE, CONTENT_SIDE)
        })
        setSize(SIDE, SIDE)
        preferredSize = size
        putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, Magnificator { _, at -> at })
      }
      val scrollPane = JBScrollPane(view).apply {
        isOpaque = false
        background = Color(SCROLL_PANE)
        border = JBUI.Borders.empty()
      }
      viewport = scrollPane.viewport as JBViewport
      viewport.isOpaque = viewportOpaque
      viewport.background = Color(VIEWPORT)
      parent.add(scrollPane, BorderLayout.CENTER)
      parent.setSize(SIDE, SIDE)
      parent.layOutTree()
    }

    /**
     * The viewport's pixel at [CORNER] live, then while magnified by 0.5, then at [CORNER] and at [INSIDE_SNAPSHOT] while
     * magnified by -0.5, which scales the snapshot down.
     *
     * Magnified by 0.5 the snapshot is 1.5 times larger around the viewport's middle, so [CORNER] shows the snapshot's
     * transparent corner. Scaled down, [CORNER] is outside the snapshot and [INSIDE_SNAPSHOT] is inside it, on its transparent
     * corner.
     */
    fun readings(): List<Int> {
      val live = pixel(CORNER)
      val middle = Point(viewport.width / 2, viewport.height / 2)

      viewport.magnificationStarted(middle)
      viewport.magnify(0.5)
      val magnified = pixel(CORNER)
      viewport.magnificationFinished(0.0)

      viewport.magnificationStarted(middle)
      viewport.magnify(-0.5)
      val scaledDown = pixel(CORNER)
      val insideSnapshot = pixel(INSIDE_SNAPSHOT)
      viewport.magnificationFinished(0.0)

      return listOf(live, magnified, scaledDown, insideSnapshot)
    }

    /** The colour at [inViewport] when the parent paints, as the screen would show it. */
    private fun pixel(inViewport: Point): Int {
      val image = BufferedImage(parent.width, parent.height, BufferedImage.TYPE_INT_ARGB)
      val g = image.createGraphics()
      try {
        parent.paint(g)
      }
      finally {
        g.dispose()
      }
      val at = SwingUtilities.convertPoint(viewport, inViewport, parent)
      return image.getRGB(at.x, at.y) and RGB
    }

    private fun Container.layOutTree() {
      doLayout()
      components.filterIsInstance<Container>().forEach { it.layOutTree() }
    }
  }

  private companion object {
    private const val PARENT = 0x3C7A5A
    private const val SCROLL_PANE = 0xB0A040
    private const val VIEWPORT = 0x2060A0
    private const val VIEW = 0x804090
    private const val CONTENT = 0xC03020

    private const val SIDE = 100
    private const val CONTENT_AT = 40
    private const val CONTENT_SIDE = 20

    /** Outside the painted square both live and magnified by 0.5, which maps it to the snapshot's point (20, 20). */
    private val CORNER = Point(5, 5)

    /** Inside the snapshot scaled down by -0.5, which starts at (16, 16), and on its transparent corner. */
    private val INSIDE_SNAPSHOT = Point(20, 20)

    private const val RGB = 0xFFFFFF

    private fun hex(rgb: Int): String = "%06x".format(rgb)
  }
}
