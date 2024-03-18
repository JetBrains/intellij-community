// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.ProgressText

sealed interface ProgressState {

  val fraction: Double?

  val text: ProgressText?

  val details: ProgressText?
}
