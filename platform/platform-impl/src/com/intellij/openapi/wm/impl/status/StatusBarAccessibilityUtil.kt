// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.accessibility.AccessibleAction
import javax.swing.JComponent
import javax.swing.UIManager

@ApiStatus.Internal
object StatusBarAccessibilityUtil {
  @JvmStatic
  fun createAccessibleAction(component: JComponent): AccessibleAction =
    PrimaryAccessibleAction(component, component, null)

  @JvmStatic
  fun createAccessibleAction(component: JComponent, action: Runnable): AccessibleAction =
    PrimaryAccessibleAction(component, component, action)

  @JvmStatic
  @Suppress("HardCodedStringLiteral")
  fun getAccessibleDescription(component: JComponent): @Nls String? {
    val toolTipText = component.toolTipText ?: return null
    return StringUtil.removeHtmlTags(toolTipText).trim().ifBlank { null }
  }

  @JvmStatic
  fun getTextOrTooltipAccessibleName(component: JComponent, text: @Nls String?): @Nls String? =
    text?.takeIf(String::isNotEmpty) ?: getAccessibleDescription(component)

  @JvmStatic
  fun getTooltipAccessibleDescription(component: JComponent, text: @Nls String?): @Nls String? =
    if (text.isNullOrEmpty()) null else getAccessibleDescription(component)

  private class PrimaryAccessibleAction(
    private val component: JComponent,
    private val activationTarget: JComponent,
    private val action: Runnable?,
  ) : AccessibleAction {
    override fun getAccessibleActionCount(): Int = 1

    override fun getAccessibleActionDescription(i: Int): String? = if (i == 0) UIManager.getString("AbstractButton.clickText") else null

    override fun doAccessibleAction(i: Int): Boolean = i == 0 && StatusBarUtil.performPrimaryAction(component, activationTarget, action)
  }
}
