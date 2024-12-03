// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.async.stateInNow
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel

interface GHPRReviewFileEditorThreadViewModel : GHPRCompactReviewThreadViewModel {
  val isVisible: StateFlow<Boolean>
  val line: StateFlow<Int?>
}

internal class MappedGHPRReviewEditorThreadViewModel(
  parentCs: CoroutineScope,
  private val sharedVm: GHPRCompactReviewThreadViewModel,
  mapping: Flow<MappingData>
) : GHPRReviewFileEditorThreadViewModel, GHPRCompactReviewThreadViewModel by sharedVm {
  private val cs = parentCs.childScope(javaClass.name)

  override val isVisible: StateFlow<Boolean> = mapping.map { it.isVisible }.stateInNow(cs, false)
  override val line: StateFlow<Int?> = mapping.map { it.line }.stateInNow(cs, null)

  data class MappingData(
    val isVisible: Boolean,
    val line: Int?
  )
}
