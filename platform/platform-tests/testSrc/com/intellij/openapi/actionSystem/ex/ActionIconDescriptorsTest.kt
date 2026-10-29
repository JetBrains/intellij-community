// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.isGray
import com.intellij.openapi.actionSystem.maxAlpha
import com.intellij.openapi.actionSystem.paintToImage
import com.intellij.openapi.actionSystem.unknownMarkerPixels
import com.intellij.openapi.actionSystem.visiblePixels
import com.intellij.openapi.actionSystem.withDarkTheme
import com.intellij.openapi.actionSystem.withGrayFilter
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.icons.RgbImageFilterSupplier
import com.intellij.ui.icons.getDisabledIcon
import com.intellij.util.ui.GrayFilter
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class ActionIconDescriptorsTest {
  companion object {
    private val PARITY_ICONS = listOf("actions/execute.svg", "actions/close.svg", "general/add.svg", "expui/toolbar/unknown.svg")

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
  fun `effective returns the presentation descriptor`() {
    val descriptor = executeDescriptor()
    val presentation = Presentation()
    presentation.iconDescriptor = descriptor
    presentation.icon = AllIcons.General.Add

    assertSame(descriptor, ActionIconDescriptors.effective(presentation))
  }

  @Test
  fun `effective returns null when the presentation has no icon`() {
    assertNull(ActionIconDescriptors.effective(Presentation()))
  }

  @Test
  fun `effective returns the unknown marker when only a legacy icon is set`() {
    val first = Presentation().apply { icon = AllIcons.General.Add }
    val second = Presentation().apply { icon = AllIcons.General.Remove }

    val marker = assertNotNull(ActionIconDescriptors.effective(first))
    assertSame(marker, ActionIconDescriptors.effective(second))
    assertEquals(unknownMarkerPixels(), marker.paint().visiblePixels(), "effective did not return the unknown marker")
  }

  @Test
  fun `disabled variant is gray`() {
    val base = executeDescriptor().paint().visiblePixels()
    assertTrue(base.any { !isGray(it) }, "the base icon is already gray, so the test proves nothing")

    val disabled = ActionIconDescriptors.disabled(executeDescriptor()).paint().visiblePixels()
    assertTrue(disabled.isNotEmpty(), "the disabled variant painted nothing")
    assertTrue(disabled.all { isGray(it) }, "the disabled variant has colored pixels")
  }

  @Test
  fun `disabled variant follows the alpha of the gray filter`() {
    assertEquals(255, executeDescriptor().paint().maxAlpha())

    val alpha = withGrayFilter(GrayFilter(0, 0, 50)) { ActionIconDescriptors.disabled(executeDescriptor()).paint().maxAlpha() }

    assertEquals(255 * 50 / 100, alpha, "the disabled variant did not scale the alpha of the pixels")
  }

  @Test
  fun `disabled variant differs between the light and the dark theme`() {
    val light = ActionIconDescriptors.disabled(executeDescriptor()).paint().visiblePixels()
    val dark = withDarkTheme { ActionIconDescriptors.disabled(executeDescriptor()).paint().visiblePixels() }

    assertTrue(light.isNotEmpty() && dark.isNotEmpty(), "a disabled variant painted nothing")
    assertNotEquals(light, dark, "both themes painted the same disabled variant")
  }

  @Test
  fun `disabled variant matches the legacy disabled icon with the gray filter of the light theme`() {
    for (path in PARITY_ICONS) assertMatchesLegacyDisabledIcon(path, GrayFilter(33, -35, 100))
  }

  @Test
  fun `disabled variant matches the legacy disabled icon with the gray filter of the dark theme`() {
    for (path in PARITY_ICONS) assertMatchesLegacyDisabledIcon(path, GrayFilter(-70, -70, 100))
  }

  private fun executeDescriptor(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun assertMatchesLegacyDisabledIcon(path: String, filter: GrayFilter) {
    val classLoader = AllIcons::class.java.classLoader
    val legacyDisabled = getDisabledIcon(IconLoader.getIcon(path, classLoader), object : RgbImageFilterSupplier {
      override fun getFilter() = filter
    })
    val expected = legacyDisabled.paintToImage().visiblePixels()
    val actual =
      withGrayFilter(filter) { imageIconDescriptor(path, classLoader).let { ActionIconDescriptors.disabled(it).paint().visiblePixels() } }

    assertTrue(expected.isNotEmpty(), "the legacy disabled icon of $path painted nothing")
    assertEquals(expected, actual, "the disabled variant of $path does not match the legacy disabled icon")
  }

  private fun IconDescriptor.paint(): BufferedImage = createSwingIcon().paintToImage()
}
