// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import java.awt.FontMetrics

interface SwingTextTrimmerStrategy {
  fun trim(text: String, metrics: FontMetrics, availableWidth: Int): String
}
