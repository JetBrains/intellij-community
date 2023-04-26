// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.ui.scale

import com.intellij.diagnostic.runActivity
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.JreHiDpiUtil
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.ui.JBScalableIcon
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.util.function.Supplier
import javax.swing.UIDefaults
import javax.swing.UIManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * @author tav
 */
object JBUIScale {
  @JvmField
  @Internal
  val SCALE_VERBOSE: Boolean = java.lang.Boolean.getBoolean("ide.ui.scale.verbose")

  private const val USER_SCALE_FACTOR_PROPERTY = "JBUIScale.userScaleFactor"

  /**
   * The user scale factor, see [ScaleType.USR_SCALE].
   */
  private val userScaleFactor: SynchronizedClearableLazy<Float> = SynchronizedClearableLazy {
    DEBUG_USER_SCALE_FACTOR.value ?: computeUserScaleFactor(if (JreHiDpiUtil.isJreHiDPIEnabled()) 1f else systemScaleFactor.value)
  }

  @Internal
  fun preload(uiDefaults: Supplier<UIDefaults?>) {
    if (!systemScaleFactor.isInitialized()) {
      runActivity("system scale factor computation") {
        computeSystemScaleFactor(uiDefaults).let {
          systemScaleFactor.value = it
        }
      }
    }

    runActivity("user scale factor computation") {
      userScaleFactor.value
    }

    getSystemFontData(uiDefaults)
  }

  private val systemScaleFactor: SynchronizedClearableLazy<Float> = SynchronizedClearableLazy {
    computeSystemScaleFactor(uiDefaults = null)
  }

  private val PROPERTY_CHANGE_SUPPORT = PropertyChangeSupport(JBUIScale)
  private const val DISCRETE_SCALE_RESOLUTION = 0.25f

  @JvmField
  var DEF_SYSTEM_FONT_SIZE = 12f

  @JvmStatic
  fun addUserScaleChangeListener(listener: PropertyChangeListener) {
    PROPERTY_CHANGE_SUPPORT.addPropertyChangeListener(USER_SCALE_FACTOR_PROPERTY, listener)
  }

  @JvmStatic
  fun removeUserScaleChangeListener(listener: PropertyChangeListener) {
    PROPERTY_CHANGE_SUPPORT.removePropertyChangeListener(USER_SCALE_FACTOR_PROPERTY, listener)
  }

  private var systemFontData = SynchronizedClearableLazy<Pair<String?, Int>> {
    runActivity("system font data computation") {
      computeSystemFontData(null)
    }
  }

  private fun computeSystemFontData(uiDefaults: Supplier<UIDefaults?>?): Pair<String, Int> {
    if (GraphicsEnvironment.isHeadless()) {
      return Pair("Dialog", 12)
    }

    // with JB Linux JDK the label font comes properly scaled based on Xft.dpi settings.
    var font: Font
    if (SystemInfoRt.isMac) {
      // see AquaFonts.getControlTextFont() - lucida13Pt is a hardcoded
      // text family should be used for relatively small sizes (<20pt), don't change to Display
      // see more about SF https://medium.com/@mach/the-secret-of-san-francisco-fonts-4b5295d9a745#.2ndr50z2v
      font = Font(".SF NS Text", Font.PLAIN, 13)
      DEF_SYSTEM_FONT_SIZE = font.size.toFloat()
    }
    else {
      font = if (uiDefaults == null) UIManager.getFont("Label.font") else uiDefaults.get()!!.getFont("Label.font")
    }

    val log = thisLogger()
    val isScaleVerbose = SCALE_VERBOSE
    if (isScaleVerbose) {
      log.info(String.format("Label font: %s, %d", font.fontName, font.size))
    }

    if (SystemInfoRt.isLinux) {
      val value = Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI")
      if (isScaleVerbose) {
        log.info(String.format("gnome.Xft/DPI: %s", value))
      }
      if (value is Int) { // defined by JB JDK when the resource is available in the system
        // If the property is defined, then:
        // 1) it provides correct system scale
        // 2) the label font size is scaled
        var dpi = value / 1024
        if (dpi < 50) dpi = 50
        val scale = if (JreHiDpiUtil.isJreHiDPIEnabled()) 1f else discreteScale(dpi / 96f) // no scaling in JRE-HiDPI mode
        // derive the actual system base font size
        DEF_SYSTEM_FONT_SIZE = font.size / scale
        if (isScaleVerbose) {
          log.info(String.format("DEF_SYSTEM_FONT_SIZE: %.2f", DEF_SYSTEM_FONT_SIZE))
        }
      }
      else if (!SystemInfo.isJetBrainsJvm) {
        // With Oracle JDK: derive a scale from X server DPI, do not change DEF_SYSTEM_FONT_SIZE
        val size = DEF_SYSTEM_FONT_SIZE * screenScale
        font = font.deriveFont(size)
        if (isScaleVerbose) {
          log.info(String.format("(Not-JB JRE) reset font size: %.2f", size))
        }
      }
    }
    else if (SystemInfoRt.isWindows) {
      val winFont = Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font") as Font?
      if (winFont != null) {
        font = winFont // comes scaled
        if (isScaleVerbose) {
          log.info(String.format("Windows sys font: %s, %d", winFont.fontName, winFont.size))
        }
      }
    }
    val result = Pair(font.name, font.size)
    if (isScaleVerbose) {
      log.info(String.format("systemFontData: %s, %d", result.first, result.second))
    }
    return result
  }

