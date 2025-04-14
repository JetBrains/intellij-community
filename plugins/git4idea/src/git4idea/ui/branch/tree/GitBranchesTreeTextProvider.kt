// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.popup.PopupFactoryImpl
import git4idea.GitReference
import git4idea.branch.GitRefType
import git4idea.repo.GitRepository
import git4idea.ui.branch.tree.GitBranchesTreeModel.RefUnderRepository

internal  object GitBranchesTreeTextProvider {
  fun getText(treeNode: Any?, selectedRepo: GitRepository?, isMultirepo: Boolean, isPrefixGrouping: Boolean): @NlsSafe String? {
    val value = treeNode ?: return null
    return when (value) {
      is GitRefType -> when {
        selectedRepo != null -> value.getInRepoText(DvcsUtil.getShortRepositoryName(selectedRepo))
        isMultirepo -> value.getCommonText()
        else -> value.getText()
      }
      is GitBranchesTreeModel.BranchesPrefixGroup -> value.prefix.last()
      is GitBranchesTreeModel.RefTypeUnderRepository -> value.type.getText()
      is RefUnderRepository -> getText(value.ref, selectedRepo, isMultirepo, isPrefixGrouping)
      is GitReference -> if (isPrefixGrouping) value.name.split('/').last() else value.name
      is PopupFactoryImpl.ActionItem -> value.text
      is GitBranchesTreeModel.PresentableNode -> value.presentableText
      else -> null
    }
  }

}