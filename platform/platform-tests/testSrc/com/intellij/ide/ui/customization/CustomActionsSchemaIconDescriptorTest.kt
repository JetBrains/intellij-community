// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ExperimentalIcons
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import javax.swing.Icon
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Coverage for keeping the experimental icons pipeline in sync with user icon customizations, see
 * `CustomActionsSchema.initActionIconDescriptor`. A customization is an arbitrary icon, so the resolved Swing icon is
 * bridged into an [IconDescriptor]; resetting the customization restores the originally declared descriptor.
 */
@TestApplication
@RegistryKey(ExperimentalIcons.REGISTRY_KEY, "true")
class CustomActionsSchemaIconDescriptorTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      IntelliJIconManager.activate()
    }
  }

  private fun action(icon: Icon? = null, descriptor: IconDescriptor? = null): AnAction {
    val anAction = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }
    if (icon != null) anAction.templatePresentation.icon = icon
    if (descriptor != null) anAction.templatePresentation.iconDescriptor = descriptor
    return anAction
  }

  @Test
  fun `a customization is bridged into a descriptor and the original is remembered`() {
    val originalDescriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)
    // the icon customization reuses another action's icon (the "reuse from" form of a customization value)
    val source = action(icon = AllIcons.Actions.Copy)

    val schema = CustomActionsSchema(null)
    schema.addIconCustomization("target", "source")
    schema.initActionIcon(anAction = target, actionId = "target", actionSupplier = { if (it == "source") source else null })

    val descriptor = target.templatePresentation.iconDescriptor
    assertNotNull(descriptor, "the customized icon must be bridged into a descriptor")
    assertNotSame(originalDescriptor, descriptor, "the descriptor must reflect the customization, not the original")
    assertSame(originalDescriptor, target.templatePresentation.getClientProperty(CustomActionsSchema.PROP_ORIGINAL_ICON_DESCRIPTOR),
               "the originally declared descriptor must be preserved for a later reset")
  }

  @Test
  fun `without a customization the originally declared descriptor is kept`() {
    val originalDescriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)

    val schema = CustomActionsSchema(null)
    schema.initActionIcon(anAction = target, actionId = "target", actionSupplier = { null })

    assertSame(originalDescriptor, target.templatePresentation.iconDescriptor)
  }
}
