// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.commit

import com.intellij.icons.AllIcons
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesViewModelBuilder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.vcs.log.util.VcsUserUtil.isSamePerson
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

fun insertEditedCommitNode(builder: ChangesViewModelBuilder, editedCommit: EditedCommitPresentation) {
  when (editedCommit) {
    is EditedCommitDetails -> {
      val commitNode = EditedCommitNode(editedCommit)
      builder.insertSubtreeRoot(commitNode)
      builder.insertChanges(editedCommit.changes, commitNode)
    }
    is EditedCommitPresentation.Loading -> {
      val commitNode = EditedCommitLoadingNode(editedCommit)
      builder.insertSubtreeRoot(commitNode)
    }
  }
}

@ApiStatus.Internal
class EditedCommitNode(editedCommit: EditedCommitDetails) : ChangesBrowserNode<EditedCommitDetails>(editedCommit) {
  private val editedCommit: EditedCommitDetails get() = getUserObject()

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    if (isDifferentCommitter()) {
      renderer.icon = AllIcons.General.Warning
      renderer.toolTipText = message("amend.commit.different.committer.warning")
    }
    else {
      renderer.icon = AllIcons.Vcs.CommitNode
    }

    renderer.append(editedCommit.subject)
    appendCount(renderer)
  }

  private fun isDifferentCommitter(): Boolean {
    val currentUser = editedCommit.currentUser ?: return false
    return !isSamePerson(currentUser, editedCommit.committer)
  }

  override fun getTextPresentation(): String = editedCommit.subject
}

@ApiStatus.Internal
class EditedCommitLoadingNode(editedCommit: EditedCommitPresentation.Loading)
  : ChangesBrowserNode<EditedCommitPresentation.Loading>(editedCommit) {
  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    renderer.append(textPresentation!!, SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }

  override fun getTextPresentation(): @Nls String? {
    return message("amend.commit.tree.node.loading")
  }
}
