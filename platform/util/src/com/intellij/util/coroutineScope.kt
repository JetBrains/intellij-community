// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Experimental
@JvmOverloads
fun Job.cancelOnDispose(disposable: Disposable, disposeOnCompletion: Boolean = true) {
  val childDisposable = Disposable { cancel("disposed") }
  Disposer.register(disposable, childDisposable)
  if (disposeOnCompletion) {
    job.invokeOnCompletion {
      Disposer.dispose(childDisposable)
    }
  }
}
