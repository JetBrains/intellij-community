// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.backend.shelf

import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.Nls

internal class ShelvedListNode(val changeList: ShelvedChangeList) : ChangesBrowserNode<ShelvedChangeList>(changeList) {

  override fun getTextPresentation(): String {
    return getUserObject().toString()
  }
}
