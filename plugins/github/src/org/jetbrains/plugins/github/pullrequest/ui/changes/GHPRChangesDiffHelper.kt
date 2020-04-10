// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider

interface GHPRChangesDiffHelper {
  fun setUp(dataProvider: GHPRDataProvider, changesProvider: GHPRChangesProvider)
  fun reset()

  fun getReviewSupport(change: Change): GHPRDiffReviewSupport?
  fun getDiffComputer(change: Change): DiffUserDataKeysEx.DiffComputer?

  companion object {
    val DATA_KEY = DataKey.create<GHPRChangesDiffHelper>("Github.PullRequest.Diff.Helper")
  }
}
