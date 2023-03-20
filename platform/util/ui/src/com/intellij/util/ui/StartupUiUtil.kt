// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale.isHiDPI
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.JBHiDPIScaledImage
import org.intellij.lang.annotations.JdkConstants
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ImageObserver
import java.util.*
import java.util.function.Function
import javax.swing.InputMap
import javax.swing.KeyStroke
import javax.swing.UIDefaults
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource
import javax.swing.text.DefaultEditorKit
import javax.swing.text.StyleContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

object StartupUiUtil {
  @ApiStatus.Internal
  @JvmField
  val ourPatchableFontResources = arrayOf("Button.font", "ToggleButton.font", "RadioButton.font",
                                          "CheckBox.font", "ColorChooser.font", "ComboBox.font", "Label.font", "List.font", "MenuBar.font",
                                          "MenuItem.font",
                                          "MenuItem.acceleratorFont", "RadioButtonMenuItem.font", "CheckBoxMenuItem.font", "Menu.font",
                                          "PopupMenu.font", "OptionPane.font",
                                          "Panel.font", "ProgressBar.font", "ScrollPane.font", "Viewport.font", "TabbedPane.font",
                                          "Table.font", "TableHeader.font",
                                          "TextField.font", "FormattedTextField.font", "Spinner.font", "PasswordField.font",
                                          "TextArea.font", "TextPane.font", "EditorPane.font",
                                          "TitledBorder.font", "ToolBar.font", "ToolTip.font", "Tree.font")
  const val ARIAL_FONT_NAME = "Arial"

  val isUnderDarcula: Boolean
    get() = UIManager.getLookAndFeel().name.contains("Darcula")

