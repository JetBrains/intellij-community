// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.shelf

import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode

internal class ShelvedListNode(val changeList: ShelvedChangeList) : ChangesBrowserNode<ShelvedChangeList>(changeList) {

  override fun getTextPresentation(): String {
    return getUserObject().toString()
  }
}
