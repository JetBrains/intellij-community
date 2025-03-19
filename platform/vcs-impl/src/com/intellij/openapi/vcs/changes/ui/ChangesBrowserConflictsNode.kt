// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.merge.MergeConflictManager
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import org.jetbrains.annotations.ApiStatus


@JvmField
internal val CONFLICTS_NODE_TAG: ChangesBrowserNode.Tag = object : ChangesBrowserNode.Tag {}

@ApiStatus.Internal
class ChangesBrowserConflictsNode(val project: Project)
  : TagChangesBrowserNode(CONFLICTS_NODE_TAG, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true) {
  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    renderer.append(VcsBundle.message("changes.nodetitle.merge.conflicts"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    renderer.append(FontUtil.spaceAndThinSpace(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    if (!MergeConflictManager.isNonModalMergeEnabled(project)) {
      renderer.append(VcsBundle.message("changes.nodetitle.merge.conflicts.resolve.link.label"), SimpleTextAttributes.LINK_BOLD_ATTRIBUTES,
                      Runnable { showResolveConflictsDialog() })
    }
  }

  private fun showResolveConflictsDialog() {
    AbstractVcsHelper.getInstance(project).showMergeDialog(ChangesUtil.iterateFiles(allChangesUnder).toList())
  }

  override fun getTextPresentation(): String {
    return VcsBundle.message("changes.nodetitle.merge.conflicts")
  }

  override fun getSortWeight(): Int = CONFLICTS_SORT_WEIGHT
}
