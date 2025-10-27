// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.commit

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.ex.changesInChangeList
import com.intellij.openapi.vcs.ex.countAllChanges
import com.intellij.openapi.vcs.ex.countIncludedChanges
import com.intellij.platform.vcs.impl.shared.changes.PartialChangesHolder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PartialCommitChangeNodeDecorator @JvmOverloads constructor(
  private val project: Project,
  private val baseDecorator: ChangeNodeDecorator?,
  private val isVisible: () -> Boolean = { true }
) : ChangeNodeDecorator {

  override fun decorate(change: Change, renderer: SimpleColoredComponent, isShowFlatten: Boolean) {
    if (project.isDisposed) return
    if (isVisible()) runReadAction { appendPartialCommitState(change, renderer) }
    baseDecorator?.decorate(change, renderer, isShowFlatten)
  }

  private fun appendPartialCommitState(change: Change, renderer: SimpleColoredComponent) {
    val changeListId = (change as? ChangeListChange)?.changeListId ?: return
    val filePath = change.afterRevision?.file ?: return
    val ranges = PartialChangesHolder.getInstance(project).getRanges(filePath) ?: return
    val rangesToCommit = ranges.changesInChangeList(changeListId).countIncludedChanges()
    val totalRanges = ranges.countAllChanges()
    if (rangesToCommit != 0 && rangesToCommit != totalRanges) {
      renderer.append(FontUtil.spaceAndThinSpace()).append(
        VcsBundle.message("ranges.to.commit.of.ranges.size.changes", rangesToCommit, totalRanges),
        SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
    }
  }

  override fun preDecorate(change: Change, renderer: ChangesBrowserNodeRenderer, isShowFlatten: Boolean) {
    baseDecorator?.preDecorate(change, renderer, isShowFlatten)
  }
}