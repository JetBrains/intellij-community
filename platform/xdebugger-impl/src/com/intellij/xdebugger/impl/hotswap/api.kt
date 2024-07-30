// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface HotSwapProvider<T> {
  fun createChangesCollector(
    session: HotSwapSession<T>,
    coroutineScope: CoroutineScope,
    listener: SourceFileChangesListener<T>,
  ): SourceFileChangesCollector<T>

  fun performHotSwap(context: DataContext, session: HotSwapSession<T>)
}

@ApiStatus.Internal
interface HotSwapResultListener {
  fun onSuccessfulReload()
  fun onFinish()
  fun onCanceled()
}

@ApiStatus.Internal
interface SourceFileChangesCollector<T> : Disposable {
  fun getChanges(): Set<T>
  fun resetChanges()
}

@ApiStatus.Internal
fun interface SourceFileChangesListener<T> {
  fun onFileChange(change: T)
}
