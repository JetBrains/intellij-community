// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.hasVisiblePixels
import com.intellij.openapi.actionSystem.maxAlpha
import com.intellij.openapi.actionSystem.paintToImage
import com.intellij.openapi.actionSystem.visiblePixels
import com.intellij.openapi.actionSystem.withDarkTheme
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
class DescriptorIconsTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
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
  fun `icon paints`() {
    val icons = DescriptorIcons.reuseOrBuild(executeDescriptor(), null)
    assertTrue(icons.icon.paintToImage().hasVisiblePixels(), "the icon painted nothing")
  }

  @Test
  fun `disabled icon is fainter`() {
    val icons = DescriptorIcons.reuseOrBuild(executeDescriptor(), null)
    val baseAlpha = icons.icon.paintToImage().maxAlpha()
    val disabledAlpha = icons.disabled.paintToImage().maxAlpha()
    assertTrue(disabledAlpha < baseAlpha, "the disabled icon is not fainter than the base icon")
  }

  @Test
  fun `disabled icon is cached`() {
    val icons = DescriptorIcons.reuseOrBuild(executeDescriptor(), null)
    val first = icons.disabled
    val second = icons.disabled
    assertSame(second, first, "the disabled icon was rebuilt")
  }

  @Test
  fun `same descriptor and theme reuse the instance`() {
    val first = DescriptorIcons.reuseOrBuild(executeDescriptor(), null)
    val second = DescriptorIcons.reuseOrBuild(executeDescriptor(), first)
    assertSame(first, second, "the instance was not reused")
  }

  @Test
  fun `a changed descriptor builds a new instance`() {
    val first = DescriptorIcons.reuseOrBuild(executeDescriptor(), null)
    val second = DescriptorIcons.reuseOrBuild(closeDescriptor(), first)

    assertNotSame(first, second, "the instance was reused for another descriptor")

    val firstPixels = first.icon.paintToImage().visiblePixels()
    val secondPixels = second.icon.paintToImage().visiblePixels()

    assertTrue(firstPixels.isNotEmpty(), "the first icon painted nothing")
    assertTrue(secondPixels.isNotEmpty(), "the second icon painted nothing")
    assertNotEquals(firstPixels, secondPixels, "the new instance still paints the old descriptor")
  }

  @Test
  fun `a theme change builds a new instance`() {
    val first = DescriptorIcons.reuseOrBuild(executeDescriptor(), null)
    val second = withDarkTheme { DescriptorIcons.reuseOrBuild(executeDescriptor(), first) }

    assertNotSame(first, second, "the instance was reused after a theme change")
  }

  @Test
  fun `reuseOrNull of null is null`() {
    val old = DescriptorIcons.reuseOrBuild(executeDescriptor(), null)

    assertNull(DescriptorIcons.reuseOrNull(null, old), "a missing descriptor still gave icons")
  }

  @Test
  fun `reuseOrNull reuses the instance for an equal descriptor`() {
    val old = DescriptorIcons.reuseOrBuild(executeDescriptor(), null)

    assertSame(old, DescriptorIcons.reuseOrNull(executeDescriptor(), old), "the instance was not reused")
  }

  private fun executeDescriptor(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun closeDescriptor(): IconDescriptor = imageIconDescriptor("actions/close.svg", AllIcons::class.java.classLoader)
}
