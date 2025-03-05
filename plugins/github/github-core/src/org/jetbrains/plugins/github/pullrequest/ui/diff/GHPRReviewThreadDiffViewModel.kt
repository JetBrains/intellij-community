// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel

interface GHPRReviewThreadDiffViewModel : GHPRCompactReviewThreadViewModel {
  val isVisible: StateFlow<Boolean>
  val location: StateFlow<DiffLineLocation?>
}

internal class MappedGHPRReviewThreadDiffViewModel(
  parentCs: CoroutineScope,
  private val sharedVm: GHPRCompactReviewThreadViewModel,
  mapping: Flow<MappingData>
) : GHPRReviewThreadDiffViewModel, GHPRCompactReviewThreadViewModel by sharedVm {
  private val cs = parentCs.childScope(javaClass.name)

  override val isVisible: StateFlow<Boolean> = mapping.map { it.isVisible }.stateInNow(cs, false)
  override val location: StateFlow<DiffLineLocation?> = mapping.map { it.location }.stateInNow(cs, null)

  data class MappingData(
    val isVisible: Boolean,
    val location: DiffLineLocation?
  )
}
