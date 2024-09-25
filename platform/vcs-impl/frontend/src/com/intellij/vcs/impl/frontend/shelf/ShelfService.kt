// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf

import com.intellij.platform.kernel.withKernel
import com.intellij.vcs.impl.shared.rhizome.SelectShelveChangeEntity
import fleet.kernel.rete.collectLatest
import fleet.kernel.rete.each
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun subscribeToShelfTreeSelectionChanged(cs: CoroutineScope, listener: (SelectShelveChangeEntity) -> Unit) {
  cs.launch {
    withKernel {
      SelectShelveChangeEntity.each().collectLatest { listener(it) }
    }
  }
}