  @Internal
  @JvmField
  val DEBUG_USER_SCALE_FACTOR: SynchronizedClearableLazy<Float?> = SynchronizedClearableLazy {
    val prop = System.getProperty("ide.ui.scale")
    when {
      prop != null -> {
        try {
          return@SynchronizedClearableLazy prop.toFloat()
        }
        catch (e: NumberFormatException) {
          thisLogger().error("ide.ui.scale system property is not a float value: $prop")
          null
        }
      }
      java.lang.Boolean.getBoolean("ide.ui.scale.override") -> 1f
      else -> null
    }
  }

  private fun computeSystemScaleFactor(uiDefaults: Supplier<UIDefaults?>?): Float {
    if (!java.lang.Boolean.parseBoolean(System.getProperty("hidpi", "true"))) {
      return 1f
    }

    if (JreHiDpiUtil.isJreHiDPIEnabled()) {
      val gd = try {
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
      }
      catch (ignore: HeadlessException) {
        null
      }

      val gc = gd?.defaultConfiguration
      if (gc == null || gc.device.type == GraphicsDevice.TYPE_PRINTER) {
        return 1f
      }
      else {
        return gc.defaultTransform.scaleX.toFloat()
      }
    }

    val result = getFontScale(getSystemFontData(uiDefaults).second.toFloat())
    thisLogger().info("System scale factor: $result (${if (JreHiDpiUtil.isJreHiDPIEnabled()) "JRE" else "IDE"}-managed HiDPI)")
    return result
  }

  @TestOnly
  @JvmStatic
  fun setSystemScaleFactor(sysScale: Float) {
    systemScaleFactor.value = sysScale
  }

  @TestOnly
  @JvmStatic
  fun setUserScaleFactorForTest(value: Float) {
    setUserScaleFactorProperty(value)
  }

  private fun setUserScaleFactorProperty(value: Float) {
    val oldValue = userScaleFactor.valueIfInitialized
    if (oldValue == value) {
      return
    }

    userScaleFactor.value = value
    thisLogger().info("User scale factor: $value")
    PROPERTY_CHANGE_SUPPORT.firePropertyChange(USER_SCALE_FACTOR_PROPERTY, oldValue, value)
  }

  /**
   * @return the scale factor of `fontSize` relative to the standard font size (currently 12pt)
   */
  @JvmStatic
  fun getFontScale(fontSize: Float): Float {
    return fontSize / DEF_SYSTEM_FONT_SIZE
  }

  /**
   * Sets the user scale factor.
   * The IDE uses the method.
   * It's not recommended to call the method directly from the client code.
   * For debugging purposes, the following JVM system property can be used:
   * ide.ui.scale=float
   * or the IDE registry keys (for backward compatibility):
   * ide.ui.scale.override=boolean
   * ide.ui.scale=float
   *
   * @return the result
   */
  @Internal
  @JvmStatic
  fun setUserScaleFactor(value: Float): Float {
    var scale = value

    val factor = DEBUG_USER_SCALE_FACTOR.value
    if (factor != null) {
      if (scale == factor) {
        // set the debug value as is, or otherwise ignore
        setUserScaleFactorProperty(factor)
      }
      return factor
    }

    scale = computeUserScaleFactor(scale)
    setUserScaleFactorProperty(scale)
    return scale
  }

  private fun computeUserScaleFactor(value: Float): Float {
    var scale = value
    if (!java.lang.Boolean.parseBoolean(System.getProperty("hidpi", "true"))) {
      return 1f
    }

    scale = discreteScale(scale)

    // downgrading user scale below 1.0 may be uncomfortable (tiny icons),
    // whereas some users prefer font size slightly below normal, which is ok
    if (scale < 1 && systemScaleFactor.value >= 1) {
      scale = 1f
    }

    // ignore the correction when UIUtil.DEF_SYSTEM_FONT_SIZE is overridden, see UIUtil.initSystemFontData
    if (SystemInfoRt.isLinux && scale == 1.25f && DEF_SYSTEM_FONT_SIZE == 12f) {
      // The default UI font size for Unity and Gnome is 15. Scaling factor 1.25f works badly on Linux.
      return 1f
    }
    else {
      return scale
    }
  }

  private fun discreteScale(scale: Float): Float {
    return (scale / DISCRETE_SCALE_RESOLUTION).roundToInt() * DISCRETE_SCALE_RESOLUTION
  }

  /**
   * The system scale factor, corresponding to the default monitor device.
   */
  @JvmStatic
  fun sysScale(): Float = systemScaleFactor.value

