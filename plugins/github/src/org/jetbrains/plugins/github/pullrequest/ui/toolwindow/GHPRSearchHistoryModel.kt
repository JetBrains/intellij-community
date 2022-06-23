// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.codereview.list.search.PersistingReviewListSearchHistoryModel
import kotlinx.coroutines.CoroutineScope

internal class GHPRSearchHistoryModel(scope: CoroutineScope, private val persistentHistoryComponent: GHPRListPersistentSearchHistory)
  : PersistingReviewListSearchHistoryModel<GHPRListSearchValue>(scope) {

  override var persistentHistory: List<GHPRListSearchValue>
    get() = persistentHistoryComponent.history
    set(value) {
      persistentHistoryComponent.history = value
    }
}