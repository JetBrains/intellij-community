// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.STYLE_OPAQUE
import com.intellij.util.FontUtil.spaceAndThinSpace
import com.intellij.util.ui.JBUI.insets
import com.intellij.util.ui.UIUtil.getTreeBackground
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

abstract class RepositoryChangesBrowserNodeBase<R, B>(repository: R) : ChangesBrowserNode<R>(repository),
                                                                       ChangesBrowserNode.NodeWithFilePath {
  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    renderer.icon = getIcon()
    renderer.append(" $textPresentation", REGULAR_ATTRIBUTES)
    appendCount(renderer)

    if (renderer.isShowingLocalChanges) {
      appendCurrentBranch(renderer)
    }
  }

  protected abstract fun getIcon(): Icon

  private fun appendCurrentBranch(renderer: ChangesBrowserNodeRenderer) {
    val repository = getUserObject()
    val branch = getCurrentBranch(repository)

    if (branch != null) {
      renderer.append(spaceAndThinSpace())
      renderer.append(" ${getBranchText(branch)} ", getBranchLabelAttributes(renderer.background ?: getTreeBackground()))
      renderer.setBackgroundInsets(BRANCH_BACKGROUND_INSETS)
      renderer.toolTipText = getBranchTooltipText(branch)
    }
  }

  protected open fun getCurrentBranch(repository: R): B? = null
  protected open fun getBranchText(branch: B): @Nls String = ""
  protected open fun getBranchTooltipText(branch: B): @Nls String = ""

  override fun getSortWeight(): Int = REPOSITORY_SORT_WEIGHT
}

private val BRANCH_BACKGROUND_INSETS = insets(1, 0)

private fun getBranchLabelAttributes(background: Color) =
  SimpleTextAttributes(BranchPresentation.getBranchPresentationBackground(background), BranchPresentation.TEXT_COLOR, null, STYLE_OPAQUE)
