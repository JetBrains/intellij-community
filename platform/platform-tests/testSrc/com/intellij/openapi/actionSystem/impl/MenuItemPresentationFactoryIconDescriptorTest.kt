// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class MenuItemPresentationFactoryIconDescriptorTest {
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
  fun `forced icon hiding clears the base and hovered descriptors`() {
    val presentation = MenuItemPresentationFactory(true).getPresentation(actionWithAllDescriptors())

    assertNull(presentation.iconDescriptor, "the base descriptor was kept")
    assertNull(presentation.hoveredIconDescriptor, "the hovered descriptor was kept")
  }

  @Test
  fun `forced hiding marks the presentation with HIDE_ICON`() {
    val presentation = MenuItemPresentationFactory(true).getPresentation(actionWithAllDescriptors())
    assertEquals(true, presentation.getClientProperty(MenuItemPresentationFactory.HIDE_ICON))
  }

  @Test
  fun `forced hiding keeps the selected descriptor`() {
    val presentation = MenuItemPresentationFactory(true).getPresentation(actionWithAllDescriptors())
    assertNotNull(presentation.selectedIconDescriptor, "the selected descriptor was cleared")
  }

  @Test
  fun `visible icons keep all descriptors`() {
    val presentation = MenuItemPresentationFactory(false).getPresentation(actionWithAllDescriptors())

    assertNull(presentation.getClientProperty(MenuItemPresentationFactory.HIDE_ICON))
    assertNotNull(presentation.iconDescriptor)
    assertNotNull(presentation.hoveredIconDescriptor)
  }

  private fun executeDescriptor(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun closeDescriptor(): IconDescriptor = imageIconDescriptor("actions/close.svg", AllIcons::class.java.classLoader)

  private fun addDescriptor(): IconDescriptor = imageIconDescriptor("general/add.svg", AllIcons::class.java.classLoader)

  private fun actionWithAllDescriptors(): AnAction {
    val action = action(executeDescriptor())
    action.templatePresentation.hoveredIconDescriptor = closeDescriptor()
    action.templatePresentation.selectedIconDescriptor = addDescriptor()
    return action
  }

  private fun action(descriptor: IconDescriptor): AnAction =
    object : AnAction({ "test" }, null, descriptor, null) {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }
}
