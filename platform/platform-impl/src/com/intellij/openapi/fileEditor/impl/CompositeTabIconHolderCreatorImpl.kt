// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.CompositeTabIconHolderCreator
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabInfoIconHolder

private class CompositeTabIconHolderCreatorImpl : CompositeTabIconHolderCreator {
  override fun createTabIconHolder(composite: EditorComposite, owner: TabInfo): TabInfoIconHolder {
    return TabInfoIconHolder.default(owner)
  }
}