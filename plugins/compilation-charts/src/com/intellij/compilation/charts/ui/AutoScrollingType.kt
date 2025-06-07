// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

class AutoScrollingType {
  private var type: Type = Type.YES

  fun isEnabled(): Boolean = type != Type.DISABLED
  fun isActive(): Boolean = type == Type.YES

  fun start() {
    type = Type.YES
  }

  fun stop() {
    type = Type.NO
  }

  fun enable() {
    type = Type.NO
  }

  fun disable() {
    type = Type.DISABLED
  }

  private enum class Type {
    YES,
    NO,
    DISABLED
  }
}