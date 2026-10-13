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
  fun createTabIconHolder(composite: EditorComposite, owner: TabInfo, startupIcon: Icon? = null): TabInfoIconHolder
  companion object {
    fun getInstance(): CompositeTabIconHolderCreator = service()
  }
}