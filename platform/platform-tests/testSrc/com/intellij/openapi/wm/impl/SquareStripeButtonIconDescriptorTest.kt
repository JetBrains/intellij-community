// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.alphaOf
import com.intellij.openapi.actionSystem.paintToImage
import com.intellij.openapi.actionSystem.unknownMarkerPixels
import com.intellij.openapi.actionSystem.visiblePixels
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.UIManager
import java.util.function.Supplier
import javax.swing.Icon
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class SquareStripeButtonIconDescriptorTest {
  companion object {
    private val project = projectFixture()

    private val RED = SquareIcon(Color.RED)
    private val BLUE = SquareIcon(Color.BLUE)

    private val MORE by lazy { IconLoader.getIcon("expui/general/moreHorizontal.svg", AllIcons::class.java.classLoader) }
    private val CLOSE by lazy { IconLoader.getIcon("expui/general/close.svg", AllIcons::class.java.classLoader) }

    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      // only IDE bootstrap activates them, and only when not headless
      IntelliJIconManager.activate()
      IconLoader.activate()
    }

    @JvmStatic
    @AfterAll
    fun deactivateIcons() {
      IconLoader.deactivate()
    }
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `base icon is the bridged tool window icon`() {
    val button = stripeButton(RED).button
    val icon = assertIs<ScalableSwingIcon>(button.icon)
    val pixels = icon.paintToImage().visiblePixels()

    assertTrue(pixels.isNotEmpty(), "the stripe button painted nothing")
    assertEquals(RED.bridgedPixels(), pixels, "the stripe button did not paint the tool window icon")
    assertNotEquals(unknownMarkerPixels(), pixels, "the stripe button painted the unknown marker")
  }

  @Test
  fun `tool window without an icon shows the unknown marker`() {
    val button = stripeButton(null).button
    val icon = assertIs<ScalableSwingIcon>(button.icon)

    assertEquals(unknownMarkerPixels(), icon.paintToImage().visiblePixels(), "the stripe button did not paint the unknown marker")
  }

  @Test
  fun `updatePresentation follows a changed tool window icon`() {
    val (toolWindow, button) = stripeButton(RED)

    runInEdtAndWait {
      toolWindow.setIcon(BLUE)
      updateSquareStripeButtonPresentationForTest(button)
      button.updateIcon()
    }

    val pixels = button.icon.paintToImage().visiblePixels()
    assertEquals(BLUE.bridgedPixels(), pixels, "the stripe button kept the old tool window icon")
  }

  @Test
  fun `updateUI follows a changed tool window icon`() {
    val (toolWindow, button) = stripeButton(RED)

    runInEdtAndWait {
      toolWindow.setIcon(BLUE)
      button.updateUI()
      button.updateIcon()
    }

    val pixels = button.icon.paintToImage().visiblePixels()
    assertEquals(BLUE.bridgedPixels(), pixels, "the stripe button kept the old tool window icon")
  }

  @Test
  fun `same tool window icon keeps the same icon instance after updatePresentation`() {
    val button = stripeButton(RED).button
    val before = button.icon

    runInEdtAndWait {
      updateSquareStripeButtonPresentationForTest(button)
      button.updateIcon()
    }

    assertSame(before, button.icon, "the icon was rebuilt for the same tool window icon")
  }

  @Test
  fun `active icon paints the tool window icon in the given color`() {
    val button = stripeButton(MORE).button
    val active = assertNotNull(activeStripeIconForTest(button, Color.RED))
    val pixels = active.paintToImage().visiblePixels()

    assertTrue(pixels.isNotEmpty(), "the active icon painted nothing")
    assertTrue(pixels.all { it and 0xFFFFFF == 0xFF0000 }, "the active icon has pixels that are not red")
    assertEquals(
      MORE.bridgedPixels().map { alphaOf(it) },
      pixels.map { alphaOf(it) },
      "the active icon does not keep the shape of the tool window icon",
    )
  }

  @Test
  fun `active icon follows the color it gets`() {
    val button = stripeButton(MORE).button
    val pixels = assertNotNull(activeStripeIconForTest(button, Color.BLUE)).paintToImage().visiblePixels()

    assertTrue(pixels.isNotEmpty(), "the active icon painted nothing")
    assertTrue(pixels.all { it and 0xFFFFFF == 0x0000FF }, "the active icon has pixels that are not blue")
  }

  @Test
  fun `button without an icon descriptor has no active icon`() {
    val button = stripeButton(null).button

    assertNull(activeStripeIconForTest(button, Color.RED))
  }

  @Test
  fun `same color keeps the same active icon instance`() {
    val button = stripeButton(MORE).button
    val first = activeStripeIconForTest(button, Color.RED)
    val second = activeStripeIconForTest(button, Color.RED)

    assertSame(first, second, "the active icon was rebuilt for the same color")
  }

  @Test
  fun `another color gives another active icon`() {
    val button = stripeButton(MORE).button
    val red = assertNotNull(activeStripeIconForTest(button, Color.RED))
    val blue = assertNotNull(activeStripeIconForTest(button, Color.BLUE))

    assertNotSame(red, blue, "the active icon kept the old color")
    assertNotEquals(red.paintToImage().visiblePixels(), blue.paintToImage().visiblePixels(), "both colors painted the same pixels")
  }

  @Test
  fun `another tool window icon gives another active icon`() {
    val (toolWindow, button) = stripeButton(MORE)
    val before = assertNotNull(activeStripeIconForTest(button, Color.RED))

    runInEdtAndWait {
      toolWindow.setIcon(CLOSE)
      updateSquareStripeButtonPresentationForTest(button)
    }

    val after = assertNotNull(activeStripeIconForTest(button, Color.RED))
    val pixels = after.paintToImage().visiblePixels()

    assertNotSame(before, after, "the active icon kept the old tool window icon")
    assertTrue(pixels.isNotEmpty(), "the active icon painted nothing")
    assertTrue(pixels.all { it and 0xFFFFFF == 0xFF0000 }, "the active icon has pixels that are not red")
    assertNotEquals(before.paintToImage().visiblePixels(), pixels, "both tool window icons painted the same pixels")
  }

  @Test
  fun `look paints the icon of a focused button in the selected foreground`() {
    val button = stripeButton(MORE).button
    setSquareStripeButtonFocusedForTest(button, true)

    val pixels = withSelectedForeground(Color.RED) { button.paintedByLook() }

    assertTrue(pixels.isNotEmpty(), "the look painted nothing")
    assertTrue(pixels.all { it and 0xFFFFFF == 0xFF0000 }, "the look painted pixels that are not red")
  }

  @Test
  fun `look paints the plain icon of a button that is not focused`() {
    val button = stripeButton(MORE).button
    setSquareStripeButtonFocusedForTest(button, false)

    val pixels = withSelectedForeground(Color.RED) { button.paintedByLook() }

    assertEquals(MORE.bridgedPixels(), pixels, "the look recolored an icon that is not focused")
  }

  @Test
  fun `look paints the plain icon when the theme has no selected foreground`() {
    val button = stripeButton(MORE).button
    setSquareStripeButtonFocusedForTest(button, true)

    val pixels = withSelectedForeground(null) { button.paintedByLook() }

    assertEquals(MORE.bridgedPixels(), pixels, "the look recolored the icon without a color")
  }

  @Test
  fun `look keeps the unknown marker of a focused button without an icon`() {
    val button = stripeButton(null).button
    setSquareStripeButtonFocusedForTest(button, true)

    val pixels = withSelectedForeground(Color.RED) { button.paintedByLook() }

    assertEquals(unknownMarkerPixels(), pixels, "the look changed the unknown marker")
  }

  private data class Stripe(val toolWindow: ToolWindowImpl, val button: ActionButton)

  private fun stripeButton(icon: Icon?): Stripe = runInEdtAndGet {
    val project = project.get()
    val manager = ToolWindowManagerImpl(project, (project as ComponentManagerEx).getCoroutineScope())
    Disposer.register(disposable, manager)
    val toolWindow = ToolWindowImpl(
      toolWindowManager = manager,
      id = "TestStripe",
      canCloseContent = false,
      dumbAware = true,
      component = null,
      parentDisposable = disposable,
      windowInfo = WindowInfoImpl(),
      contentFactory = null,
      stripeTitleProvider = Supplier { "Test" },
    )
    if (icon != null) toolWindow.setIcon(icon)
    val button = createSquareStripeButtonForTest(toolWindow)
    button.updateIcon()
    Stripe(toolWindow, button)
  }
}

private fun ActionButton.paintedByLook(): List<Int> {
  setSize(40, 40)
  val image = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()
  try {
    paintSquareStripeButtonIconForTest(this, g)
  }
  finally {
    g.dispose()
  }
  return image.visiblePixels()
}

private fun <T> withSelectedForeground(color: Color?, action: () -> T): T {
  val key = "ToolWindow.Button.selectedForeground"
  val old = UIManager.get(key)
  UIManager.put(key, color)
  try {
    return action()
  }
  finally {
    UIManager.put(key, old)
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

private fun Icon.bridgedPixels(): List<Int> = toIconDescriptor().createSwingIcon().paintToImage().visiblePixels()
