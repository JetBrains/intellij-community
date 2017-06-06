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
import com.intellij.ui.content.TabbedContent

class SelectContentTabStep(val content: TabbedContent) : BaseListPopupStep<Int>(null) {

  private val myTabs = content.tabs

  init {
    val indexes = (0 until myTabs.size).toList()
    init(null, indexes, null)
    defaultOptionIndex = content.selectedIndex
  }

  override fun isSpeedSearchEnabled(): Boolean = true

  override fun getTextFor(value: Int): String = myTabs[value].first

  override fun onChosen(selectedValue: Int, finalChoice: Boolean): PopupStep<*>? {
    val manager = content.manager ?: return FINAL_CHOICE
    content.selectContent(selectedValue)
    manager.setSelectedContent(content)
    return FINAL_CHOICE
  }
}
