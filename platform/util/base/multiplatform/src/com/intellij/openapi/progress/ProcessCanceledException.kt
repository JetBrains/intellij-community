// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.diagnostic.ControlFlowException
import kotlin.coroutines.cancellation.CancellationException

/**
 * An exception indicating that the currently running operation was terminated and should finish as soon as possible.
 *
 *
 * Usually, this exception should not be caught, swallowed, logged, or handled in any way.
 * Instead, it should be rethrown so that the infrastructure can handle it correctly.
 *
 *
 *
 * This exception can happen during almost any IDE activity, e.g. any PSI query,
 * [com.intellij.openapi.extensions.ExtensionPointName.getExtensions],
 * [com.intellij.openapi.actionSystem.AnAction.update], etc.
 *
 *
 * @see com.intellij.openapi.progress.ProgressIndicator.checkCanceled
 * @see [Threading Model](https://plugins.jetbrains.com/docs/intellij/threading-model.html)
 */
open class ProcessCanceledException : CancellationException, ControlFlowException {
  private var _cause: Throwable? = null
  override val cause: Throwable?
    get() = _cause

  constructor()

  constructor(cause: Throwable?) : super(cause?.toString()) { // repeat Throwable(Throwable) constructor logic
    if (cause is ProcessCanceledException) {
      throw IllegalArgumentException("Must not self-wrap ProcessCanceledException: ", cause)
    }
    this._cause = cause
  }

  protected constructor(message: String) : super(message)
}
