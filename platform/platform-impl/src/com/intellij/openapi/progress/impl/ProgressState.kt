// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText

internal data class ProgressState(
  @ProgressText val text: String?,
  @ProgressDetails val details: String?,
  val fraction: Double,
)
