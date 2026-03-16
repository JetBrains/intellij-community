// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.util.registry.Registry

internal object MergeConflictIterativeResolution {
  @JvmStatic
  fun isEnabled(): Boolean {
    return Registry.`is`("vcs.merge.conflict.iterative.resolution")
  }
}
