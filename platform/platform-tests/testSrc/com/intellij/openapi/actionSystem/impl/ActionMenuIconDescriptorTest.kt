// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
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
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class ActionMenuIconDescriptorTest {
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
  fun `group with a descriptor shows a bridged icon`() {
    val menu = menu(group(executeDescriptor()))
    val icon = assertIs<ScalableSwingIcon>(menu.icon)

    assertTrue(icon.paintToImage().hasVisiblePixels(), "the menu icon painted nothing")
  }

  @Test
  fun `group with a legacy icon only shows the unknown marker`() {
    val group = group().apply { templatePresentation.icon = AllIcons.General.Add }
    val menu = menu(group)
    val icon = assertIs<ScalableSwingIcon>(menu.icon)

    assertEquals(unknownMarkerPixels(), icon.paintToImage().visiblePixels(), "the menu icon did not paint the unknown marker")
  }

  @Test
  fun `group without an icon shows no icon`() {
    val menu = menu(group())

    assertNull(menu.icon)
  }

  @Test
  fun `disabled group shows a gray disabled icon`() {
    val group = group(executeDescriptor())
    val factory = MenuItemPresentationFactory().apply { getPresentation(group).isEnabled = false }
    val menu = menu(group, factory)
    val baseIcon = assertIs<ScalableSwingIcon>(menu.icon).paintToImage().visiblePixels()
    val disabledIcon = assertIs<ScalableSwingIcon>(menu.disabledIcon).paintToImage().visiblePixels()

    assertTrue(baseIcon.isNotEmpty(), "the base icon painted nothing")
    assertTrue(disabledIcon.isNotEmpty(), "the disabled icon painted nothing")
    assertNotEquals(baseIcon, disabledIcon, "the base icon should be disabled")
    assertTrue(disabledIcon.all { isGray(it) }, "the disabled icon painted non-gray pixels")
  }

  @Test
  fun `toggled group keeps the checkmark`() {
    val group = TestToggleGroup().apply {
      templatePresentation.text = "test"
      templatePresentation.iconDescriptor = executeDescriptor()
    }

    Toggleable.setSelected(group.templatePresentation, true)

    val menu = menu(group)

    assertNotNull(menu.icon)
    assertIsNot<ScalableSwingIcon>(menu.icon)
  }

  @Test
  fun `hidden icons give no icon`() {
    val factory = MenuItemPresentationFactory(true)
    val menu = menu(group(executeDescriptor()), factory)
    assertNull(menu.icon)
  }

  private fun executeDescriptor(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun menu(group: ActionGroup, factory: PresentationFactory = MenuItemPresentationFactory()): ActionMenu =
    runInEdtAndGet { ActionMenu(null, "TestPlace", group, factory, false, false) }

  private fun group(descriptor: IconDescriptor? = null): DefaultActionGroup =
    DefaultActionGroup().apply {
      templatePresentation.text = "test"
      templatePresentation.iconDescriptor = descriptor
    }

  private class TestToggleGroup : DefaultActionGroup(), Toggleable
}
