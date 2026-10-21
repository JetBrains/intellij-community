// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabInfoIconHolder
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface CompositeTabIconHolderCreator {
  /**
   * @param startupIcon the icon to show until the computed icon is available.
   * @param waitForEditors when it is `false`, the holder shows each computed icon at once and shows no loading spinner.
   * Use it for a tab whose composite does not start the initialization now.
   */
  fun createTabIconHolder(
    composite: EditorComposite,
    owner: TabInfo,
    startupIcon: Icon? = null,
    waitForEditors: Boolean = true,
  ): TabInfoIconHolder
  companion object {
    fun getInstance(): CompositeTabIconHolderCreator = service()
  }
}