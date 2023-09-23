// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.ui

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.ui.scale.isHiDPIEnabledAndApplicable
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ImageObserver
import java.util.*
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource
import javax.swing.text.StyleContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

object StartupUiUtil {
  @JvmStatic
  val isUnderDarcula: Boolean
    @Deprecated("Do not use it. Use UI theme properties.", ReplaceWith("StartupUiUtil[isDarkTheme]"))
    @ScheduledForRemoval
    get() = isDarkTheme

  @get:Internal
  val isDarkTheme: Boolean
    get() {
      // Do not use UIManager.getLookAndFeel().defaults because it won't work.
      // We use UIManager.getLookAndFeelDefaults() in installTheme in com.intellij.ide.ui.laf.LafManagerImpl.doSetLaF
      val lookAndFeelDefaults = UIManager.getLookAndFeelDefaults()
      return lookAndFeelDefaults == null || lookAndFeelDefaults.getBoolean("ui.theme.is.dark")
    }

  @JvmStatic
  @Deprecated("Starts from 2023.3 all themes use DarculaLaf", ReplaceWith("false"))
  @ScheduledForRemoval
  fun isUnderIntelliJLaF(): Boolean {
    return !isDarkTheme
  }

  @JvmStatic
  @Deprecated("Starts from NewUI default mac theme lost meaning. If you want to something based on theme please check current theme id.",
              ReplaceWith("false"))
  @ScheduledForRemoval
  fun isUnderDefaultMacTheme(): Boolean {
    return false
  }

  @JvmStatic
  @Deprecated("Starts from NewUI default win10 theme lost meaning. If you want to something based on theme please check current theme id.",
              ReplaceWith("false"))
  @ScheduledForRemoval
  fun isUnderWin10LookAndFeel(): Boolean {
    return false
  }

  @JvmStatic
  fun getLcdContrastValue(): Int {
    val lcdContrastValue = if (LoadingState.APP_STARTED.isOccurred) Registry.intValue("lcd.contrast.value", 0) else 0
    return if (lcdContrastValue == 0) {
      doGetLcdContrastValueForSplash(isDarkTheme)
    }
    else {
      normalizeLcdContrastValue(lcdContrastValue)
    }
  }

