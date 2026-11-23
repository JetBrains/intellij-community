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
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
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
