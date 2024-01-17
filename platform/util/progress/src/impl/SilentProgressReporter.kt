// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.intellij.platform.util.progress.EmptyRawProgressReporter
import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.RawProgressReporter
import kotlinx.coroutines.CoroutineScope

internal class SilentProgressReporter(parentScope: CoroutineScope) : BaseProgressReporter(parentScope) {

  override fun createStep(duration: Double, text: ProgressText?): ProgressReporter {
    return EmptyProgressReporter
  }

  override fun asRawReporter(): RawProgressReporter {
    return EmptyRawProgressReporter
  }
}
