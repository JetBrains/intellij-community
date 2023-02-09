// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.actionSystem.DataKey

internal val GIT_BRANCHES = DataKey.create<List<BranchInfo>>("GitBranchKey")
internal val GIT_BRANCH_FILTERS = DataKey.create<List<String>>("GitBranchFilterKey")
internal val GIT_BRANCH_REMOTES = DataKey.create<Set<RemoteInfo>>("GitBranchRemotesKey")
internal val GIT_BRANCH_DESCRIPTORS = DataKey.create<Set<BranchNodeDescriptor>>("GitBranchDescriptorsKey")
internal val BRANCHES_UI_CONTROLLER = DataKey.create<BranchesDashboardController>("GitBranchesUiControllerKey")