// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ex.ActionIconDescriptors
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.IconManager
import com.intellij.ui.components.SplitButtonHalf
import com.intellij.ui.components.SplitButtonZones
import com.intellij.ui.icons.CoreIconManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class SplitButtonActionIconDescriptorTest {
  companion object {
    private var iconsActivated = false

    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      // only IDE bootstrap activates it, and only when not headless
      IntelliJIconManager.activate()
      IconLoader.activate()
      val iconManagerClass = IconManager.getInstance().javaClass
      IconManager.activate(CoreIconManager())
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

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `disabled action paints the disabled descriptor`() {
    val pixels = splitButton(Presentation().apply { iconDescriptor = execute() }).paintedPixels()

    assertTrue(pixels.isNotEmpty(), "the split button painted nothing")
    assertEquals(execute().disabledPixels(), pixels, "the split button did not paint the disabled descriptor")
  }

  @Test
  fun `toggled and disabled action paints the disabled selected descriptor`() {
    val toggled = Presentation().apply {
      iconDescriptor = execute()
      selectedIconDescriptor = close()
      Toggleable.setSelected(this, true)
    }
    val expected = Presentation().apply {
      iconDescriptor = close()
      Toggleable.setSelected(this, true)
    }

    assertEquals(splitButton(expected).paintedPixels(), splitButton(toggled).paintedPixels(), "the split button did not paint the selected descriptor")
  }

  @Test
  fun `disabled action without an icon paints the disabled icon of the template presentation`() {
    val action = SplitButtonAction(DefaultActionGroup()).apply { templatePresentation.iconDescriptor = execute() }

    assertEquals(execute().disabledPixels(), splitButton(Presentation(), action).paintedPixels(), "the split button did not paint the template icon")
  }

  @Test
  fun `disabled action without any icon paints the disabled unknown marker`() {
    val pixels = splitButton(Presentation().apply { icon = AllIcons.General.Add }).paintedPixels()

    assertEquals(unknown().disabledPixels(), pixels, "the split button did not paint the unknown marker")
  }

  @Test
  fun `disabled action paints the legacy disabled icon when the flag is off`() {
    Registry.get(ExperimentalIconRendering.REGISTRY_KEY).setValue(false, disposable)
    val pixels = splitButton(Presentation().apply { icon = AllIcons.Actions.Execute }).paintedPixels()

    assertTrue(pixels.isNotEmpty(), "the split button painted nothing")
    assertEquals(IconLoader.getDisabledIcon(AllIcons.Actions.Execute).paintToImage().visiblePixels(), pixels, "the split button did not paint the legacy disabled icon")
  }

  private fun splitButton(presentation: Presentation, action: SplitButtonAction = SplitButtonAction(DefaultActionGroup())): ActionButton {
    val button = action.createCustomComponent(presentation, "TestPlace") as ActionButton
    button.updateIcon()
    button.size = button.preferredSize
    return button
  }

  private fun ActionButton.paintedImage(): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      paint(g)
    }
    finally {
      g.dispose()
    }
    return image
  }

  private fun ActionButton.paintedPixels(): List<Int> {
    val zone = (this as SplitButtonZones).splitButtonZone(SplitButtonHalf.ACTION)!!
    return paintedImage().getSubimage(zone.x, zone.y, zone.width, zone.height).visiblePixels()
  }

  private fun execute(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun close(): IconDescriptor = imageIconDescriptor("actions/close.svg", AllIcons::class.java.classLoader)

  private fun unknown(): IconDescriptor = imageIconDescriptor("expui/toolbar/unknown.svg", AllIcons::class.java.classLoader)

  private fun IconDescriptor.disabledPixels(): List<Int> = ActionIconDescriptors.disabled(this).createSwingIcon().paintToImage().visiblePixels()
}
