// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress;

import com.intellij.platform.util.progress.impl.ProgressText

internal data class StepState(
  override val fraction: Double?,
  override val text: ProgressText?,
  override val details: ProgressText?,
) : ProgressState
