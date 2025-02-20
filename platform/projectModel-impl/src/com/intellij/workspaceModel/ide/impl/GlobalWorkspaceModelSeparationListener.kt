// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache
import org.jetbrains.annotations.ApiStatus

/**
 * When the logic for separation changes, we need to rebalance existing workspace model entities from a singleton cache to multiple
 * per-environment caches.
 * The logic of rebalancing may be sophisticated, so to ensure correctness and simplify maintenance,
 * we simply require the user to reimport the project
 */
@ApiStatus.Internal
class GlobalWorkspaceModelSeparationListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    if (value.key != "ide.workspace.model.per.environment.model.separation") {
      return
    }
    GlobalWorkspaceModelCache.getInstance()?.invalidateCaches()
  }
}