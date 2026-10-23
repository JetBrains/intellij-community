// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ex.ActionIconDescriptors
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.JBColor
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import javax.swing.Icon
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class SplitButtonActionIconDescriptorTest {
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
  fun `disabled icon matches the disabled descriptor`() {
    val descriptor = executeDescriptor()
    val disabled = splitButton(descriptor).chooseActionIcon().paintToImage().visiblePixels()
    val expected = ActionIconDescriptors.disabled(descriptor).createSwingIcon().paintToImage().visiblePixels()

    assertTrue(disabled.isNotEmpty(), "the disabled icon painted nothing")
    assertTrue(expected.isNotEmpty(), "the expected icon painted nothing")
    assertEquals(expected, disabled, "the icons painted different pixels")
  }

  @Test
  fun `disabled icon is translucent and fainter than the base icon`() {
    val descriptor = executeDescriptor()
    val enabledAlpha = descriptor.createSwingIcon().paintToImage().maxAlpha()
    val disabledAlpha = splitButton(descriptor).chooseActionIcon().paintToImage().maxAlpha()

    assertTrue(disabledAlpha < 255, "the disabled icon is opaque")
    assertTrue(disabledAlpha < enabledAlpha, "the disabled icon is not fainter than the base icon")
  }

  @Test
  fun `disabled icon is reused between reads`() {
    val button = splitButton(executeDescriptor())
    val first = button.chooseActionIcon()
    val second = button.chooseActionIcon()

    assertSame(first, second, "the icons are different instances")
  }

  @Test
  fun `changed descriptor gives another disabled icon`() {
    val button = splitButton(executeDescriptor())
    val before = button.chooseActionIcon().paintToImage().visiblePixels()

    button.presentation.iconDescriptor = closeDescriptor()
    button.updateIcon()
    val after = button.chooseActionIcon().paintToImage().visiblePixels()

    assertTrue(after.isNotEmpty(), "the disabled icon painted nothing")
    assertNotEquals(before, after, "the disabled icon did not change with the descriptor")
    assertEquals(closeDescriptor().disabledPixels(), after, "the disabled icon is not the one of the new descriptor")
  }

  @Test
  fun `legacy-only action gives the disabled unknown marker`() {
    val button = splitButton(null, legacyIcon = AllIcons.General.Add)
    val icon = button.chooseActionIcon().paintToImage().visiblePixels()

    assertTrue(icon.isNotEmpty(), "the disabled icon painted nothing")
    assertEquals(unknownDescriptor().disabledPixels(), icon, "the disabled icon is not the unknown marker")
  }

  @Test
  fun `action without any icon gives the disabled unknown marker`() {
    val icon = splitButton(null).chooseActionIcon().paintToImage().visiblePixels()

    assertEquals(unknownDescriptor().disabledPixels(), icon, "the disabled icon is not the unknown marker")
  }

  @Test
  fun `enabled button shows the base icon`() {
    val descriptor = executeDescriptor()
    val button = splitButton(descriptor)
    button.javaClass.getDeclaredField("selectedAction").apply { isAccessible = true }.set(button, DefaultActionGroup())

    val icon = button.chooseActionIcon().paintToImage().visiblePixels()

    assertEquals(descriptor.createSwingIcon().paintToImage().visiblePixels(), icon, "the enabled button does not show the base icon")
  }

  @Test
  fun `disabled icon is rebuilt after a theme change`() {
    assertTrue(JBColor.isBright(), "the test must start on a bright theme")

    val button = splitButton(executeDescriptor())
    val brightIcon = button.chooseActionIcon()
    val brightAlpha = brightIcon.paintToImage().maxAlpha()
    val (darkIcon, darkAlpha) =
      withDarkTheme {
        val icon = button.chooseActionIcon()
        icon to icon.paintToImage().maxAlpha()
      }

    assertNotSame(brightIcon, darkIcon, "the disabled icon was not rebuilt after the theme change")
    assertTrue(darkAlpha < brightAlpha, "dark alpha $darkAlpha is not below bright alpha $brightAlpha")
  }

  private fun splitButton(descriptor: IconDescriptor?, legacyIcon: Icon? = null): ActionButton {
    val presentation = Presentation().apply {
      iconDescriptor = descriptor
      icon = legacyIcon
    }
    val button = SplitButtonAction(DefaultActionGroup()).createCustomComponent(presentation, "TestPlace") as ActionButton
    button.updateIcon()
    return button
  }

  private fun ActionButton.chooseActionIcon(): Icon {
    val method = javaClass.getDeclaredMethod("chooseActionIcon").apply { isAccessible = true }
    return method.invoke(this) as Icon
  }

  private fun executeDescriptor(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun closeDescriptor(): IconDescriptor = imageIconDescriptor("actions/close.svg", AllIcons::class.java.classLoader)

  private fun unknownDescriptor(): IconDescriptor = imageIconDescriptor("expui/toolbar/unknown.svg", AllIcons::class.java.classLoader)

  private fun IconDescriptor.disabledPixels(): List<Int> = ActionIconDescriptors.disabled(this).createSwingIcon().paintToImage().visiblePixels()
}
