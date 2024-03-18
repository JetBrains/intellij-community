// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

internal data class StepConfig(
  val isIndeterminate: Boolean, // false = reports fraction, true = does not report fraction
  val textLevel: Int, // 2 = text & details, 1 = text only, 0 = no text
) {

  init {
    // TODO consider supporting > 2 text levels for console logging
    require(textLevel in 0..2) {
      "Unsupported text level: $textLevel"
    }
    // existence of StepConfig instance guarantees some reporting
    require(!isIndeterminate || textLevel > 0) {
      "This step is effectively no-op"
    }
  }

  fun childConfig(indeterminate: Boolean, hasText: Boolean): StepConfig? {
    val childIndeterminate = indeterminate || isIndeterminate
    val childLevel = if (hasText && textLevel > 0) {
      textLevel - 1
    }
    else {
      textLevel
    }
    if (childLevel == 0 && childIndeterminate) {
      // no reporting from child is visible
      return null
    }
    return StepConfig(childIndeterminate, childLevel)
  }
}
