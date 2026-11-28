// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.swing.ScalableSwingIcon
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.platform.icons.swing.toIconDescriptor
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.BadgeIcon
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.IconManager
import com.intellij.ui.JBColor
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.icons.CoreIconManager
import com.intellij.ui.icons.HoledIcon
import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.ui.scale.TestScaleHelper
import com.intellij.util.ui.JBUI
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class SquareStripeButtonIconDescriptorTest {
  companion object {
    private var iconsActivated = false

    private val project = projectFixture()

    private val RED = SquareIcon(Color.RED)
    private val BLUE = SquareIcon(Color.BLUE)

    private val MORE by lazy { IconLoader.getIcon("expui/general/moreHorizontal.svg", AllIcons::class.java.classLoader) }
    private val CLOSE by lazy { IconLoader.getIcon("expui/general/close.svg", AllIcons::class.java.classLoader) }
    private val PROJECT by lazy { IconLoader.getIcon("expui/toolwindows/project.svg", AllIcons::class.java.classLoader) }
    private val NOTIFICATIONS by lazy { IconLoader.getIcon("expui/toolwindows/notifications.svg", AllIcons::class.java.classLoader) }
    private val BADGE by lazy { BadgeIcon(NOTIFICATIONS, Color.BLUE) }

    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      // only IDE bootstrap activates them, and only when not headless
      // `AllIcons.Toolbar.Unknown`, the icon of a tool window without an icon, is a dummy icon until this manager is active.
      val iconManagerClass = IconManager.getInstance().javaClass
      IconManager.activate(CoreIconManager())
      IntelliJIconManager.activate()
      IconLoader.activate()
      iconsActivated = IconManager.getInstance().javaClass != iconManagerClass
    }

    @JvmStatic
    @AfterAll
    fun deactivateIcons() {
      // keep the icons of a test that activated them before this class
      if (iconsActivated) {
        IconLoader.deactivate()
        IconManager.deactivate()
      }
    }
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `base icon is the bridged tool window icon`() {
    val button = stripeButton(RED).button
    val icon = assertIs<ScalableSwingIcon>(button.icon)
    val pixels = icon.visiblePixels()

    assertTrue(pixels.isNotEmpty(), "the stripe button painted nothing")
    assertEquals(RED.bridgedPixels(), pixels, "the stripe button did not paint the tool window icon")
  }

  @Test
  fun `base icon is the 20x20 artwork of a tool window icon that has one`() {
    val button = stripeButton(PROJECT).button
    val icon = assertIs<ScalableSwingIcon>(button.icon)

    assertEquals(STRIPE_ICON_SIZE, icon.iconWidth, "the stripe icon is not as wide as the stripe size")
    assertEquals(STRIPE_ICON_SIZE, icon.iconHeight, "the stripe icon is not as high as the stripe size")
    assertEquals(
      PROJECT.legacyStripeIcon().bridgedPixels(),
      icon.visiblePixels(),
      "the stripe button did not paint the @20x20 artwork of the tool window icon",
    )
  }

  @Test
  fun `base icon follows a changed stripe icon size for the same tool window icon`() {
    val button = stripeButton(PROJECT).button

    withStripeIconSize(COMPACT_STRIPE_ICON_SIZE) {
      runInEdtAndWait { button.updateUI() }
      val icon = assertIs<ScalableSwingIcon>(button.icon)
      assertEquals(COMPACT_STRIPE_ICON_SIZE, icon.iconWidth, "the stripe icon kept the old size in the compact mode")
      assertEquals(
        PROJECT.legacyStripeIcon(COMPACT_STRIPE_ICON_SIZE).bridgedPixels(),
        icon.visiblePixels(),
        "the stripe button did not paint the tool window icon at the compact size",
      )
    }

    runInEdtAndWait { button.updateUI() }
    assertEquals(STRIPE_ICON_SIZE, assertIs<ScalableSwingIcon>(button.icon).iconWidth, "the stripe icon kept the compact size")
  }

  @Test
  fun `active icon of a tool window icon with a badge is the legacy stroke icon`() {
    val button = stripeButton(BADGE).button

    assertEquals(
      toStrokeIcon(BADGE.legacyStripeIcon(), Color.RED).visiblePixels(),
      button.activeIcon(Color.RED).visiblePixels(),
      "the active icon does not recolor the glyph as the legacy stroke icon does",
    )
  }

  @Test
  fun `active icon follows a theme change of the same color`() {
    val button = stripeButton(PROJECT).button
    val color = JBColor(Color.RED, Color.GREEN)
    button.activeIcon(color)

    withDarkTheme {
      assertEquals(
        toStrokeIcon(PROJECT.legacyStripeIcon(), color).visiblePixels(),
        button.activeIcon(color).visiblePixels(),
        "the active icon kept the stroke color of the previous theme",
      )
    }
  }

  @Test
  fun `look places a tool window icon with a badge as the legacy look does`() {
    assertPaintedAsLegacy(BADGE, active = false)
  }

  @Test
  fun `look with tool window names places a tool window icon with a badge as the legacy look does`() {
    val settings = UISettings.getInstance()
    val showNames = settings.showToolWindowsNames
    settings.showToolWindowsNames = true
    try {
      assertPaintedAsLegacy(BADGE, active = false)
      assertPaintedAsLegacy(BADGE, active = true)
    }
    finally {
      settings.showToolWindowsNames = showNames
    }
  }

  /**
   * Paints the whole button of the tool window with the flag on and off. Both must give the same pixels. The test theme has no
   * [selectedForeground], and without one an active button paints the plain icon.
   */
  private fun assertPaintedAsLegacy(icon: Icon?, active: Boolean, selectedForeground: Color? = Color.RED) {
    withSelectedForeground(selectedForeground) {
      check(UIManager.getColor(SELECTED_FOREGROUND_KEY) == selectedForeground) { "the theme does not have the requested selected foreground" }
      val registry = Registry.get(ExperimentalIconRendering.REGISTRY_KEY)
      val bridged = stripeButton(icon, active).button.paintedRaster()
      registry.setValue(false)
      try {
        val legacy = stripeButton(icon, active).button.paintedRaster()
        assertContentEquals(legacy, bridged, "the button does not paint as the legacy button does")
      }
      finally {
        registry.setValue(true)
      }
    }
  }

  @Test
  fun `tool window icon with a badge is as sharp as the legacy icon at scale 2`() {
    val icon = stripeButton(BADGE).button.icon

    assertContentEquals(BADGE.legacyStripeIcon().rasterAtScale2(), icon.rasterAtScale2(), "the badge icon is not sharp at scale 2")
  }

  @Test
  fun `wrapped icon with a badge is as sharp as the legacy icon at scale 2`() {
    val badge = BADGE.legacyStripeIcon()
    val wrapped = object : RetrievableIcon, Icon by badge {
      override fun retrieveIcon(): Icon = badge
    }

    assertContentEquals(badge.rasterAtScale2(),
                        wrapped.toIconDescriptor().createSwingIcon().rasterAtScale2(),
                        "the wrapped icon is not sharp at scale 2")
  }

  @Test
  fun `tool window icon without a 20x20 artwork is as sharp as the legacy icon at scale 2`() {
    val icon = stripeButton(CLOSE).button.icon

    assertContentEquals(CLOSE.legacyStripeIcon().rasterAtScale2(), icon.rasterAtScale2(), "the scaled icon is not sharp at scale 2")
  }

  @Test
  fun `tool window without an icon shows the unknown marker`() {
    val button = stripeButton(null).button
    val icon = assertIs<ScalableSwingIcon>(button.icon)

    val pixels = icon.visiblePixels()

    assertTrue(pixels.isNotEmpty(), "the stripe button painted nothing")
    assertEquals(AllIcons.Toolbar.Unknown.legacyStripeIcon().bridgedPixels(), pixels, "the stripe button did not paint the unknown marker")
  }

  @Test
  fun `updatePresentation follows a changed tool window icon`() {
    val (toolWindow, button) = stripeButton(RED)

    runInEdtAndWait {
      toolWindow.setIcon(BLUE)
      button.updatePresentation()
    }

    val pixels = button.icon.visiblePixels()
    assertEquals(BLUE.bridgedPixels(), pixels, "the stripe button kept the old tool window icon")
  }

  @Test
  fun `updateUI follows a changed tool window icon`() {
    val (toolWindow, button) = stripeButton(RED)

    runInEdtAndWait {
      toolWindow.setIcon(BLUE)
      button.updateUI()
    }

    val pixels = button.icon.visiblePixels()
    assertEquals(BLUE.bridgedPixels(), pixels, "the stripe button kept the old tool window icon")
  }

  @Test
  fun `same tool window icon keeps the same icon instance after updatePresentation`() {
    val button = stripeButton(RED).button
    val before = button.icon

    runInEdtAndWait { button.updatePresentation() }

    assertSame(before, button.icon, "the icon was rebuilt for the same tool window icon")
  }

  @Test
  fun `active icon paints the tool window icon in the given color`() {
    val button = stripeButton(MORE).button
    val active = button.activeIcon(Color.RED)
    val pixels = active.visiblePixels()

    assertTrue(pixels.isNotEmpty(), "the active icon painted nothing")
    assertTrue(pixels.all { it and 0xFFFFFF == 0xFF0000 }, "the active icon has pixels that are not red")
    assertEquals(
      MORE.legacyStripeIcon().bridgedPixels().map { it ushr 24 },
      pixels.map { it ushr 24 },
      "the active icon does not keep the shape of the tool window icon",
    )
  }

  @Test
  fun `active icon follows the color it gets`() {
    val button = stripeButton(MORE).button
    val pixels = button.activeIcon(Color.BLUE).visiblePixels()

    assertTrue(pixels.isNotEmpty(), "the active icon painted nothing")
    assertTrue(pixels.all { it and 0xFFFFFF == 0x0000FF }, "the active icon has pixels that are not blue")
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "false")
  fun `active icon is the legacy stroke icon when the experimental rendering is off`() {
    val button = stripeButton(MORE).button

    val pixels = button.activeIcon(Color.RED).visiblePixels()

    assertTrue(pixels.isNotEmpty(), "the active icon painted nothing")
    assertEquals(
      toStrokeIcon(button.icon, Color.RED).visiblePixels(),
      pixels,
      "the active icon is not the legacy stroke icon",
    )
  }

  @Test
  fun `same color keeps the same active icon instance`() {
    val button = stripeButton(MORE).button
    val first = button.activeIcon(Color.RED)
    val second = button.activeIcon(Color.RED)

    assertSame(first, second, "the active icon was rebuilt for the same color")
  }

  @Test
  fun `another color gives another active icon`() {
    val button = stripeButton(MORE).button
    val red = button.activeIcon(Color.RED)
    val blue = button.activeIcon(Color.BLUE)

    assertNotSame(red, blue, "the active icon kept the old color")
    assertNotEquals(red.visiblePixels(), blue.visiblePixels(), "both colors painted the same pixels")
  }

  @Test
  fun `another tool window icon gives another active icon`() {
    val (toolWindow, button) = stripeButton(MORE)
    val before = button.activeIcon(Color.RED)

    runInEdtAndWait {
      toolWindow.setIcon(CLOSE)
      button.updatePresentation()
    }

    val after = button.activeIcon(Color.RED)
    val pixels = after.visiblePixels()

    assertNotSame(before, after, "the active icon kept the old tool window icon")
    assertTrue(pixels.isNotEmpty(), "the active icon painted nothing")
    assertTrue(pixels.all { it and 0xFFFFFF == 0xFF0000 }, "the active icon has pixels that are not red")
    assertNotEquals(before.visiblePixels(), pixels, "both tool window icons painted the same pixels")
  }

  @Test
  fun `look paints an active tool window icon as the legacy look does`() {
    assertPaintedAsLegacy(MORE, active = true)
  }

  @Test
  fun `look paints an inactive tool window icon as the legacy look does`() {
    assertPaintedAsLegacy(MORE, active = false)
  }

  @Test
  fun `look paints an active tool window icon as the legacy look does when the theme has no selected foreground`() {
    // a theme that defines the color, which the test must hide
    val lookAndFeel = UIManager.getLookAndFeelDefaults()
    val themeColor = lookAndFeel[SELECTED_FOREGROUND_KEY]
    lookAndFeel[SELECTED_FOREGROUND_KEY] = Color.GREEN
    try {
      assertPaintedAsLegacy(MORE, active = true, selectedForeground = null)
    }
    finally {
      lookAndFeel[SELECTED_FOREGROUND_KEY] = themeColor
    }
  }

  @Test
  fun `look paints the unknown marker of an active tool window without an icon as the legacy look does`() {
    assertPaintedAsLegacy(null, active = true)
  }

  private data class Stripe(val toolWindow: ToolWindowImpl, val button: SquareStripeButton)

  /**
   * An [active] tool window is visible, has a decorator, and is the active tool window of its manager, as [ToolWindowImpl.isActive] needs.
   */
  private fun stripeButton(icon: Icon?, active: Boolean = false): Stripe = runInEdtAndGet {
    val project = project.get()
    val coroutineScope = (project as ComponentManagerEx).getCoroutineScope()
    // the platform manager reads the focus of the frame, and the test has no frame
    val manager = object : ToolWindowManagerImpl(project, ExperimentalUI.isNewUI(), isEdtRequired = true, coroutineScope) {
      override val activeToolWindowId: String?
        get() = if (active) STRIPE_ID else null
    }
    Disposer.register(disposable, manager)
    val toolWindow = ToolWindowImpl(
      toolWindowManager = manager,
      id = STRIPE_ID,
      canCloseContent = false,
      dumbAware = true,
      component = null,
      parentDisposable = disposable,
      windowInfo = WindowInfoImpl().apply { isVisible = active },
      contentFactory = null,
      stripeTitleProvider = Supplier { "Test" },
    )
    if (active) toolWindow.getOrCreateDecoratorComponent()
    check(toolWindow.isActive == active) { "the tool window is not in the requested active state" }
    if (icon != null) toolWindow.setIcon(icon)
    val button = SquareStripeButton(toolWindow)
    // also picks the look for the "Show Tool Window Names" setting
    button.updatePresentation()
    // what `ActionButton.addNotify` does when a toolbar adds the button
    button.updateIcon()
    Stripe(toolWindow, button)
  }
}

