// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.Nls

internal class ShelvedListNode(val changeList: ShelvedChangeList) : ChangesBrowserNode<ShelvedChangeList>(changeList) {

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    var listName = changeList.description
    if (listName.isBlank()) listName = VcsBundle.message("changes.nodetitle.empty.changelist.name")

    if (changeList.isRecycled || changeList.isDeleted) {
      renderer.appendTextWithIssueLinks(listName, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
    }
    else {
      renderer.appendTextWithIssueLinks(listName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    appendCount(renderer)
    val date = DateFormatUtil.formatPrettyDateTime(changeList.date)
    renderer.append(", $date", SimpleTextAttributes.GRAYED_ATTRIBUTES)

    val loadingError = changeList.changesLoadingError
    if (loadingError != null) {
      renderer.append(FontUtil.spaceAndThinSpace() + loadingError, SimpleTextAttributes.ERROR_ATTRIBUTES)
    }
  }

  override fun getTextPresentation(): String? {
    return getUserObject().toString()
  }
}
