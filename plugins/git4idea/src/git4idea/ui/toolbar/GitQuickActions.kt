// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.ide.ui.customization.GroupEnd
import com.intellij.ide.ui.customization.ToolbarAddQuickActionsAction

private val insertStrategy = GroupEnd("MainToolbarNewUI", "MainToolbarVCSGroup")
  .orElse(GroupEnd("MainToolbarNewUI", "MainToolbarLeft"))

class UpdateQuickAction: ToolbarAddQuickActionsAction(listOf("Vcs.UpdateProject"), "MainToolbarNewUI", insertStrategy) {}
class CommitQuickAction: ToolbarAddQuickActionsAction(listOf("CheckinProject"), "MainToolbarNewUI", insertStrategy) {}
class PushQuickAction: ToolbarAddQuickActionsAction(listOf("Vcs.Push"), "MainToolbarNewUI", insertStrategy) {}
class DiffQuickAction: ToolbarAddQuickActionsAction(listOf("Compare.SameVersion"), "MainToolbarNewUI", insertStrategy) {}
class HistoryQuickAction: ToolbarAddQuickActionsAction(listOf("Vcs.ShowTabbedFileHistory"), "MainToolbarNewUI", insertStrategy) {}
class RollbackQuickAction: ToolbarAddQuickActionsAction(listOf("ChangesView.Revert"), "MainToolbarNewUI", insertStrategy) {}