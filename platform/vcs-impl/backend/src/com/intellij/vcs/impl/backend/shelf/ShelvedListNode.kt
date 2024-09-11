// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.Nls

internal class ShelvedListNode(list: ShelvedChangeList) : ChangesBrowserNode<ShelvedChangeList?>(list) {
  private val myList: ShelvedChangeList

  init {
    myList = list
  }

  fun getList(): ShelvedChangeList {
    return myList
  }

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    var listName = myList.getDescription()
    if (StringUtil.isEmptyOrSpaces(listName)) listName = VcsBundle.message("changes.nodetitle.empty.changelist.name")

    if (myList.isRecycled() || myList.isDeleted()) {
      renderer.appendTextWithIssueLinks(listName, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
    }
    else {
      renderer.appendTextWithIssueLinks(listName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    appendCount(renderer)
    val date = DateFormatUtil.formatPrettyDateTime(myList.getDate())
    renderer.append(", " + date, SimpleTextAttributes.GRAYED_ATTRIBUTES)

    val loadingError = myList.getChangesLoadingError()
    if (loadingError != null) {
      renderer.append(FontUtil.spaceAndThinSpace() + loadingError, SimpleTextAttributes.ERROR_ATTRIBUTES)
    }
  }

  override fun getTextPresentation(): @Nls String? {
    return getUserObject().toString()
  }
}
