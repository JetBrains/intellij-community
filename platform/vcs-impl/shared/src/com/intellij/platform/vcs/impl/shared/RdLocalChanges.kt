// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object RdLocalChanges {
  @JvmStatic
  fun isEnabled(): Boolean = Registry.`is`("vcs.rd.local.changes.enabled", false)
}