// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

internal data class CaretRepaintMetrics(@JvmField val caretHeight: Int, @JvmField val topOverhang: Int) {
  companion object {
    val EMPTY: CaretRepaintMetrics = CaretRepaintMetrics(0, 0)
  }
}
