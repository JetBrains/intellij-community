// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider

interface GHPRChangesDiffHelper {
  fun setUp(dataProvider: GithubPullRequestDataProvider, changesProvider: GHPRChangesProvider)
  fun reset()

  fun getReviewSupport(change: Change): GHPRDiffReviewSupport?
  fun getDiffComputer(change: Change): DiffUserDataKeysEx.DiffComputer?
}