  @ApiStatus.Internal
  fun doGetLcdContrastValueForSplash(isUnderDarcula: Boolean): Int {
    if (SystemInfoRt.isMac) {
      return if (isUnderDarcula) 140 else 230
    }

    @Suppress("SpellCheckingInspection")
    val lcdContrastValue = (Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") as? Map<*, *>)
                             ?.get(RenderingHints.KEY_TEXT_LCD_CONTRAST) as Int? ?: return 140
    return normalizeLcdContrastValue(lcdContrastValue)
  }

  fun normalizeLcdContrastValue(lcdContrastValue: Int): Int {
    return if (lcdContrastValue < 100 || lcdContrastValue > 250) 140 else lcdContrastValue
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the default monitor device is HiDPI.
   * (analogue of [UIUtil.isRetina] on macOS)
   */
  val isJreHiDPI: Boolean
    get() = JreHiDpiUtil.isJreHiDPI(sysScale())

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the provided component is tied to a HiDPI device.
   */
  fun isJreHiDPI(comp: Component?): Boolean {
    return JreHiDpiUtil.isJreHiDPI(comp?.graphicsConfiguration)
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the provided system scale context is HiDPI.
   */
  fun isJreHiDPI(ctx: ScaleContext?): Boolean {
    return JreHiDpiUtil.isJreHiDPIEnabled() && isHiDPI(sysScale(ctx))
  }

  fun getCenterPoint(container: Dimension, child: Dimension): Point {
    return getCenterPoint(Rectangle(container), child)
  }

  fun getCenterPoint(container: Rectangle, child: Dimension): Point {
    return Point(container.x + (container.width - child.width) / 2, container.y + (container.height - child.height) / 2)
  }

  /**
   * A hidpi-aware wrapper over [Graphics.drawImage].
   *
   * @see .drawImage
   */
  fun drawImage(g: Graphics, image: Image, x: Int, y: Int, observer: ImageObserver?) {
    drawImage(g = g, image = image, dstBounds = Rectangle(x, y, -1, -1), srcBounds = null, op = null, observer = observer)
  }

  fun drawImage(g: Graphics, image: Image, x: Int, y: Int, width: Int, height: Int, op: BufferedImageOp?) {
    val srcBounds = if (width >= 0 && height >= 0) Rectangle(x, y, width, height) else null
    drawImage(g = g, image = image, dstBounds = Rectangle(x, y, width, height), srcBounds = srcBounds, op = op, observer = null)
  }

  fun drawImage(g: Graphics, image: Image) {
    drawImage(g = g, image = image, x = 0, y = 0, width = -1, height = -1, op = null)
  }

  /**
   * A hidpi-aware wrapper over [Graphics.drawImage].
   *
   * @see .drawImage
   */
  fun drawImage(g: Graphics, image: Image, dstBounds: Rectangle?, observer: ImageObserver?) {
    drawImage(g = g, image = image, dstBounds = dstBounds, srcBounds = null, op = null, observer = observer)
  }

  /**
   * @see .drawImage
   */
  fun drawImage(g: Graphics,
                image: Image,
                dstBounds: Rectangle?,
                srcBounds: Rectangle?,
                observer: ImageObserver?) {
    drawImage(g = g, image = image, dstBounds = dstBounds, srcBounds = srcBounds, op = null, observer = observer)
  }

  /**
   * A hidpi-aware wrapper over [Graphics.drawImage].
   *
   *
   * The `dstBounds` and `srcBounds` are in the user space (just like the width/height of the image).
   * If `dstBounds` is null or if its width/height is set to (-1) the image bounds or the image width/height is used.
   * If `srcBounds` is null or if its width/height is set to (-1) the image bounds or the image right/bottom area to the provided x/y is used.
   */
  fun drawImage(g: Graphics,
                image: Image,
                dstBounds: Rectangle?,
                srcBounds: Rectangle?,
                op: BufferedImageOp?,
                observer: ImageObserver?) {
    var g = g
    var image = image
    val userWidth = ImageUtil.getUserWidth(image)
    val userHeight = ImageUtil.getUserHeight(image)
    var dx = 0
    var dy = 0
    var dw = -1
    var dh = -1
    if (dstBounds != null) {
      dx = dstBounds.x
      dy = dstBounds.y
      dw = dstBounds.width
      dh = dstBounds.height
    }
    val hasDstSize = dw >= 0 && dh >= 0
    var invG: Graphics2D? = null
    var scale = 1.0
    if (image is JBHiDPIScaledImage) {
      val delegate = image.delegate
      if (delegate != null) {
        image = delegate
      }
      scale = image.scale
      var delta = 0.0
      if (java.lang.Boolean.parseBoolean(System.getProperty("ide.icon.scale.useAccuracyDelta", "true"))) {
        // Calculate the delta based on the image size. The bigger the size - the smaller the delta.
        val maxSize = max(userWidth, userHeight)
        if (maxSize < Int.MAX_VALUE / 2) { // sanity check
          var dotAccuracy = 1
          var pow: Double
          while (maxSize > 10.0.pow(dotAccuracy.toDouble()).also { pow = it }) dotAccuracy++
          delta = 1 / pow
        }
      }
      val tx = (g as Graphics2D).transform
      if (abs(scale - tx.scaleX) <= delta) {
        scale = tx.scaleX

        // The image has the same original scale as the graphics scale. However, the real image
        // scale - userSize/realSize - can suffer from inaccuracy due to the image user size
        // rounding to int (userSize = (int)realSize/originalImageScale). This may case quality
        // loss if the image is drawn via Graphics.drawImage(image, <srcRect>, <dstRect>)
        // due to scaling in Graphics. To avoid that, the image should be drawn directly via
        // Graphics.drawImage(image, 0, 0) on the unscaled Graphics.
        val gScaleX = tx.scaleX
        val gScaleY = tx.scaleY
        tx.scale(1 / gScaleX, 1 / gScaleY)
        tx.translate(dx * gScaleX, dy * gScaleY)
        dy = 0
        dx = dy
        invG = g.create() as Graphics2D
        g = invG
        invG!!.transform = tx
      }
    }

    val _scale = scale
    val size = Function { size1: Int -> (size1 * _scale).roundToLong().toInt() }
    try {
      if (op != null && image is BufferedImage) {
        image = op.filter(image, null)
      }
      if (invG != null && hasDstSize) {
        dw = size.apply(dw)
        dh = size.apply(dh)
      }
      if (srcBounds != null) {
        val sx = size.apply(srcBounds.x)
        val sy = size.apply(srcBounds.y)
        val sw = if (srcBounds.width >= 0) size.apply(srcBounds.width) else size.apply(userWidth) - sx
        val sh = if (srcBounds.height >= 0) size.apply(srcBounds.height) else size.apply(userHeight) - sy
        if (!hasDstSize) {
          dw = size.apply(userWidth)
          dh = size.apply(userHeight)
        }
        g.drawImage(image,
                    dx, dy, dx + dw, dy + dh,
                    sx, sy, sx + sw, sy + sh,
                    observer)
      }
      else if (hasDstSize) {
        g.drawImage(image, dx, dy, dw, dh, observer)
      }
      else if (invG == null) {
        g.drawImage(image, dx, dy, userWidth, userHeight, observer)
      }
      else {
        g.drawImage(image, dx, dy, observer)
      }
    }
    finally {
      invG?.dispose()
    }
  }

  /**
   * @see .drawImage
   */
  fun drawImage(g: Graphics, image: BufferedImage, op: BufferedImageOp?, x: Int, y: Int) {
    drawImage(g = g, image = image, x = x, y = y, width = -1, height = -1, op = op)
  }

  val labelFont: Font
    get() = UIManager.getFont("Label.font")

  fun isDialogFont(font: Font): Boolean {
    return Font.DIALOG == font.getFamily(Locale.US)
  }

  fun initInputMapDefaults(defaults: UIDefaults) {
    // Make ENTER work in JTrees
    val treeInputMap = defaults["Tree.focusInputMap"] as InputMap?
    treeInputMap?.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "toggle")
    // Cut/Copy/Paste in JTextAreas
    val textAreaInputMap = defaults["TextArea.focusInputMap"] as InputMap?
    if (textAreaInputMap != null) { // It really can be null, for example when LAF isn't properly initialized (Alloy license problem)
      installCutCopyPasteShortcuts(textAreaInputMap, false)
    }
    // Cut/Copy/Paste in JTextFields
    val textFieldInputMap = defaults["TextField.focusInputMap"] as InputMap?
    if (textFieldInputMap != null) { // It really can be null, for example when LAF isn't properly initialized (Alloy license problem)
      installCutCopyPasteShortcuts(textFieldInputMap, false)
    }
    // Cut/Copy/Paste in JPasswordField
    val passwordFieldInputMap = defaults["PasswordField.focusInputMap"] as InputMap?
    if (passwordFieldInputMap != null) { // It really can be null, for example when LAF isn't properly initialized (Alloy license problem)
      installCutCopyPasteShortcuts(passwordFieldInputMap, false)
    }
    // Cut/Copy/Paste in JTables
    val tableInputMap = defaults["Table.ancestorInputMap"] as InputMap?
    if (tableInputMap != null) { // It really can be null, for example when LAF isn't properly initialized (Alloy license problem)
      installCutCopyPasteShortcuts(tableInputMap, true)
    }
  }

  private fun installCutCopyPasteShortcuts(inputMap: InputMap, useSimpleActionKeys: Boolean) {
    val copyActionKey = if (useSimpleActionKeys) "copy" else DefaultEditorKit.copyAction
    val pasteActionKey = if (useSimpleActionKeys) "paste" else DefaultEditorKit.pasteAction
    val cutActionKey = if (useSimpleActionKeys) "cut" else DefaultEditorKit.cutAction
    // Ctrl+Ins, Shift+Ins, Shift+Del
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.CTRL_DOWN_MASK), copyActionKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.SHIFT_DOWN_MASK), pasteActionKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.SHIFT_DOWN_MASK), cutActionKey)
    // Ctrl+C, Ctrl+V, Ctrl+X
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), copyActionKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), pasteActionKey)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), DefaultEditorKit.cutAction)
  }

  fun initFontDefaults(defaults: UIDefaults, uiFont: FontUIResource) {
    defaults["Tree.ancestorInputMap"] = null
    val textFont = FontUIResource(uiFont)
    val monoFont = FontUIResource("Monospaced", Font.PLAIN, uiFont.size)
    for (fontResource in ourPatchableFontResources) {
      defaults[fontResource] = uiFont
    }
    if (!SystemInfoRt.isMac) {
      defaults["PasswordField.font"] = monoFont
    }
    defaults["TextArea.font"] = monoFont
    defaults["TextPane.font"] = textFont
    defaults["EditorPane.font"] = textFont
  }

  fun getFontWithFallback(familyName: String?, @JdkConstants.FontStyle style: Int, size: Float): FontUIResource {
    // On macOS font fallback is implemented in JDK by default
    // (except for explicitly registered fonts, e.g. the fonts we bundle with IDE, for them we don't have a solution now)
    // in headless mode just use fallback in order to avoid font loading
    val fontWithFallback = if (SystemInfoRt.isMac || GraphicsEnvironment.isHeadless()) Font(familyName, style, size.toInt()).deriveFont(
      size)
    else StyleContext().getFont(familyName, style, size.toInt()).deriveFont(size)
    return if (fontWithFallback is FontUIResource) fontWithFallback else FontUIResource(fontWithFallback)
  }

  fun addAwtListener(listener: AWTEventListener, mask: Long, parent: Disposable) {
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, mask)
    Disposer.register(parent) { Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
  }
}
