// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.ProgressText
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable

@Experimental
@NonExtendable
@Deprecated("Use `ProgressStep` via `reportProgress` or `reportSequentialProgress`.")
interface ProgressReporter0 {

  fun step(endFraction: Double, text: ProgressText?): ProgressReporter0

  fun rawReporter(): RawProgressReporter
}
