// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup.compose

import com.intellij.openapi.util.TextRange
import com.intellij.ui.speedSearch.SpeedSearch
import git4idea.GitBranch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class GitBranchComposeVm(
  coroutineScope: CoroutineScope,
  branch: GitBranch,
  speedSearchText: StateFlow<String>,
  val isFavorite: StateFlow<Boolean>,
  private val toggleIsFavoriteState: () -> Unit,
  val isCurrent: Boolean,
  private val speedSearch: SpeedSearch,
) {
  val name: String = branch.name

  val matchingFragments: StateFlow<List<TextRange>> = speedSearchText.map {
    speedSearch.matchingFragments(name)?.toList() ?: emptyList()
  }.stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

  fun toggleIsFavourite() {
    toggleIsFavoriteState()
  }
}