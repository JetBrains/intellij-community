// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model

import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowViewModel
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.flow.mapStateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel

@ApiStatus.Experimental
class GHPRToolWindowViewModel internal constructor(
  parentCs: CoroutineScope,
  vm: GHPRProjectViewModel
) : ReviewToolwindowViewModel<GHPRToolWindowProjectViewModel> {
  private val cs = parentCs.childScope(javaClass.name)

  override val projectVm: StateFlow<GHPRToolWindowProjectViewModel?> = vm.connectedProjectVm.mapStateIn(cs, SharingStarted.Eagerly) {
    it as GHPRToolWindowProjectViewModel?
  }
}