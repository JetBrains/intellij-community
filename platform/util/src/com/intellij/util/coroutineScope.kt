// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import com.intellij.platform.util.coroutines.attachAsChildTo as attachAsChildTo2

@Deprecated(
  "The function was moved to another module and another package",
  ReplaceWith("attachAsChildTo(secondaryParent)", "com.intellij.platform.util.coroutines")
)
@Internal
@Suppress("DEPRECATION_ERROR")
fun CoroutineScope.attachAsChildTo(secondaryParent: CoroutineScope) {
  attachAsChildTo2(secondaryParent)
}

@Internal
@Experimental
fun Job.cancelOnDispose(disposable: Disposable) {
  val childDisposable = Disposable { cancel("disposed") }
  Disposer.register(disposable, childDisposable)
  job.invokeOnCompletion {
    Disposer.dispose(childDisposable)
  }
}
