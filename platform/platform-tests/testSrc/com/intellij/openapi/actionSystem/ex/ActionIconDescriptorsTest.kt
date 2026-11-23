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
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.imageFileIconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.scale.fitArea
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.scale.JBUIScale
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
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
    // a presentation with both sets the icon first, because a later icon replaces the descriptor
    presentation.icon = AllIcons.General.Add
    presentation.iconDescriptor = descriptor

    assertSame(descriptor, ActionIconDescriptors.effective(presentation))
  }

  @Test
  fun `the disabled variant of an oversized custom icon keeps the fitted size`(@TempDir dir: Path) {
    val file = dir.resolve("custom36.svg")
    Files.writeString(file, """<svg xmlns="http://www.w3.org/2000/svg" width="36" height="36"><rect width="36" height="36" fill="#FF0000"/></svg>""")
    // as CustomActionsSchema fits a custom icon of any size
    val custom = imageFileIconDescriptor(file, IconModifier.scale(fitArea(18.dp, 18.dp, upscale = false)))

    val disabled = ActionIconDescriptors.disabled(custom).createSwingIcon()

    assertEquals(JBUIScale.scale(18), disabled.iconWidth)
    assertEquals(JBUIScale.scale(18), disabled.iconHeight)
  }

  @Test
  fun `effective returns null when the presentation has no icon`() {
    assertNull(ActionIconDescriptors.effective(Presentation()))
  }

  @Test
  fun `effective returns the bridged descriptor when only a legacy icon is set`() {
    val presentation = Presentation().apply { icon = AllIcons.General.Add }

    val bridged = assertNotNull(ActionIconDescriptors.effective(presentation))
    assertSame(presentation.iconDescriptor, bridged)
    assertNotEquals(unknownMarkerPixels(), bridged.paint().visiblePixels(), "effective returned the unknown marker")
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
  fun `disabled variant is translucent and more so on a dark theme`() {
    val baseAlpha = executeDescriptor().paint().maxAlpha()
    val brightAlpha = ActionIconDescriptors.disabled(executeDescriptor()).paint().maxAlpha()
    val darkAlpha = withDarkTheme { ActionIconDescriptors.disabled(executeDescriptor()).paint().maxAlpha() }

    assertEquals(255, baseAlpha)
    assertTrue(brightAlpha < baseAlpha, "bright theme: disabled alpha $brightAlpha is not below $baseAlpha")
    assertTrue(darkAlpha < brightAlpha, "dark theme: disabled alpha $darkAlpha is not below $brightAlpha")
  }

  private fun executeDescriptor(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun IconDescriptor.paint(): BufferedImage = createSwingIcon().paintToImage()
}
