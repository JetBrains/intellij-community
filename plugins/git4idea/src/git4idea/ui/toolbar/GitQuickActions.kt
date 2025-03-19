// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.customization.GroupEnd
import com.intellij.ide.ui.customization.ToolbarAddQuickActionInfo
import com.intellij.openapi.actionSystem.IdeActions
import git4idea.i18n.GitBundle

private val insertStrategy = GroupEnd("MainToolbarNewUI", "MainToolbarVCSGroup")
  .orElse(GroupEnd("MainToolbarNewUI", IdeActions.GROUP_MAIN_TOOLBAR_LEFT))

class UpdatePushQuickAction: ToolbarAddQuickActionInfo(listOf("Vcs.UpdateProject", "Vcs.Push"), GitBundle.message("MainToolbarQuickActions.Git.UpdatePush.text"), AllIcons.Actions.CheckOut, insertStrategy)
class CommitQuickAction: ToolbarAddQuickActionInfo(listOf("CheckinProject"), GitBundle.message("MainToolbarQuickActions.Git.Commit.text"), AllIcons.Actions.Commit, insertStrategy)
class HistoryQuickAction: ToolbarAddQuickActionInfo(listOf("Vcs.ShowTabbedFileHistory"), GitBundle.message("MainToolbarQuickActions.Git.History.text"), AllIcons.Vcs.History, insertStrategy)
class RollbackQuickAction: ToolbarAddQuickActionInfo(listOf("ChangesView.Revert"), GitBundle.message("MainToolbarQuickActions.Git.Rollback.text"), AllIcons.Diff.Revert, insertStrategy)
