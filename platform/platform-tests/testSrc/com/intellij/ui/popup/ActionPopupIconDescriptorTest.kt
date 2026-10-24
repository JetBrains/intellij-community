// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory
import com.intellij.openapi.actionSystem.isGray
import com.intellij.openapi.actionSystem.paintToImage
import com.intellij.openapi.actionSystem.unknownMarkerPixels
import com.intellij.openapi.actionSystem.visiblePixels
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconDescriptor
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
import kotlin.test.assertIsNot
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class ActionPopupIconDescriptorTest {
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
  fun `descriptor action gives a bridged icon and no selected icon`() {
    val presentation = Presentation()
    presentation.iconDescriptor = executeDescriptor()

    val icons = calcRawIcons(presentation)

    assertIs<ScalableSwingIcon>(icons.first)
    assertNull(icons.second)
  }

  @Test
  fun `legacy-only action gives the unknown marker`() {
    val presentation = Presentation()
    presentation.icon = AllIcons.General.Add

    val icons = calcRawIcons(presentation)

    val icon = assertIs<ScalableSwingIcon>(icons.first)
    assertNull(icons.second)
    assertEquals(unknownMarkerPixels(), icon.paintToImage().visiblePixels(), "the popup row did not paint the unknown marker")
  }

  @Test
  fun `action without any icon gives no icons`() {
    val presentation = Presentation()
    val icons = calcRawIcons(presentation)
    assertNull(icons.first)
    assertNull(icons.second)
  }

  @Test
  fun `disabled descriptor action gives a gray icon`() {
    val presentation = Presentation().apply { isEnabled = false }
    presentation.iconDescriptor = executeDescriptor()

    val icons = calcRawIcons(presentation)
    val disabled = icons.first.paintToImage().visiblePixels()

    assertTrue(disabled.isNotEmpty())
    assertTrue(disabled.all { isGray(it) }, "the icon has colored pixels")
    assertNull(icons.second)
  }

  @Test
  fun `selected descriptor gives a separate selected icon`() {
    val presentation = Presentation()
    presentation.iconDescriptor = executeDescriptor()
    presentation.selectedIconDescriptor = closeDescriptor()

    val icons = calcRawIcons(presentation)

    val icon = assertIs<ScalableSwingIcon>(icons.first)
    val selectedIcon = assertIs<ScalableSwingIcon>(icons.second)

    assertNotEquals(
      icon.paintToImage().visiblePixels(),
      selectedIcon.paintToImage().visiblePixels(),
      "the selected icon paints the same image as the base icon",
    )
  }

  @Test
  fun `disabled action also disables the selected icon`() {
    val presentation = Presentation().apply { isEnabled = false }
    presentation.iconDescriptor = executeDescriptor()
    presentation.selectedIconDescriptor = closeDescriptor()

    val icons = calcRawIcons(presentation)

    val icon = assertIs<ScalableSwingIcon>(icons.first).paintToImage().visiblePixels()
    val selectedIcon = assertIs<ScalableSwingIcon>(icons.second).paintToImage().visiblePixels()

    assertTrue(icon.isNotEmpty(), "the icon painted nothing")
    assertTrue(selectedIcon.isNotEmpty(), "the selected icon painted nothing")
    assertTrue(icon.all { isGray(it) }, "the icon has colored pixels")
    assertTrue(selectedIcon.all { isGray(it) }, "the selected icon has colored pixels")

    assertNotEquals(
      icon,
      selectedIcon,
      "the selected icon paints the same image as the base icon",
    )
  }

  @Test
  fun `hidden icon ignores the descriptor`() {
    val presentation = Presentation()
    presentation.iconDescriptor = executeDescriptor()
    presentation.selectedIconDescriptor = closeDescriptor()

    presentation.putClientProperty(MenuItemPresentationFactory.HIDE_ICON, true)

    val icons = calcRawIcons(presentation)

    assertNull(icons.first)
    assertNull(icons.second)
  }

  @Test
  fun `selected toggle without icon keeps the checkmark`() {
    val presentation = Presentation()
    Toggleable.setSelected(presentation, true)

    val icons = calcRawIcons(presentation, TestToggleAction)

    assertNotNull(icons.first)
    assertIsNot<ScalableSwingIcon>(icons.first)
    assertNotNull(icons.second)
    assertIsNot<ScalableSwingIcon>(icons.second)
  }

  @Test
  fun `selected descriptor without a base icon gives only a selected icon`() {
    val presentation = Presentation()
    presentation.selectedIconDescriptor = closeDescriptor()

    val icons = calcRawIcons(presentation)
    assertNull(icons.first, "a row without a base icon got a base icon")

    val selectedIcon = assertIs<ScalableSwingIcon>(icons.second)
    assertEquals(
      closeDescriptor().createSwingIcon().paintToImage().visiblePixels(),
      selectedIcon.paintToImage().visiblePixels(),
      "the row did not paint the selected descriptor",
    )
  }

  @Test
  fun `disabled action with only a selected descriptor gives a gray selected icon`() {
    val presentation = Presentation().apply { isEnabled = false }
    presentation.selectedIconDescriptor = closeDescriptor()

    val icons = calcRawIcons(presentation)
    assertNull(icons.first, "a row without a base icon got a base icon")

    val selectedPixels = assertIs<ScalableSwingIcon>(icons.second).paintToImage().visiblePixels()
    assertTrue(selectedPixels.isNotEmpty(), "the selected icon painted nothing")
    assertTrue(selectedPixels.all { isGray(it) }, "the selected icon has colored pixels")
  }

  @Test
  fun `legacy selected icon does not leak`() {
    val presentation = Presentation()
    presentation.selectedIcon = AllIcons.General.Add

    val icons = calcRawIcons(presentation)

    assertNull(icons.first, "the legacy selected icon reached the base icon")
    assertNull(icons.second, "the legacy selected icon leaked into the row")
  }

  @Test
  fun `legacy disabled icon does not leak`() {
    val presentation = Presentation().apply { isEnabled = false }
    presentation.disabledIcon = AllIcons.General.Remove

    val icons = calcRawIcons(presentation)

    assertNull(icons.first, "the legacy disabled icon leaked into the row")
    assertNull(icons.second, "the legacy disabled icon leaked into the selected icon")
  }

  @Test
  fun `legacy selected icon does not hide the checkmark of a toggle`() {
    val presentation = Presentation()
    Toggleable.setSelected(presentation, true)
    presentation.selectedIcon = AllIcons.General.Add

    val icons = calcRawIcons(presentation, TestToggleAction)

    assertIsNot<ScalableSwingIcon>(assertNotNull(icons.first, "the toggle has no checkmark"))
    assertIsNot<ScalableSwingIcon>(assertNotNull(icons.second, "the toggle has no selected checkmark"))
    assertNotEquals(AllIcons.General.Add, icons.second, "the toggle shows the legacy selected icon")
  }

  @Test
  fun `icon that fits the slot is not rescaled`() {
    val presentation = Presentation().apply { text = "test" }
    presentation.iconDescriptor = executeDescriptor()

    val icon = popupItem(presentation, slot = 16).getIcon(false)

    assertIs<ScalableSwingIcon>(icon)
    assertEquals(16, icon.iconWidth)
    assertEquals(16, icon.iconHeight)
  }

  @Test
  fun `bigger slot scales the bridged icon`() {
    val presentation = Presentation().apply { text = "test" }
    presentation.iconDescriptor = executeDescriptor()

    val icon = popupItem(presentation, slot = 20).getIcon(false)

    assertIs<ScalableSwingIcon>(icon)
    assertEquals(20, icon.iconWidth)
    assertEquals(20, icon.iconHeight)
  }

  @Test
  fun `bigger slot scales the selected icon too`() {
    val presentation = Presentation().apply { text = "test" }
    presentation.iconDescriptor = executeDescriptor()
    presentation.selectedIconDescriptor = closeDescriptor()

    val icon = popupItem(presentation, slot = 20).getIcon(true)

    assertIs<ScalableSwingIcon>(icon)
    assertEquals(20, icon.iconWidth)
    assertEquals(20, icon.iconHeight)
  }

  @Test
  fun `action without icon gets an empty icon of the slot size`() {
    val presentation = Presentation().apply { text = "test" }
    val icon = assertNotNull(popupItem(presentation, slot = 20).getIcon(false))

    assertIsNot<ScalableSwingIcon>(icon)
    assertEquals(20, icon.iconWidth)
    assertEquals(20, icon.iconHeight)
  }

  private fun popupItem(
    presentation: Presentation,
    slot: Int,
  ): PopupFactoryImpl.ActionItem =
    runInEdtAndGet {
      val item = PopupFactoryImpl.ActionItem(TestAction, null, false, false, slot, slot, false, null)
      item.updateFromPresentation(presentation, "TestPlace")
      item
    }

  private fun executeDescriptor(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun closeDescriptor(): IconDescriptor = imageIconDescriptor("actions/close.svg", AllIcons::class.java.classLoader)

  private fun calcRawIcons(
    presentation: Presentation,
    action: AnAction = TestAction,
  ) = ActionStepBuilder.calcRawIcons(action, presentation, false)

  private object TestAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = Unit
  }

  private object TestToggleAction : AnAction(), Toggleable {
    override fun actionPerformed(e: AnActionEvent) = Unit
  }
}
