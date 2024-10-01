// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import org.jetbrains.annotations.ApiStatus
import java.awt.FontMetrics
import java.awt.Graphics

@ApiStatus.Internal
interface TextCutStrategy {
  fun calcShownText(text: String, metrics: FontMetrics, maxWidth: Int, g: Graphics): String

  fun calcMinTextWidth(text: String, metrics: FontMetrics): Int
}