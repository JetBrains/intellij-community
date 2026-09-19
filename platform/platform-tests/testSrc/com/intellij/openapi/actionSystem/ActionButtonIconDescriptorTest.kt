// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.swing.ScalableSwingIcon
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.image.BufferedImage
import javax.swing.Icon
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIcons.REGISTRY_KEY, "true")
class ActionButtonIconDescriptorTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      // only IDE bootstrap activates it, and only when not headless
      IntelliJIconManager.activate()
    }
  }

  private fun button(descriptor: IconDescriptor?): ActionButton {
    val action =
      object : AnAction({ "test" }, null, descriptor, null) {
        override fun actionPerformed(e: AnActionEvent) = Unit
      }
    val button = ActionButton(action, action.templatePresentation.clone(), "TestPlace", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    button.updateIcon()
    return button
  }

  /** A descriptor-backed button whose presentation also carries legacy hovered and disabled icons. */
  private fun buttonWithLegacyStateIcons(): ActionButton {
    val button = button(closeDescriptor())
    button.presentation.hoveredIcon = AllIcons.General.Add
    button.presentation.disabledIcon = AllIcons.General.Remove
    return button
  }

  private fun closeDescriptor(): IconDescriptor = imageIconDescriptor("actions/close.svg", AllIcons::class.java.classLoader)

  @Test
  fun `descriptor is rendered through the new pipeline`() {
    val button = button(closeDescriptor())
    val icon = button.icon
    assertIs<ScalableSwingIcon>(icon)
    assertEquals(16, icon.iconWidth)
    assertEquals(16, icon.iconHeight)
    assertTrue(icon.paintToImage(button).hasVisiblePixels(), "the new pipeline painted nothing")
  }

  @Test
  fun `missing descriptor paints the unknown marker`() {
    val button = button(null)
    val icon = button.icon
    assertIs<ScalableSwingIcon>(icon)
    assertEquals(16, icon.iconWidth)
    assertEquals(16, icon.iconHeight)
    assertTrue(icon.paintToImage(button).hasVisiblePixels(), "the unknown marker painted nothing")
  }

  @Test
  fun `selected state keeps the descriptor icon instead of the legacy hovered icon`() {
    val button = buttonWithLegacyStateIcons()
    val descriptorIcon = button.icon

    // a selected button is PUSHED, the state that used to pick the legacy hovered icon
    Toggleable.setSelected(button.presentation, true)

    assertSame(descriptorIcon, button.icon)
  }

  @Test
  fun `disabled state derives from the descriptor instead of the legacy disabled icon`() {
    val button = buttonWithLegacyStateIcons()
    val enabledIcon = button.icon

    button.presentation.isEnabled = false
    val disabledIcon = button.icon

    assertNotSame(AllIcons.General.Remove, disabledIcon)
    assertNotSame(enabledIcon, disabledIcon)
    assertTrue(disabledIcon.paintToImage(button).hasVisiblePixels(), "the disabled descriptor icon painted nothing")
  }
}

/** Paints the icon at its own size into a transparent ARGB image. */
private fun Icon.paintToImage(component: Component): BufferedImage {
  val image = BufferedImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()
  try {
    paintIcon(component, g, 0, 0)
  }
  finally {
    g.dispose()
  }
  return image
}

/** True when at least one pixel is not fully transparent; partial alpha counts, so anti-aliased edges are enough. */
private fun BufferedImage.hasVisiblePixels(): Boolean {
  val argbPixels = getRGB(0, 0, width, height, null, 0, width)
  return argbPixels.any { alphaOf(it) != 0 }
}

/** The alpha byte of an ARGB pixel, 0 (transparent) to 255 (opaque). */
private fun alphaOf(argb: Int): Int = argb ushr 24
