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
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.swing.ScalableSwingIcon
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class ComboBoxActionIconDescriptorTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      // only IDE bootstrap activates it, and only when not headless
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
  fun `initial descriptor shows the descriptor icon`() {
    val button = button(presentation(executeDescriptor()))
    val pixels = assertIs<ScalableSwingIcon>(button.icon).paintToImage().visiblePixels()
    assertTrue(pixels.isNotEmpty(), "the icon descriptor painted nothing")
  }

  @Test
  fun `the disabled icon is explicit and fainter`() {
    val button = button(presentation(executeDescriptor()))
    val base = assertIs<ScalableSwingIcon>(button.icon).paintToImage()
    val disabled = assertIs<ScalableSwingIcon>(button.disabledIcon).paintToImage()
    assertTrue(disabled.maxAlpha() < base.maxAlpha(), "the disabled icon is not fainter")
  }

  @Test
  fun `a descriptor change gives a new icon`() {
    val presentation = presentation(executeDescriptor())
    val button = button(presentation)
    val initialBaseIcon = assertIs<ScalableSwingIcon>(button.icon).paintToImage().visiblePixels()
    val initialDisabledIcon = assertIs<ScalableSwingIcon>(button.disabledIcon).paintToImage().visiblePixels()

    presentation.iconDescriptor = closeDescriptor()

    val updatedBaseIcon = assertIs<ScalableSwingIcon>(button.icon).paintToImage().visiblePixels()
    val updatedDisabledIcon = assertIs<ScalableSwingIcon>(button.disabledIcon).paintToImage().visiblePixels()

    assertTrue(initialBaseIcon.isNotEmpty(), "the initial base icon painted nothing")
    assertTrue(updatedBaseIcon.isNotEmpty(), "the updated base icon painted nothing")

    assertNotEquals(initialBaseIcon, updatedBaseIcon, "the base icon did not change")

    assertTrue(initialDisabledIcon.isNotEmpty(), "the initial disabled icon painted nothing")
    assertTrue(updatedDisabledIcon.isNotEmpty(), "the updated disabled icon painted nothing")

    assertNotEquals(initialDisabledIcon, updatedDisabledIcon, "the disabled icon did not change")
  }

  @Test
  fun `a legacy icon change shows the unknown marker`() {
    val presentation = presentation()
    val button = button(presentation)

    assertNull(button.icon)

    presentation.icon = AllIcons.General.Add
    val actual = assertIs<ScalableSwingIcon>(button.icon).paintToImage().visiblePixels()

    assertEquals(unknownMarkerPixels(), actual, "the button did not show the unknown marker")
  }

  @Test
  fun `a legacy icon event keeps the cached icons`() {
    val presentation = presentation(executeDescriptor())
    val button = button(presentation)
    val initialBaseIcon = assertIs<ScalableSwingIcon>(button.icon)
    val initialDisabledIcon = assertIs<ScalableSwingIcon>(button.disabledIcon)

    presentation.icon = AllIcons.General.Add

    assertSame(initialBaseIcon, button.icon)
    assertSame(initialDisabledIcon, button.disabledIcon)
  }

  @Test
  fun `a descriptor that comes back after a removal shows the icon again`() {
    val presentation = presentation(executeDescriptor())
    val button = button(presentation)
    presentation.iconDescriptor = null

    assertNull(button.icon, "the base icon of the removed descriptor stayed")
    assertNull(button.disabledIcon, "the disabled icon of the removed descriptor stayed")

    presentation.iconDescriptor = executeDescriptor()

    val icon = assertIs<ScalableSwingIcon>(button.icon).paintToImage().visiblePixels()
    assertTrue(icon.isNotEmpty(), "the base icon painted nothing")

    val disabled = assertIs<ScalableSwingIcon>(button.disabledIcon).paintToImage().visiblePixels()
    assertTrue(disabled.isNotEmpty(), "the disabled icon painted nothing")
    assertTrue(disabled.all { isGray(it) }, "the disabled icon painted colored pixels")
  }

  @Test
  fun `a theme change rebuilds the disabled icon`() {
    val presentation = presentation(executeDescriptor())
    val button = button(presentation)

    val brightAlpha = assertIs<ScalableSwingIcon>(button.disabledIcon).paintToImage().maxAlpha()

    val darkAlpha = withDarkTheme {
      runInEdtAndWait { button.updateUI() }
      assertIs<ScalableSwingIcon>(button.disabledIcon).paintToImage().maxAlpha()
    }

    assertTrue(darkAlpha < brightAlpha, "the disabled icon was not rebuilt for the dark theme")
  }

  private fun button(presentation: Presentation): ComboBoxAction.ComboBoxButton {
    val action = object : ComboBoxAction() {
      fun open(p: Presentation) = createComboBoxButton(p)
    }
    return runInEdtAndGet { action.open(presentation) }
  }

  private fun presentation(descriptor: IconDescriptor? = null): Presentation =
    Presentation().apply {
      text = "test"
      iconDescriptor = descriptor
    }

  private fun executeDescriptor(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun closeDescriptor(): IconDescriptor = imageIconDescriptor("actions/close.svg", AllIcons::class.java.classLoader)
}
