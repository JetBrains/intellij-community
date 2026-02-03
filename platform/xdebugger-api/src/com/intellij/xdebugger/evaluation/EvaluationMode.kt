// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.evaluation

import kotlinx.serialization.Serializable

@Serializable
enum class EvaluationMode {
  EXPRESSION, CODE_FRAGMENT
}
