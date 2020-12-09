// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content

import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.content.Content
import com.intellij.ui.content.TabbedContent
import java.awt.Color
import javax.swing.Icon

open class SelectContentStep : BaseListPopupStep<Content> {
  constructor(contents: Array<Content>) : super(null, *contents)
  constructor(contents: List<Content>) : super(null, contents)

  override fun isSpeedSearchEnabled(): Boolean = true

  override fun getIconFor(value: Content): Icon? = value.icon

  override fun getTextFor(value: Content): String {
    return value.asMultiTabbed()?.titlePrefix ?: value.displayName ?: super.getTextFor(value)
  }

  override fun getBackgroundFor(value: Content): Color? = value.tabColor

  override fun hasSubstep(value: Content): Boolean = value.asMultiTabbed() != null

  override fun onChosen(value: Content, finalChoice: Boolean): PopupStep<*>? {
    val tabbed = value.asMultiTabbed()
    if (tabbed == null) {
      value.manager?.setSelectedContentCB(value, true, true)
      return PopupStep.FINAL_CHOICE
    }
    else {
      return SelectContentTabStep(tabbed)
    }
  }

  private fun Content.asMultiTabbed(): TabbedContent? = if (this is TabbedContent && hasMultipleTabs()) this else null
}
