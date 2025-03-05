// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes.tree

import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangesBrowserNodeRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.JBColor.namedColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.STYLE_OPAQUE
import com.intellij.util.FontUtil.spaceAndThinSpace
import com.intellij.util.ui.CheckboxIcon
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI.insets
import com.intellij.util.ui.UIUtil.getTreeBackground
import com.intellij.platform.vcs.impl.frontend.changes.getBranchPresentationBackground
import com.intellij.platform.vcs.impl.frontend.shelf.tree.EntityChangesBrowserNode
import com.intellij.platform.vcs.impl.shared.rhizome.RepositoryNodeEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color

@ApiStatus.Internal
class RepositoryTreeNode(entity: RepositoryNodeEntity) : EntityChangesBrowserNode<RepositoryNodeEntity>(entity) {

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    renderer.icon = getRepositoryIcon()
    renderer.append(" ${getUserObject().name}", REGULAR_ATTRIBUTES)
    appendCount(renderer)

    if (renderer.isShowingLocalChanges) {
      appendCurrentBranch(renderer)
    }

  }

  private fun appendCurrentBranch(renderer: ChangesBrowserNodeRenderer) {
    val repository = getUserObject()
    val branch = repository.branchName

    if (branch != null) {
      renderer.append(spaceAndThinSpace())
      renderer.append(" ${repository.branchName} ", getBranchLabelAttributes(renderer.background ?: getTreeBackground()))
      renderer.setBackgroundInsets(BRANCH_BACKGROUND_INSETS)
      renderer.toolTipText = repository.toolTip
    }
  }

  private fun getBranchLabelAttributes(background: Color) =
    SimpleTextAttributes(getBranchPresentationBackground(background), TEXT_COLOR, null, STYLE_OPAQUE)

  //TODO Move color calculation to frontend. Otherwise wrong color can be passed from backend for CWM cases, in case of different color schemas on client and server sides.
  @Suppress("UseJBColor")
  fun getRepositoryIcon(): ColorIcon {
    val repositoryEntity = getUserObject()
    val color = Color(repositoryEntity.colorRed, repositoryEntity.colorGreen, repositoryEntity.colorBlue)
    return CheckboxIcon.createAndScale(color)
  }

  override fun doGetTextPresentation(): @Nls String? {
    return getUserObject().name
  }


  companion object {
    @JvmField
    val TEXT_COLOR: JBColor = namedColor("VersionControl.RefLabel.foreground", JBColor(Color(0x7a7a7a), Color(0x909090)))
  }
}

private val BRANCH_BACKGROUND_INSETS = insets(1, 0)