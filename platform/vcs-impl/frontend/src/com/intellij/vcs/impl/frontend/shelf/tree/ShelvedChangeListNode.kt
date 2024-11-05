// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf.tree

import com.intellij.openapi.vcs.VcsBundle
import com.intellij.platform.kernel.KernelService
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.jetbrains.rhizomedb.asOf
import fleet.kernel.rete.Rete
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class ShelvedChangeListNode(private val changeList: ShelvedChangeListEntity) : EntityChangesBrowserNode<ShelvedChangeListEntity>(changeList) {

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

    val loadingError = changeList.error ?: return

    renderer.append(FontUtil.spaceAndThinSpace() + loadingError, SimpleTextAttributes.ERROR_ATTRIBUTES)
  }

  override fun doGetTextPresentation(): @Nls String? {
    return getUserObject().description
  }
}