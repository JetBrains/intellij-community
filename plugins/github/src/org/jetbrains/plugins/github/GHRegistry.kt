// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.openapi.util.registry.Registry

object GHRegistry {
  fun isCombinedDiffEnabled(): Boolean = Registry.`is`("github.enable.combined.diff")
}