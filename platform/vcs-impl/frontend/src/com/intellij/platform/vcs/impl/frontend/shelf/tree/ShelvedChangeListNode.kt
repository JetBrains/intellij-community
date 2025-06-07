// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.tree

import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.platform.vcs.impl.frontend.VcsFrontendBundle
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class ShelvedChangeListNode(private val changeList: ShelvedChangeListEntity) : EntityChangesBrowserNode<ShelvedChangeListEntity>(changeList) {

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    var listName = changeList.description
    if (listName.isBlank()) listName = VcsFrontendBundle.message("changes.nodetitle.empty.changelist.name")
    if (changeList.isRecycled || changeList.isDeleted) {
      renderer.appendTextWithIssueLinks(listName, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
    }
    else {
      renderer.appendTextWithIssueLinks(listName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    appendCount(renderer)
    val date = DateFormatUtil.formatPrettyDateTime(changeList.date)
    renderer.append(", $date", SimpleTextAttributes.GRAYED_ATTRIBUTES)

    val loadingError = changeList.error ?: return

    renderer.append(FontUtil.spaceAndThinSpace() + loadingError, SimpleTextAttributes.ERROR_ATTRIBUTES)
  }

  override fun doGetTextPresentation(): @Nls String? {
    return getUserObject().description
  }
}