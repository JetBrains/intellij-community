// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.branch

import com.intellij.openapi.vcs.VcsBundle
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls

object BranchPresentation {
  @Nls
  fun getText(branches: Collection<BranchData>): String {
    val distinct = branches.distinctBy { it.branchName }
    return when (distinct.size) {
      0 -> ""
      1 -> getPresentableText(distinct.first())
      else -> "${getPresentableText(distinct.first())},..."
    }
  }

  @Nls
  fun getPresentableText(branch: BranchData) = if (branch is LinkedBranchData) branch.branchName ?: "!" else branch.branchName.orEmpty()

  @Nls
  fun getTooltip(branches: Collection<BranchData>): String? {
    val distinct = branches.distinctBy { it.branchName to (it as? LinkedBranchData)?.linkedBranchName }
    return when (distinct.size) {
      0 -> null
      1 -> getSingleTooltip(distinct.first())
      else -> branches.sortedBy { it.presentableRootName }.joinToString("") { getMultiTooltip(it) }
    }
  }

  @Nls
  fun getSingleTooltip(branch: BranchData): String? = if (branch is LinkedBranchData && branch.branchName != null)
    branch.linkedBranchName?.let { "${branch.branchName} ${UIUtil.rightArrow()} $it" } ?: VcsBundle.message("changes.no.tracking.branch")
  else null

  @Nls
  fun getMultiTooltip(branch: BranchData): String {
    val linkedBranchPart = if (branch is LinkedBranchData && branch.branchName != null) {
      branch.linkedBranchName?.let { " ${UIUtil.rightArrow()} $it" } ?: VcsBundle.message("changes.no.tracking.branch.suffix")
    }
    else ""

    return "<tr><td>${branch.presentableRootName}:</td><td>${getPresentableText(branch)}$linkedBranchPart</td></tr>" // NON-NLS
  }
}
