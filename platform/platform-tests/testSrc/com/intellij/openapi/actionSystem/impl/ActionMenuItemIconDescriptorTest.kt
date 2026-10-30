// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.hasVisiblePixels
import com.intellij.openapi.actionSystem.isGray
import com.intellij.openapi.actionSystem.paintToImage
import com.intellij.openapi.actionSystem.unknownMarkerPixels
import com.intellij.openapi.actionSystem.visiblePixels
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class ActionMenuItemIconDescriptorTest {
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
  fun `descriptor action shows a bridged icon`() {
    val item = menuItem(presentation(executeDescriptor()))
    val icon = assertIs<ScalableSwingIcon>(item.icon)

    assertTrue(icon.paintToImage().hasVisiblePixels(), "the menu icon painted nothing")
  }

  @Test
  fun `legacy-only action shows the unknown marker`() {
    val presentation = presentation().apply { icon = AllIcons.General.Add }
    val item = menuItem(presentation)
    val icon = assertIs<ScalableSwingIcon>(item.icon)

    assertEquals(unknownMarkerPixels(), icon.paintToImage().visiblePixels(), "the menu icon did not paint the unknown marker")
  }

  @Test
  fun `action without an icon shows no icon`() {
    val item = menuItem(presentation())

    assertNull(item.icon)
  }

  @Test
  fun `disabled action shows a gray icon and a gray disabled icon`() {
    val presentation = presentation(executeDescriptor()).apply { isEnabled = false }
    val item = menuItem(presentation)

    assertFalse(item.isEnabled)

    // a disabled item shows the disabled icon as its main icon
    val iconPixels = assertIs<ScalableSwingIcon>(item.icon).paintToImage().visiblePixels()
    val disabledPixels = assertIs<ScalableSwingIcon>(item.disabledIcon).paintToImage().visiblePixels()

    assertTrue(iconPixels.isNotEmpty(), "the icon painted nothing")
    assertTrue(disabledPixels.isNotEmpty(), "the disabled icon painted nothing")
    assertTrue(iconPixels.all { isGray(it) }, "the icon has colored pixels")
    assertTrue(disabledPixels.all { isGray(it) }, "the disabled icon has colored pixels")
  }

  @Test
  fun `selected descriptor gives a separate selected icon`() {
    val presentation = presentation(executeDescriptor())
    presentation.selectedIconDescriptor = closeDescriptor()

    val item = menuItem(presentation)
    val basePixels = assertIs<ScalableSwingIcon>(item.icon).paintToImage().visiblePixels()
    val selectedPixels = assertIs<ScalableSwingIcon>(item.selectedIcon).paintToImage().visiblePixels()

    assertTrue(basePixels.isNotEmpty(), "the icon painted nothing")
    assertTrue(selectedPixels.isNotEmpty(), "the selected icon painted nothing")
    assertNotEquals(basePixels, selectedPixels, "the selected icon paints the same image as the base icon")
  }

  @Test
  fun `selected icon is the base icon without a selected descriptor`() {
    val item = menuItem(presentation(executeDescriptor()))
    val icon = assertIs<ScalableSwingIcon>(item.icon)

    assertSame(icon, item.selectedIcon)
  }

  @Test
  fun `toggled action with an icon shows a popped icon`() {
    val presentation = presentation(executeDescriptor())
    Toggleable.setSelected(presentation, true)
    val item = menuItem(presentation, TestToggleAction)

    assertIs<PoppedIcon>(item.icon)
  }

  @Test
  fun `toggled action without an icon keeps the checkmark`() {
    val presentation = presentation()
    Toggleable.setSelected(presentation, true)
    val item = menuItem(presentation, TestToggleAction)

    assertNotNull(item.icon)
    assertIsNot<ScalableSwingIcon>(item.icon)
  }

  @Test
  fun `toggled action in a checked group shows the descriptor icon, not a popped icon`() {
    val presentation = presentation(executeDescriptor())
    Toggleable.setSelected(presentation, true)
    val item = menuItem(presentation, TestToggleAction, insideCheckedGroup = true)

    assertIs<ScalableSwingIcon>(item.icon)
    assertIsNot<PoppedIcon>(item.icon)
  }

  private fun presentation(descriptor: IconDescriptor? = null): Presentation =
    Presentation().apply {
      text = "test"
      iconDescriptor = descriptor
    }

  private fun menuItem(
    presentation: Presentation,
    action: AnAction = TestAction,
    insideCheckedGroup: Boolean = false,
  ): ActionMenuItem =
    runInEdtAndGet {
      val item = createActionMenuItemForTest(action, insideCheckedGroup = insideCheckedGroup)
      item.updateFromPresentation(presentation)
      item
    }

  private fun executeDescriptor(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun closeDescriptor(): IconDescriptor = imageIconDescriptor("actions/close.svg", AllIcons::class.java.classLoader)

  private object TestAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = Unit
  }

  private object TestToggleAction : AnAction(), Toggleable {
    override fun actionPerformed(e: AnActionEvent) = Unit
  }
}
