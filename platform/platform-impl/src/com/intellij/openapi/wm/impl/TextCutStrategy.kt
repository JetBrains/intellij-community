// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import java.awt.FontMetrics

interface TextCutStrategy {
  fun calcShownText(text: String, metrics: FontMetrics, maxWidth: Int): String
}