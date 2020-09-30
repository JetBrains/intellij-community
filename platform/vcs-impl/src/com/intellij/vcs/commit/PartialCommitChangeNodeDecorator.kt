// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES
import com.intellij.util.FontUtil.spaceAndThinSpace

class PartialCommitChangeNodeDecorator @JvmOverloads constructor(
  private val project: Project,
  private val baseDecorator: ChangeNodeDecorator,
  private val isVisible: () -> Boolean = { true }
) : ChangeNodeDecorator {

  override fun decorate(change: Change, renderer: SimpleColoredComponent, isShowFlatten: Boolean) {
    if (project.isDisposed) return
    if (isVisible()) appendPartialCommitState(change, renderer)
    baseDecorator.decorate(change, renderer, isShowFlatten)
  }

  private fun appendPartialCommitState(change: Change, renderer: SimpleColoredComponent) {
    val changeListId = (change as? ChangeListChange)?.changeListId ?: return
    val ranges = PartialChangesUtil.getPartialTracker(project, change)?.getRanges() ?: return
    val rangesToCommit = ranges.count { it.changelistId == changeListId && !it.isExcludedFromCommit }

    if (rangesToCommit != 0 && rangesToCommit != ranges.size) {
      renderer.append(spaceAndThinSpace()).append(VcsBundle.message("ranges.to.commit.of.ranges.size.changes", rangesToCommit, ranges.size), GRAY_ITALIC_ATTRIBUTES)
    }
  }

  override fun preDecorate(change: Change, renderer: ChangesBrowserNodeRenderer, isShowFlatten: Boolean) =
    baseDecorator.preDecorate(change, renderer, isShowFlatten)
}
