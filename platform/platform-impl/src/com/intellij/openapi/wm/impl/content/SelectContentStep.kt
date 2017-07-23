/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl.content

import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.content.Content
import com.intellij.ui.content.TabbedContent
import javax.swing.Icon

class SelectContentStep : BaseListPopupStep<Content> {

  constructor(contents: Array<Content>) : super(null, *contents)
  constructor(contents: List<Content>) : super(null, contents)

  override fun isSpeedSearchEnabled(): Boolean = true

  override fun getIconFor(value: Content): Icon? = value.icon

  override fun getTextFor(value: Content): String {
    return value.asMultiTabbed()?.titlePrefix ?: value.displayName ?: super.getTextFor(value)
  }

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
