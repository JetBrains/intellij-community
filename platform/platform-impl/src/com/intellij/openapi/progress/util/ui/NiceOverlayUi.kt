// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util.ui

import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.impl.ShadowBorderPainter
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage
import javax.swing.JRootPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * Implementation of Popup by UX-3484
 * We have to use AWT here, as we cannot spin the EventQueue for Swing
 */
@ApiStatus.Internal
class NiceOverlayUi(
  val rootPane: JRootPane,
  /**
   * "Close" button requires making a screenshot (see [com.intellij.openapi.progress.util.ui.NiceOverlayUi.screenshot])
   * The screenshot via Robot provokes an alert on MacOS, and it does not work nice on multi-monitor linux setup
   * So for now we decide to not show the close button and release at least some part of the UI.
   */
  val showCloseButton: Boolean,
) {


  private val mainText = DiagnosticBundle.message("freeze.popup.application.is.not.responding", ApplicationInfo.getInstance().versionName)
  private val dumpThreadsButtonText = DiagnosticBundle.message("freeze.popup.dump.threads.suggestion")
  val dumpThreadsButtonShortcut: KeyboardShortcut = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_D, (if (SystemInfo.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK)), null)
  private val dumpThreadsButtonShortcutText = KeymapUtil.getShortcutText(dumpThreadsButtonShortcut)


  private val popupHeight: Int = JBUIScale.scale(32)
  private val horizontalInset = JBUIScale.scale(12)
  private val gapBetweenText1AndText2 = JBUIScale.scale(15)
  private val gapBetweenText2AndText3 = JBUIScale.scale(3)
  private val separatorInset = JBUIScale.scale(10)
  private val closeIconLength = JBUIScale.scale(10)
  private val border = JBUIScale.scale(8)
  private val separatorHeight = JBUIScale.scale(20)
  private val buttonMargin = JBUIScale.scale(4)
  private val buttonHeight = JBUIScale.scale(20)
  private val buttonArc = JBUIScale.scale(5)
  private val font = JBUI.Fonts.label()
  private val shadowSize: Int = JBUIScale.scale(3)


  private val bgColor: Color = JBColor.namedColor("Notification.background")
  private val borderColor: Color = JBColor.namedColor("Notification.borderColor")
  private val fgColor: Color = ColorUtil.toAlpha(UIManager.getColor("Notification.foreground"), 220)
  private val fgShortcutColor: Color = ColorUtil.toAlpha(fgColor, 100)
  private val buttonHoverColor: Color = JBUI.CurrentTheme.ActionButton.pressedBackground()


  private val currentFontMetrics: FontMetrics = GraphicsUtil.safelyGetGraphics(rootPane).getFontMetrics(font)
  private val yOffsetOfTextInPopup: Int = popupHeight / 2 + currentFontMetrics.ascent / 2 - JBUIScale.scale(1)

  private val popupWidth: Int = horizontalInset + getTextLength(mainText) + gapBetweenText1AndText2 + getTextLength(dumpThreadsButtonText) + gapBetweenText2AndText3 + getTextLength(dumpThreadsButtonShortcutText) +
                                if (showCloseButton) {
                                  separatorInset + 1 + separatorInset + closeIconLength
                                }
                                else {
                                  0
                                } +
                                horizontalInset

  // offsets relative to the containing component
  private val popupOffsetX: Int = rootPane.width / 2 - popupWidth / 2
  private val popupOffsetY: Int = run {
    val frame = SwingUtilities.getAncestorOfClass(IdeFrame::class.java, rootPane) as? IdeFrame
    frame?.statusBar?.component?.location?.y ?: (rootPane.height - 20 - popupHeight)
  }

  // regions of the buttons relative to the containing component
  private val locationOfCross: Rectangle
  private val locationOfThreadDumpButton: Rectangle

  /**
   * The users might want to close the freeze popup. In this case, we need to draw the contents of whatever is behind the popup
   * We cannot ask Swing to repaint the region, as we cannot exit the EDT event we are currently in.
   * We cannot repaint the region manually, as the underlying painting logic might try to access the read/write lock, and we would get a deadlock.
   *
   * But we know that the UI is frozen, hence we do a trick: we take a screenshot of the region where the freeze popup is located,
   * and draw it back when the user decides to close the popup.
   */
  private val screenshot: MultiResolutionImage?

  /**
   * The location of popup including its shadow; we need it to replace it with the screenshot later
   */
  private val popupWithShadowLocation: Rectangle = Rectangle(popupOffsetX - shadowSize, popupOffsetY - shadowSize, popupWidth + shadowSize * 2, popupHeight + shadowSize * 2)

  private val initialCursor = rootPane.cursor

  /**
   * The "model" of the popup
   */
  private var closeButtonHovered: Boolean = false
  private var threadDumpButtonHovered: Boolean = false
  private var closed = false

  init {
    screenshot = if (showCloseButton) {
      takeScreenshot()
    }
    else {
      // screenshot is not accessed in this case
      null
    }

    trySetCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))

    drawShadow()

    val startOfThreadDumpText = popupOffsetX + horizontalInset + getTextLength(mainText) + gapBetweenText1AndText2
    val offsetFromTop = popupOffsetY + (popupHeight - closeIconLength) / 2 - JBUIScale.scale(2)
    // see the comment in takeScreenshot for the reason of shifting by the start of `rootPane`
    locationOfThreadDumpButton = Rectangle(
      rootPane.x + startOfThreadDumpText - buttonMargin,
      rootPane.y + offsetFromTop - buttonMargin,
      getTextLength(dumpThreadsButtonText) + gapBetweenText2AndText3 + getTextLength(dumpThreadsButtonShortcutText) + buttonMargin * 2,
      buttonHeight)
    locationOfCross = Rectangle(
      rootPane.x + startOfThreadDumpText + getTextLength(dumpThreadsButtonText) + gapBetweenText2AndText3 + getTextLength(dumpThreadsButtonShortcutText) + separatorInset + JBUIScale.scale(1) + separatorInset - buttonMargin,
      rootPane.y + offsetFromTop - buttonMargin,
      closeIconLength + buttonMargin * 2,
      buttonHeight)

    redrawMainComponent()
  }

  private fun drawShadow() {

    val backBuffer = drawPopupContents()

    val originalGraphics = GraphicsUtil.safelyGetGraphics(rootPane)
    try {
      originalGraphics.translate(popupOffsetX, popupOffsetY)
      val shadow = ShadowBorderPainter.createShadow(backBuffer, 0, 0, false, shadowSize)

      // unfortunately, our shadow-handling code is not scale-aware
      if (JreHiDpiUtil.isJreHiDPI(originalGraphics as Graphics2D)) {
        val scaledGraphics = originalGraphics.create() as Graphics2D
        try {
          val s: Float = 1 / JBUIScale.sysScale(originalGraphics)
          scaledGraphics.scale(s.toDouble(), s.toDouble())
          UIUtil.drawImage(scaledGraphics, shadow.getImage(), shadow.getX() + shadowSize + 1, shadow.getY() - shadowSize + 1, null)
        } finally {
          scaledGraphics.dispose()
        }
      }
      else {
        UIUtil.drawImage(originalGraphics, shadow.image, shadow.x, shadow.y, null)
      }
    }
    finally {
      originalGraphics.dispose()
    }
  }

  /**
   * Redraws the freeze popup based on its current state.
   */
  fun redrawMainComponent() {
    if (closed) {
      return
    }

    val popupImage = drawPopupContents()

    val originalGraphics = GraphicsUtil.safelyGetGraphics(rootPane)
    try {
      originalGraphics.translate(popupOffsetX, popupOffsetY)
      UIUtil.drawImage(originalGraphics, popupImage, 0, 0, null)
    }
    finally {
      originalGraphics.dispose()
    }
  }

  private fun takeScreenshot(): MultiResolutionImage {
    val window = SwingUtilities.getWindowAncestor(rootPane)
    // for some reason, on Windows the location of `java.awt.Window` may start with negative coordinates
    // I am clueless why it is like this, but adjusting the coordinates to the location of IdeRootPane
    // allows interacting with actual positions on the screen
    val targetX = window.x + rootPane.x + popupWithShadowLocation.x
    val targetY = window.y + rootPane.y + popupWithShadowLocation.y
    val screen = Robot(window.graphicsConfiguration.device).createMultiResolutionScreenCapture(Rectangle(targetX, targetY, popupWithShadowLocation.width, popupWithShadowLocation.height))
    return screen
  }

  /**
   * @param point the point relative to the containing component
   */
  fun mouseMoved(point: Point) {
    closeButtonHovered = showCloseButton && locationOfCross.contains(point)
    threadDumpButtonHovered = locationOfThreadDumpButton.contains(point)
  }

  enum class ClickOutcome {
    NOTHING,
    DUMP_THREADS,
    CLOSED,
  }

  /**
   * @param point the point relative to the containing component
   */
  fun mouseClicked(point: Point): ClickOutcome {
    if (showCloseButton && locationOfCross.contains(point)) {
      closed = true
      restoreScreenshot()
      return ClickOutcome.CLOSED
    }
    else if (locationOfThreadDumpButton.contains(point)) {
      return ClickOutcome.DUMP_THREADS
    }
    else {
      return ClickOutcome.NOTHING
    }
  }

  /**
   * Closes the overlay and marks the region for repainting
   */
  fun close() {
    rootPane.repaint(popupWithShadowLocation)
    trySetCursor(initialCursor)
  }

  private fun restoreScreenshot() {
    check(showCloseButton) {
      "Screenshot can be used only when the close button is enabled"
    }
    checkNotNull(screenshot) {
      "Screenshot must be initialized for restoration"
    }
    val innerGraphics = GraphicsUtil.safelyGetGraphics(rootPane) as Graphics2D
    try {
      val variant = screenshot.resolutionVariants.run { if (size > 1) get(1) else get(0) }
      val scaledGraphics = innerGraphics.create() as Graphics2D
      try {
        val scale = JBUIScale.sysScale(innerGraphics)
        val s: Float = 1 / scale
        scaledGraphics.scale(s.toDouble(), s.toDouble())
        UIUtil.drawImage(scaledGraphics, variant, (popupWithShadowLocation.x * scale).toInt(), (popupWithShadowLocation.y * scale).toInt(), null)
      } finally {
        scaledGraphics.dispose()
      }
    }
    finally {
      innerGraphics.dispose()
    }
  }

  /**
   * Computes a Hi-DPI image of the opaque part of the freeze popup. The image does not include shadow.
   */
  private fun drawPopupContents(): BufferedImage {

    val backBuffer = UIUtil.createImage(rootPane.graphicsConfiguration, popupWidth.toDouble(), popupHeight.toDouble(),
                                        BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.ROUND)
    val graphics: Graphics2D = backBuffer.createGraphics()
    try {
      GraphicsUtil.setupAAPainting(graphics)
      graphics.font = font

      graphics.drawBackgroundShape()
      graphics.drawBorder()
      var accumulatingOffsetX = horizontalInset

      val lengthOfText1 = SwingUtilities.computeStringWidth(currentFontMetrics, mainText)
      val lengthOfText2 = SwingUtilities.computeStringWidth(currentFontMetrics, dumpThreadsButtonText)
      val lengthOfText3 = SwingUtilities.computeStringWidth(currentFontMetrics, dumpThreadsButtonShortcutText)

      graphics.drawMainText(accumulatingOffsetX, mainText)

      accumulatingOffsetX += lengthOfText1 + gapBetweenText1AndText2

      if (threadDumpButtonHovered) {
        trySetCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
        graphics.paintButtonBackground(accumulatingOffsetX, lengthOfText2 + gapBetweenText2AndText3 + lengthOfText3)
      }
      else {
        trySetCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
      }

      graphics.drawMainText(accumulatingOffsetX, dumpThreadsButtonText)
      accumulatingOffsetX += lengthOfText2 + gapBetweenText2AndText3
      accumulatingOffsetX += graphics.drawShortcut(accumulatingOffsetX, dumpThreadsButtonShortcutText)

      if (showCloseButton) {
        accumulatingOffsetX += graphics.drawSeparator(accumulatingOffsetX)

        graphics.color = fgColor

        if (closeButtonHovered) {
          graphics.paintButtonBackground(accumulatingOffsetX, closeIconLength)
        }
        graphics.drawCloseButton(accumulatingOffsetX)
      }

    }
    finally {
      graphics.dispose()
    }

    return backBuffer
  }

  private fun trySetCursor(cursor: Cursor) {
    val point = MouseInfo.getPointerInfo()?.location ?: return
    val window = SwingUtilities.getWindowAncestor(rootPane) ?: return
    if (!window.isShowing || !window.bounds.contains(point)) return
    SwingUtilities.convertPointFromScreen(point, window) // handles insets/decorations
    val deepest = SwingUtilities.getDeepestComponentAt(window, point.x, point.y) ?: window
    UIUtil.setCursor(deepest, cursor)
  }

  private fun getTextLength(text: @Nls String): Int {
    return SwingUtilities.computeStringWidth(currentFontMetrics, text)
  }

  private fun Graphics2D.drawBackgroundShape() {
    color = bgColor
    fillRoundRect(0, 0, popupWidth, popupHeight, border, border)
  }

  private fun Graphics2D.drawBorder() {
    stroke = BasicStroke(JBUIScale.scale(1.0f))
    color = borderColor
    drawRoundRect(0, 0, popupWidth, popupHeight, border, border)
  }

  private fun Graphics2D.drawMainText(startX: Int, text: @Nls String) {
    color = fgColor
    drawString(text, startX.toFloat(), yOffsetOfTextInPopup.toFloat())
  }

  private fun Graphics2D.drawShortcut(startX: Int, shortcut: @NlsSafe String): /* length of the drawn string */  Int {
    color = fgShortcutColor
    drawString(shortcut, startX, yOffsetOfTextInPopup)
    return SwingUtilities.computeStringWidth(currentFontMetrics, shortcut)
  }

  private fun Graphics2D.drawSeparator(startX: Int): /* length of separator including insets */ Int {
    val actualStart = startX + separatorInset
    color = ColorUtil.toAlpha(fgColor, 50)
    stroke = BasicStroke(JBUIScale.scale(1.0f))
    val offsetFromTop = (popupHeight - separatorHeight) / 2
    drawLine(actualStart, offsetFromTop, actualStart, popupHeight - offsetFromTop)
    return separatorInset + JBUIScale.scale(1) + separatorInset
  }

  private fun Graphics2D.drawCloseButton(startX: Int) {
    color = fgColor
    val offsetFromTop = (popupHeight - closeIconLength) / 2
    drawLine(startX, offsetFromTop, startX + closeIconLength, popupHeight - offsetFromTop)
    drawLine(startX + closeIconLength, offsetFromTop, startX, popupHeight - offsetFromTop)
  }

  private fun Graphics2D.paintButtonBackground(startX: Int, lengthOfButton: Int) {
    color = buttonHoverColor
    fillRoundRect(startX - buttonMargin, (popupHeight - buttonHeight) / 2, lengthOfButton + buttonMargin * 2, buttonHeight, buttonArc, buttonArc)
  }

}