// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText

data class ProgressState(
  val text: @ProgressText String?,
  val details: @ProgressDetails String? = null,
  val fraction: Double,
)