private const val STRIPE_ID = "TestStripe"

private fun SquareStripeButton.activeIcon(color: Color): Icon = computeActiveIcon(icon, color)

private fun SquareStripeButton.paintedRaster(): IntArray {
  // the look with tool window names measures its label, and a headless button has no font
  font = JBUI.Fonts.label()
  size = preferredSize
  val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()
  try {
    paint(g)
  }
  finally {
    g.dispose()
  }
  return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
}

/** The ARGB pixels of the icon, painted at its own size, that are not fully transparent. */
internal fun Icon.visiblePixels(): List<Int> {
  val image = BufferedImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()
  try {
    paintIcon(JPanel(), g, 0, 0)
  }
  finally {
    g.dispose()
  }
  return image.getRGB(0, 0, image.width, image.height, null, 0, image.width).filter { it ushr 24 != 0 }
}

private fun <T> withDarkTheme(action: () -> T): T {
  val wasBright = JBColor.isBright()
  JBColor.setDark(true)
  try {
    return action()
  }
  finally {
    JBColor.setDark(!wasBright)
  }
}

/** Paints the icon at scale 2 with the JRE HiDPI support on, as on a Retina screen. */
private fun Icon.rasterAtScale2(): IntArray {
  val wasJreHiDpi = JreHiDpiUtil.isJreHiDPIEnabled()
  TestScaleHelper.overrideJreHiDPIEnabled(true)
  try {
    val imageAndGraphics = TestScaleHelper.createImageAndGraphics(2.0, iconWidth, iconHeight)
    val image = imageAndGraphics.first
    val g = imageAndGraphics.second
    try {
      paintIcon(JPanel(), g, 0, 0)
    }
    finally {
      g.dispose()
    }
    return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
  }
  finally {
    TestScaleHelper.overrideJreHiDPIEnabled(wasJreHiDpi)
  }
}

