// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.impl.visiblePixels
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.swing.ScalableSwingIcon
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.platform.icons.swing.toIconDescriptor
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.IconManager
import com.intellij.ui.icons.CoreIconManager
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class MoreSquareStripeButtonIconDescriptorTest {
  companion object {
    private var iconsActivated = false

    private const val STRIPE_ICON_SIZE = 20

    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      // only IDE bootstrap activates them, and only when not headless
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

  @Test
  fun `more button paints the more icon`() {
    val button = runInEdtAndGet { shownMoreButton() }
    val icon = assertIs<ScalableSwingIcon>(button.icon)
    val pixels = icon.visiblePixels()

    assertTrue(pixels.isNotEmpty(), "the more button painted nothing")
    assertEquals(moreIconPixels(), pixels, "the more button did not paint the more icon")
  }

  @Test
  fun `updateUI gives the more button the more icon`() {
    val button = runInEdtAndGet { moreButton().also { it.updateUI() } }

    assertEquals(moreIconPixels(), button.icon.visiblePixels(), "updateUI did not set the more icon")
  }

  @Test
  fun `more button icon is as big as the stripe icon`() {
    val button = runInEdtAndGet { shownMoreButton() }
    val icon = assertIs<ScalableSwingIcon>(button.icon)

    assertEquals(STRIPE_ICON_SIZE, icon.iconWidth, "the more icon is not as wide as the stripe size")
    assertEquals(STRIPE_ICON_SIZE, icon.iconHeight, "the more icon is not as high as the stripe size")
  }

  private fun moreButton(): MoreSquareStripeButton = ToolWindowLeftToolbar(paneId = "TestPane", isPrimary = true).moreButton

  private fun shownMoreButton(): MoreSquareStripeButton = moreButton().also { it.updateIcon() }

  private fun moreIconPixels(): List<Int> {
    val more = AllIcons.Actions.MoreHorizontal
    val legacy = loadIconCustomVersionOrScale(more as ScalableIcon, STRIPE_ICON_SIZE)
    val pixels = legacy.toIconDescriptor().createSwingIcon().visiblePixels()
    check(pixels.isNotEmpty()) { "the @20x20 artwork of the more icon painted nothing" }
    return pixels
  }
}