  private fun doGetLcdContrastValueForSplash(isUnderDarcula: Boolean): Int {
    if (SystemInfoRt.isMac) {
      return if (isUnderDarcula) 140 else 230
    }

    @Suppress("SpellCheckingInspection")
    val lcdContrastValue = (Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") as? Map<*, *>)
                             ?.get(RenderingHints.KEY_TEXT_LCD_CONTRAST) as Int? ?: return 140
    return normalizeLcdContrastValue(lcdContrastValue)
  }

  private fun normalizeLcdContrastValue(lcdContrastValue: Int): Int {
    return if (lcdContrastValue < 100 || lcdContrastValue > 250) 140 else lcdContrastValue
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the default monitor device is HiDPI.
   * (Analogue of [UIUtil.isRetina] on macOS)
   */
  @JvmStatic
  val isJreHiDPI: Boolean
    get() = isHiDPIEnabledAndApplicable(JBUIScale.sysScale())

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the provided component is tied to a HiDPI device.
   */
  @JvmStatic
  fun isJreHiDPI(component: Component?): Boolean = JreHiDpiUtil.isJreHiDPI(component?.graphicsConfiguration)

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the provided system scale context is HiDPI.
   */
  fun isJreHiDPI(scaleContext: ScaleContext?): Boolean {
    return isHiDPIEnabledAndApplicable(scaleContext?.getScale(ScaleType.SYS_SCALE)?.toFloat() ?: JBUIScale.sysScale())
  }

  @JvmStatic
  fun getCenterPoint(container: Dimension, child: Dimension): Point {
    return getCenterPoint(Rectangle(container), child)
  }

  @JvmStatic
  fun getCenterPoint(container: Rectangle, child: Dimension): Point {
    return Point(container.x + (container.width - child.width) / 2, container.y + (container.height - child.height) / 2)
  }

  /**
   * A hidpi-aware wrapper over [Graphics.drawImage].
   *
   * @see .drawImage
   */
  @JvmStatic
  fun drawImage(g: Graphics, image: Image, x: Int, y: Int, observer: ImageObserver?) {
    drawImage(g = g, image = image, x = x, y = y, sourceBounds = null, op = null, observer = observer)
  }

  @JvmStatic
  fun drawImage(g: Graphics, image: Image) {
    drawImage(g = g, image = image, x = 0, y = 0, dw = -1, dh = -1, sourceBounds = null, op = null, observer = null)
  }

  /**
   * A hidpi-aware wrapper over [Graphics.drawImage].
   *
   * @see .drawImage
   */
  @JvmStatic
  fun drawImage(g: Graphics, image: Image, dstBounds: Rectangle?, observer: ImageObserver?) {
    drawImage(g = g, image = image, destinationBounds = dstBounds, sourceBounds = null, op = null, observer = observer)
  }

  /**
   * @see .drawImage
   */
  @JvmStatic
  fun drawImage(g: Graphics, image: Image, dstBounds: Rectangle?, srcBounds: Rectangle?, observer: ImageObserver?) {
    drawImage(g = g, image = image, destinationBounds = dstBounds, sourceBounds = srcBounds, op = null, observer = observer)
  }

  /**
   * @see .drawImage
   */
  @JvmStatic
  fun drawImage(g: Graphics, image: BufferedImage, op: BufferedImageOp?, x: Int, y: Int) {
    drawImage(g = g, image = image, x = x, y = y, dw = -1, dh = -1, sourceBounds = null, op = op, observer = null)
  }

  @JvmStatic
  val labelFont: Font
    get() = UIManager.getFont("Label.font")

  @JvmStatic
  fun isDialogFont(font: Font): Boolean = Font.DIALOG == font.getFamily(Locale.US)

  @JvmStatic
  fun addAwtListener(listener: AWTEventListener, mask: Long, parent: Disposable) {
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, mask)
    Disposer.register(parent) { Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
  }

  /**
   * Waits for the EDT to dispatch all its invocation events.
   * Must be called outside EDT.
   * Use [com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue] if you want to pump from inside EDT
   */
  @TestOnly
  fun pump() {
    assert(!SwingUtilities.isEventDispatchThread())
    val lock = Semaphore(1)
    SwingUtilities.invokeLater { lock.up() }
    lock.waitFor()
  }
}

fun getFontWithFallback(familyName: String?,
                        @Suppress("DEPRECATION") @org.intellij.lang.annotations.JdkConstants.FontStyle style: Int,
                        size: Float): FontUIResource {
  // On macOS font fallback is implemented in JDK by default
  // (except for explicitly registered fonts, e.g., the fonts we bundle with IDE, for them, we don't have a solution now)
  // in headless mode just use fallback in order to avoid font loading
  val fontWithFallback = if (SystemInfoRt.isMac || GraphicsEnvironment.isHeadless()) {
    Font(familyName, style, size.toInt()).deriveFont(size)
  }
  else {
    StyleContext().getFont(familyName, style, size.toInt()).deriveFont(size)
  }
  return if (fontWithFallback is FontUIResource) fontWithFallback else FontUIResource(fontWithFallback)
}

@Internal
fun drawImage(g: Graphics,
              image: Image,
              destinationBounds: Rectangle?,
              sourceBounds: Rectangle?,
              op: BufferedImageOp?,
              observer: ImageObserver?) {
  if (destinationBounds == null) {
    drawImage(g = g, image = image, sourceBounds = sourceBounds, op = op, observer = observer)
  }
  else {
    drawImage(g = g,
              image = image,
              x = destinationBounds.x,
              y = destinationBounds.y,
              dw = destinationBounds.width,
              dh = destinationBounds.height,
              sourceBounds = sourceBounds,
              op = op,
              observer = observer)
  }
}

private val useAccuracyDelta = System.getProperty("ide.icon.scale.useAccuracyDelta", "true").toBoolean()

/**
 * A hidpi-aware wrapper over [Graphics.drawImage].
 *
 * The `dstBounds` and `srcBounds` are in the user space (just like the width/height of the image).
 * If `dstBounds` is null or if its width/height is set to (-1) the image bounds or the image width/height is used.
 * If `srcBounds` is null or if its width/height is set to (-1) the image bounds or the image right/bottom area to the provided x/y is used.
 */
@Internal
fun drawImage(g: Graphics,
              image: Image,
              x: Int = 0,
              y: Int = 0,
              dw: Int = -1,
              dh: Int = -1,
              sourceBounds: Rectangle? = null,
              op: BufferedImageOp? = null,
              observer: ImageObserver? = null) {
  val hasDestinationSize = dw >= 0 && dh >= 0
  if (image is JBHiDPIScaledImage) {
    doDrawHiDpi(userWidth = image.getUserWidth(),
                userHeight = image.getUserHeight(),
                g = g,
                scale = image.scale,
                dx = x,
                dy = y,
                dw = dw,
                dh = dh,
                hasDestinationSize = hasDestinationSize,
                op = op,
                image = image.delegate ?: image,
                srcBounds = sourceBounds,
                observer = observer)
  }
  else {
    doDraw(op = op,
           image = image,
           invG = null,
           hasDestinationSize = hasDestinationSize,
           dw = dw,
           dh = dh,
           sourceBounds = sourceBounds,
           userWidth = image.getWidth(null),
           userHeight = image.getHeight(null),
           g = g,
           dx = x,
           dy = y,
           observer = observer,
           scale = 1.0)
  }
}

@Suppress("NAME_SHADOWING")
private fun doDrawHiDpi(userWidth: Int,
                        userHeight: Int,
                        g: Graphics,
                        scale: Double,
                        dx: Int,
                        dy: Int,
                        dw: Int,
                        dh: Int,
                        hasDestinationSize: Boolean,
                        op: BufferedImageOp?,
                        image: Image,
                        srcBounds: Rectangle?,
                        observer: ImageObserver?) {
  var scale1 = scale
  var dx1 = dx
  var dy1 = dy
  var delta = 0.0
  if (useAccuracyDelta) {
    // Calculate the delta based on the image size. The bigger the size - the smaller the delta.
    val maxSize = max(userWidth, userHeight)
    if (maxSize < Int.MAX_VALUE / 2) {
      var dotAccuracy = 1
      var pow: Double
      while (maxSize > 10.0.pow(dotAccuracy.toDouble()).also { pow = it }) {
        dotAccuracy++
      }
      delta = 1 / pow
    }
  }

  if (g !is Graphics2D) {
    return
  }

  val tx = g.transform
  var invG: Graphics2D? = null
  var g1 = g
  if ((tx.type and AffineTransform.TYPE_MASK_ROTATION) == 0 &&
      abs(scale1 - tx.scaleX) <= delta) {
    scale1 = tx.scaleX

    // The image has the same original scale as the graphics scale. However, the real image
    // scale - userSize/realSize - can suffer from inaccuracy due to the image user size
    // rounding to int (userSize = (int)realSize/originalImageScale). This may case quality
    // loss if the image is drawn via Graphics.drawImage(image, <srcRect>, <dstRect>)
    // due to scaling in Graphics. To avoid that, the image should be drawn directly via
    // Graphics.drawImage(image, 0, 0) on the unscaled Graphics.
    val gScaleX = tx.scaleX
    val gScaleY = tx.scaleY
    tx.scale(1 / gScaleX, 1 / gScaleY)
    tx.translate(dx1 * gScaleX, dy1 * gScaleY)
    dy1 = 0
    dx1 = 0
    invG = g1.create() as Graphics2D
    g1 = invG
    invG.transform = tx
  }

  try {
    var dw = dw
    var dh = dh
    if (invG != null && hasDestinationSize) {
      dw = scaleSize(dw, scale1)
      dh = scaleSize(dh, scale1)
    }
    doDraw(op = op,
           image = image,
           invG = invG,
           hasDestinationSize = hasDestinationSize,
           dw = dw,
           dh = dh,
           sourceBounds = srcBounds,
           userWidth = userWidth,
           userHeight = userHeight,
           g = g1,
           dx = dx1,
           dy = dy1,
           observer = observer,
           scale = scale1)
  }
  finally {
    invG?.dispose()
  }
}

private fun scaleSize(size: Int, scale: Double) = (size * scale).roundToInt()

@Suppress("NAME_SHADOWING")
private fun doDraw(op: BufferedImageOp?,
                   image: Image,
                   invG: Graphics2D?,
                   hasDestinationSize: Boolean,
                   dw: Int,
                   dh: Int,
                   sourceBounds: Rectangle?,
                   userWidth: Int,
                   userHeight: Int,
                   g: Graphics,
                   dx: Int,
                   dy: Int,
                   observer: ImageObserver?,
                   scale: Double) {
  var image = image
  if (op != null && image is BufferedImage) {
    image = op.filter(image, null)
  }

  when {
    sourceBounds != null -> {
      fun size(size: Int) = scaleSize(size, scale)

      val sx = size(sourceBounds.x)
      val sy = size(sourceBounds.y)
      val sw = if (sourceBounds.width >= 0) size(sourceBounds.width) else size(userWidth) - sx
      val sh = if (sourceBounds.height >= 0) size(sourceBounds.height) else size(userHeight) - sy

      var dw = dw
      var dh = dh
      if (!hasDestinationSize) {
        dw = size(userWidth)
        dh = size(userHeight)
      }
      g.drawImage(/* img = */ image,
                  /* dx1 = */ dx, /* dy1 = */ dy, /* dx2 = */ dx + dw, /* dy2 = */ dy + dh,
                  /* sx1 = */ sx, /* sy1 = */ sy, /* sx2 = */ sx + sw, /* sy2 = */ sy + sh,
                  /* observer = */ observer)
    }
    hasDestinationSize -> {
      g.drawImage(image, dx, dy, dw, dh, observer)
    }
    invG == null -> {
      g.drawImage(image, dx, dy, userWidth, userHeight, observer)
    }
    else -> {
      g.drawImage(image, dx, dy, observer)
    }
  }
}
