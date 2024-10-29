// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.actionSystem.DataKey

internal val GIT_BRANCHES_TREE_SELECTION = DataKey.create<BranchesTreeSelection>("GitBranchesTreeSelection")
internal val BRANCHES_UI_CONTROLLER = DataKey.create<BranchesDashboardController>("GitBranchesUiControllerKey")