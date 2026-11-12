// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.actionSystem.Presentation
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class AbstractToolbarComboIconDescriptorTest {
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
  fun `enabled presentation shows the descriptor icon`() {
    val presentation = presentation(executeDescriptor())
    val widget = widget()
    widget.updateFromPresentation(presentation)

    val pixels = assertIs<ScalableSwingIcon>(widget.leftIcons.single()).paintToImage().visiblePixels()

    assertTrue(pixels.isNotEmpty(), "the icon painted nothing")
  }

  @Test
  fun `disabled presentation shows a fainter icon`() {
    val presentation = presentation(executeDescriptor())
    val widget = widget()
    widget.updateFromPresentation(presentation)

    val enabled = assertIs<ScalableSwingIcon>(widget.leftIcons.single()).paintToImage()

    presentation.isEnabled = false
    widget.updateFromPresentation(presentation)

    val disabled = assertIs<ScalableSwingIcon>(widget.leftIcons.single()).paintToImage()

    assertTrue(disabled.maxAlpha() < enabled.maxAlpha(), "the disabled icon is not fainter")
  }

  @Test
  fun `icon returns to normal after a disable and enable`() {
    val presentation = presentation(executeDescriptor())
    val widget = widget()
    widget.updateFromPresentation(presentation)

    val base = assertIs<ScalableSwingIcon>(widget.leftIcons.single()).paintToImage().visiblePixels()

    presentation.isEnabled = false
    widget.updateFromPresentation(presentation)

    val disabled = assertIs<ScalableSwingIcon>(widget.leftIcons.single()).paintToImage().visiblePixels()

    presentation.isEnabled = true
    widget.updateFromPresentation(presentation)

    val enabled = assertIs<ScalableSwingIcon>(widget.leftIcons.single()).paintToImage().visiblePixels()

    assertTrue(base.isNotEmpty(), "the base painted nothing")
    assertTrue(disabled.isNotEmpty(), "the disabled painted nothing")
    assertTrue(enabled.isNotEmpty(), "the enabled painted nothing")

    assertEquals(base, enabled)
    assertNotEquals(base, disabled)
  }

  @Test
  fun `same descriptor gives the same instance`() {
    val presentation = presentation(executeDescriptor())
    val widget = widget()
    widget.updateFromPresentation(presentation)

    val first = assertIs<ScalableSwingIcon>(widget.leftIcons.single())

    var events = 0

    widget.addPropertyChangeListener("leftIcons") { events++ }
    widget.updateFromPresentation(presentation)

    assertEquals(0, events)
    assertSame(first, widget.leftIcons.single())
  }

  @Test
  fun `a changed descriptor gives a new icon`() {
    val presentation = presentation(executeDescriptor())
    val widget = widget()
    widget.updateFromPresentation(presentation)

    val first = assertIs<ScalableSwingIcon>(widget.leftIcons.single())

    presentation.iconDescriptor = closeDescriptor()
    widget.updateFromPresentation(presentation)

    val updated = assertIs<ScalableSwingIcon>(widget.leftIcons.single())

    assertNotSame(first, updated)

    val firstPixels = first.paintToImage().visiblePixels()
    val updatedPixels = updated.paintToImage().visiblePixels()

    assertTrue(firstPixels.isNotEmpty(), "the first icon painted nothing")
    assertTrue(updatedPixels.isNotEmpty(), "the updated icon painted nothing")

    assertNotEquals(firstPixels, updatedPixels)
  }

  @Test
  fun `no descriptor and no icon shows no icon`() {
    val widget = widget()
    widget.updateFromPresentation(presentation())

    assertTrue(widget.leftIcons.isEmpty(), "the widget showed an icon without a source")
  }

  @Test
  fun `a removed descriptor clears the icon`() {
    val presentation = presentation(executeDescriptor())
    val widget = widget()
    widget.updateFromPresentation(presentation)

    presentation.iconDescriptor = null
    widget.updateFromPresentation(presentation)

    assertTrue(widget.leftIcons.isEmpty(), "the icon of the removed descriptor stayed")
  }

  @Test
  fun `a legacy icon alone shows the unknown marker`() {
    val presentation = presentation().apply { icon = AllIcons.General.Add }
    val widget = widget()
    widget.updateFromPresentation(presentation)

    val actual = assertIs<ScalableSwingIcon>(widget.leftIcons.single()).paintToImage().visiblePixels()

    assertEquals(unknownMarkerPixels(), actual, "the widget did not show the unknown marker")
  }

  @Test
  fun `a theme change rebuilds the disabled icon`() {
    val presentation = presentation(executeDescriptor()).apply { isEnabled = false }
    val widget = widget()
    widget.updateFromPresentation(presentation)

    val brightAlpha = assertIs<ScalableSwingIcon>(widget.leftIcons.single()).paintToImage().maxAlpha()

    val darkAlpha = withDarkTheme {
      widget.updateFromPresentation(presentation)
      assertIs<ScalableSwingIcon>(widget.leftIcons.single()).paintToImage().maxAlpha()
    }

    assertTrue(darkAlpha < brightAlpha, "the disabled icon was not rebuilt for the dark theme")
  }

  private fun widget(): ToolbarComboButton =
    runInEdtAndGet { ToolbarComboButton(DefaultToolbarComboButtonModel()) }

  private fun presentation(descriptor: IconDescriptor? = null): Presentation =
    Presentation().apply {
      text = "test"
      iconDescriptor = descriptor
    }

  private fun executeDescriptor(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun closeDescriptor(): IconDescriptor = imageIconDescriptor("actions/close.svg", AllIcons::class.java.classLoader)
}
