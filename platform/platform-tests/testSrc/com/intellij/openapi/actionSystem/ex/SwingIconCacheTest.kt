// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.isGray
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
import javax.swing.Icon
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
class SwingIconCacheTest {
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
  fun `a new cache has no icons`() {
    val cache = SwingIconCache()
    assertNull(cache.icon, "a new cache has an icon")
    assertNull(cache.disabled, "a new cache has a disabled icon")
  }

  @Test
  fun `icon paints`() {
    val cache = SwingIconCache().apply { update(executeDescriptor()) }
    assertTrue(cache.icon.pixels().isNotEmpty(), "the icon painted nothing")
  }

  @Test
  fun `disabled icon is gray and differs from the base icon`() {
    val cache = SwingIconCache().apply { update(executeDescriptor()) }
    val basePixels = assertNotNull(cache.icon).paintToImage().visiblePixels()
    val disabledPixels = assertNotNull(cache.disabled).paintToImage().visiblePixels()

    assertTrue(basePixels.any { !isGray(it) }, "the base icon is already gray, so the test proves nothing")
    assertTrue(disabledPixels.isNotEmpty(), "the disabled icon painted nothing")
    assertTrue(disabledPixels.all { isGray(it) }, "the disabled icon has colored pixels")
  }

  @Test
  fun `disabled icon is cached`() {
    val cache = SwingIconCache().apply { update(executeDescriptor()) }
    assertSame(cache.disabled, cache.disabled, "the disabled icon was rebuilt")
  }

  @Test
  fun `an equal descriptor and the same theme keep the icons`() {
    val cache = SwingIconCache().apply { update(executeDescriptor()) }
    val icon = cache.icon
    val disabled = cache.disabled

    cache.update(executeDescriptor())

    assertSame(icon, cache.icon, "the icon was rebuilt")
    assertSame(disabled, cache.disabled, "the disabled icon was rebuilt")
  }

  @Test
  fun `a changed descriptor gives another icon`() {
    val cache = SwingIconCache().apply { update(executeDescriptor()) }
    val first = assertNotNull(cache.icon)
    val firstPixels = first.paintToImage().visiblePixels()

    cache.update(closeDescriptor())

    val second = assertNotNull(cache.icon)
    val secondPixels = second.paintToImage().visiblePixels()
    assertNotSame(first, second, "the icon was kept for another descriptor")
    assertTrue(firstPixels.isNotEmpty() && secondPixels.isNotEmpty(), "an icon painted nothing")
    assertNotEquals(firstPixels, secondPixels, "the cache still paints the old descriptor")
  }

  @Test
  fun `a changed descriptor gives another disabled icon`() {
    val cache = SwingIconCache().apply { update(executeDescriptor()) }
    val before = assertNotNull(cache.disabled)
    val beforePixels = before.paintToImage().visiblePixels()

    cache.update(closeDescriptor())

    val after = assertNotNull(cache.disabled)
    assertNotSame(before, after, "the old disabled icon was kept")
    assertNotEquals(beforePixels, after.paintToImage().visiblePixels(), "the disabled icon is still the old one")
  }

  @Test
  fun `a theme change gives another icon`() {
    val cache = SwingIconCache().apply { update(executeDescriptor()) }
    val first = cache.icon

    withDarkTheme { cache.update(executeDescriptor()) }

    assertNotSame(first, cache.icon, "the icon was kept after a theme change")
  }

  @Test
  fun `a null descriptor clears the icons`() {
    val cache = SwingIconCache().apply { update(executeDescriptor()) }
    assertNotNull(cache.disabled)

    cache.update(null)

    assertNull(cache.icon, "the icon was kept")
    assertNull(cache.disabled, "the disabled icon was kept")
  }

  private fun Icon?.pixels(): List<Int> = assertNotNull(this, "there is no icon").paintToImage().visiblePixels()

  private fun executeDescriptor(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun closeDescriptor(): IconDescriptor = imageIconDescriptor("actions/close.svg", AllIcons::class.java.classLoader)
}