private const val COMPACT_STRIPE_ICON_SIZE = 16

private fun <T> withStripeIconSize(size: Int, action: () -> T): T =
  withUiDefault(JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconSizeKey(), size, action)

private const val SELECTED_FOREGROUND_KEY = "ToolWindow.Button.selectedForeground"

private fun <T> withSelectedForeground(color: Color?, action: () -> T): T = withUiDefault(SELECTED_FOREGROUND_KEY, color, action)

/**
 * Sets [key] in the user defaults and in the look and feel defaults, and restores both after [action]. A `null` [value] hides a value of
 * the theme too. `UIManager.getDefaults().get` falls back to the look and feel defaults, so only `containsKey` tells the own value of the
 * user defaults.
 */
private fun <T> withUiDefault(key: String, value: Any?, action: () -> T): T {
  val user = UIManager.getDefaults()
  val lookAndFeel = UIManager.getLookAndFeelDefaults()
  val oldUser = if (user.containsKey(key)) user[key] else null
  val oldLookAndFeel = lookAndFeel[key]
  user[key] = value
  lookAndFeel[key] = value
  try {
    return action()
  }
  finally {
    lookAndFeel[key] = oldLookAndFeel
    user[key] = oldUser
  }
}

private class SquareIcon(private val color: Color) : ScalableIcon {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    g.color = color
    g.fillRect(x + 2, y + 2, 12, 12)
  }

  override fun getIconWidth(): Int = 16

  override fun getIconHeight(): Int = 16

  override fun getScale(): Float = 1f

  override fun scale(scaleFactor: Float): Icon = this
}

private fun Icon.bridgedPixels(): List<Int> = toIconDescriptor().createSwingIcon().visiblePixels()

private const val STRIPE_ICON_SIZE = 20

private fun Icon.legacyStripeIcon(size: Int = STRIPE_ICON_SIZE): Icon {
  if (this !is HoledIcon) return loadIconCustomVersionOrScale(this as ScalableIcon, size)
  return replaceBy(object : IconReplacer {
    override fun replaceIcon(icon: Icon): Icon = loadIconCustomVersionOrScale(icon as ScalableIcon, size)
  })
}