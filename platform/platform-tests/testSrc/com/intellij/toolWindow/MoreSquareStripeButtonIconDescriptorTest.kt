// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.actionSystem.paintToImage
import com.intellij.openapi.actionSystem.unknownMarkerPixels
import com.intellij.openapi.actionSystem.visiblePixels
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.swing.ScalableSwingIcon
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class MoreSquareStripeButtonIconDescriptorTest {
  companion object {
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

  @Test
  fun `more button paints the more icon`() {
    val button = runInEdtAndGet { createMoreSquareStripeButtonForTest().also { it.updateIcon() } }
    val icon = assertIs<ScalableSwingIcon>(button.icon)
    val pixels = icon.paintToImage().visiblePixels()

    assertTrue(pixels.isNotEmpty(), "the more button painted nothing")
    assertEquals(moreIconPixels(), pixels, "the more button did not paint the more icon")
    assertNotEquals(unknownMarkerPixels(), pixels, "the more button painted the unknown marker")
  }

  @Test
  fun `more button keeps the more icon after updateUI`() {
    val button = runInEdtAndGet {
      createMoreSquareStripeButtonForTest().also {
        it.updateUI()
        it.updateIcon()
      }
    }

    assertEquals(moreIconPixels(), button.icon.paintToImage().visiblePixels(), "the more button lost the more icon")
  }

  private fun moreIconPixels(): List<Int> {
    val descriptor = imageIconDescriptor("expui/general/moreHorizontal.svg", AllIcons::class.java.classLoader)
    return descriptor.createSwingIcon().paintToImage().visiblePixels()
  }
}