  /**
   * Returns the system scale factor, corresponding to the device the component is tied to.
   * In the IDE-managed HiDPI mode defaults to [.sysScale]
   */
  @JvmStatic
  fun sysScale(component: Component?): Float {
    return if (component == null) sysScale() else sysScale(component.graphicsConfiguration)
  }

  /**
   * Returns the system scale factor, corresponding to the graphics configuration.
   * In the IDE-managed HiDPI mode defaults to [.sysScale]
   */
  @JvmStatic
  fun sysScale(gc: GraphicsConfiguration?): Float {
    if (JreHiDpiUtil.isJreHiDPIEnabled() && gc != null && gc.device.type != GraphicsDevice.TYPE_PRINTER) {
      return gc.defaultTransform.scaleX.toFloat()
    }
    else {
      return systemScaleFactor.value
    }
  }

  /**
   * @return 'f' scaled by the user scale factor
   */
  @JvmStatic
  fun scale(value: Float): Float {
    return value * userScaleFactor.value
  }

  /**
   * @return 'i' scaled by the user scale factor
   */
  @JvmStatic
  fun scale(i: Int): Int {
    return (userScaleFactor.value * i).roundToInt()
  }

  /**
   * Scales the passed `icon` according to the user scale factor.
   *
   * @see ScaleType.USR_SCALE
   */
  @JvmStatic
  fun <T : JBScalableIcon> scaleIcon(icon: T): T {
    @Suppress("UNCHECKED_CAST")
    return icon.withIconPreScaled(false) as T
  }

  @JvmStatic
  fun scaleFontSize(fontSize: Float): Int {
    return scaleFontSize(fontSize, userScaleFactor.value)
  }

  @Internal
  @JvmStatic
  fun scaleFontSize(fontSize: Float, userScaleFactor: Float): Int {
    return when (userScaleFactor) {
      1.25f -> fontSize * 1.34f
      1.75f -> fontSize * 1.67f
      else -> fontSize * userScaleFactor
    }.toInt()
  }

  private val screenScale: Float
    get() {
      val dpi = try {
        Toolkit.getDefaultToolkit().screenResolution
      }
      catch (ignored: HeadlessException) {
        96
      }
      return discreteScale(dpi / 96f)
    }

  @JvmStatic
  fun getSystemFontData(uiDefaults: Supplier<UIDefaults?>?): Pair<String?, Int> {
    if (uiDefaults == null) {
      return systemFontData.value
    }

    systemFontData.valueIfInitialized?.let {
      return it
    }
    return computeSystemFontData(uiDefaults).also { systemFontData.value = it }
  }

  /**
   * Returns the system scale factor, corresponding to the graphics.
   * For BufferedImage's graphics, the scale is taken from the graphics itself.
   * In the IDE-managed HiDPI mode defaults to [.sysScale]
   */
  @JvmStatic
  fun sysScale(g: Graphics2D?): Float {
    if (g == null || !JreHiDpiUtil.isJreHiDPIEnabled()) {
      return sysScale()
    }

    val gc = g.deviceConfiguration
    if (gc == null || gc.device.type == GraphicsDevice.TYPE_IMAGE_BUFFER || gc.device.type == GraphicsDevice.TYPE_PRINTER) {
      // in this case, gc doesn't provide a valid scale
      return abs(getTransformScaleX(g.transform))
    }
    else {
      return gc.defaultTransform.scaleX.toFloat()
    }
  }

  /**
   * Get scale for an arbitrary affine transform.
   * This should not be necessary for [GraphicsConfiguration.getDefaultTransform], as it is expected to be a translation/uniform scale only.
   *
   * See javadoc [AffineTransform.getScaleX], it will return an arbitrary number (inc. negative ones)
   * after [AffineTransform.rotate] or `AffineTransform.scale(-1, 1)` transforms.
   */
  private fun getTransformScaleX(transform: AffineTransform): Float {
    val p = Point2D.Double(1.0, 0.0);
    transform.deltaTransform(p, p)
    return p.distance(0.0, 0.0).toFloat()
  }

  @JvmStatic
  fun sysScale(context: ScaleContext?): Double {
    return context?.getScale(ScaleType.SYS_SCALE) ?: sysScale().toDouble()
  }

  /**
   * Returns whether the provided scale assumes HiDPI-awareness.
   */
  @JvmStatic
  fun isHiDPI(scale: Double): Boolean {
    // The scale below 1.0 is impractical.
    // It's rather accepted for debug purpose.
    // Treat it as "hidpi" to correctly manage images which have different users and real size
    // (for scale below 1.0 the real size will be smaller).
    return scale != 1.0
  }

  /**
   * Returns whether the [ScaleType.USR_SCALE] scale factor assumes HiDPI-awareness. An equivalent of `isHiDPI(scale(1f))`
   */
  @JvmStatic
  val isUsrHiDPI: Boolean
    get() = isHiDPI(scale(1f))
}