// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.platform.icons.imageIcon
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.swing.ScalableSwingIcon
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EmptyIcon
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

  private fun button(descriptor: com.intellij.platform.icons.Icon?): ActionButton {
    val action =
      object : AnAction({ "test" }, null, descriptor, null) {
        override fun actionPerformed(e: AnActionEvent) = Unit
      }
    val button = ActionButton(action, action.templatePresentation.clone(), "TestPlace", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    button.updateIcon()
    return button
  }

  @Test
  fun `descriptor is rendered through the new pipeline`() {
    val button = button(imageIcon("actions/close.svg", AllIcons::class.java.classLoader))
    val icon = button.icon
    assertIs<ScalableSwingIcon>(icon)
    assertEquals(16, icon.iconWidth)
    assertEquals(16, icon.iconHeight)

    val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      icon.paintIcon(button, g, 0, 0)
    } finally {
      g.dispose()
    }
    assertTrue(image.hasVisiblePixels(), "the new pipeline painted nothing")
  }

  @Test
  fun `missing descriptor paints an empty placeholder`() {
    assertSame(EmptyIcon.ICON_16, button(null).icon)
  }
}

/** True when at least one pixel is not fully transparent; partial alpha counts, so anti-aliased edges are enough. */
private fun BufferedImage.hasVisiblePixels(): Boolean {
  val argbPixels = getRGB(0, 0, width, height, null, 0, width)
  return argbPixels.any { alphaOf(it) != 0 }
}

/** The alpha byte of an ARGB pixel, 0 (transparent) to 255 (opaque). */
private fun alphaOf(argb: Int): Int = argb ushr 24
