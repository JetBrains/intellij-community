// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.customization.GroupEnd
import com.intellij.ide.ui.customization.ToolbarAddQuickActionInfo
import com.intellij.openapi.actionSystem.IdeActions

private val insertStrategy = GroupEnd("MainToolbarNewUI", "MainToolbarVCSGroup")
  .orElse(GroupEnd("MainToolbarNewUI", IdeActions.GROUP_MAIN_TOOLBAR_LEFT))

internal class UpdatePushQuickAction: ToolbarAddQuickActionInfo(listOf("Vcs.UpdateProject", "Vcs.Push"), GitFrontendBundle.message("MainToolbarQuickActions.Git.UpdatePush.text"), AllIcons.Actions.CheckOut, insertStrategy)
internal class CommitQuickAction: ToolbarAddQuickActionInfo(listOf("CheckinProject", "Git.Commit.Stage"), GitFrontendBundle.message("MainToolbarQuickActions.Git.Commit.text"), AllIcons.Actions.Commit, insertStrategy)
internal class HistoryQuickAction: ToolbarAddQuickActionInfo(listOf("Vcs.ShowTabbedFileHistory"), GitFrontendBundle.message("MainToolbarQuickActions.Git.History.text"), AllIcons.Vcs.History, insertStrategy)
internal class RollbackQuickAction: ToolbarAddQuickActionInfo(listOf("ChangesView.Revert"), GitFrontendBundle.message("MainToolbarQuickActions.Git.Rollback.text"), AllIcons.Diff.Revert, insertStrategy)
