// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

import com.intellij.openapi.util.Computable

/**
 * Installs thread-local [ProgressIndicator] which delegates its cancellation checks to a given [progress].
 * This method should be used to call methods which rely on [ProgressManager.checkCanceled].
 */
fun <T> runUnderIndicator(progress: Progress, action: () -> T): T {
  if (progress is JobProgress) {
    // no sink because this `runUnderIndicator` overload is used with `Progress` instance,
    // which is supposed to only handle the cancellation.
    return runUnderIndicator(progress.job, null, action)
  }
  val indicator = object : EmptyProgressIndicatorBase() {

    @Volatile
    var myProgress: Progress? = null

    override fun cancel() {
      error("'cancel' must not be called from inside the action")
    }

    override fun isCanceled(): Boolean {
      return myProgress?.isCancelled == true
    }
  }
  try {
    return ProgressManager.getInstance().runProcess(Computable {
      // set progress inside runProcess to avoid cancelled indicator before even starting the computation
      indicator.myProgress = progress
      action()
    }, indicator)
  }
  catch (pce: ProcessCanceledException) {
    // if canceled, then the next line will throw CancellationException
    progress.checkCancelled()

    // no exception from previous line
    // => progress was not canceled
    // => PCE was thrown manually
    // => treat it as any other exception
    throw pce
  }
}
