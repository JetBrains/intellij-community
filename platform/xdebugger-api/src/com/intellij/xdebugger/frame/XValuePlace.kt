// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

import kotlinx.serialization.Serializable

/**
 * Describes place where value can be shown.
 */
@Serializable
enum class XValuePlace {
  TREE, TOOLTIP
}
