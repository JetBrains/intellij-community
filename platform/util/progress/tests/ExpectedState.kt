// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText

data class ExpectedState(
  val fraction: Double? = null,
  val text: @ProgressText String? = null,
  val details: @ProgressDetails String? = null,
)
