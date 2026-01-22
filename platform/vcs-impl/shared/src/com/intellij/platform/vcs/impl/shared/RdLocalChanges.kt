// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.idea.AppModeAssertions
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object RdLocalChanges {
  const val REGISTRY_KEY: String = "vcs.rd.local.changes.forced.mode"

  /**
   * Enabled by default in RD mode and can be overridden via registry.
   * Even though it mentions "RD", it can also affect execution in the monolith mode by forcing it to use RPC
   * instead of regular methods invocations.
   *
   * @see [com.intellij.vcs.changes.viewModel.ChangesViewProxy]
   */
  @JvmStatic
  fun isEnabled(): Boolean {
    val registryValue = Registry.get(REGISTRY_KEY)
    return if (registryValue.isChangedFromDefault()) {
      registryValue.asBoolean()
    } else {
      !AppModeAssertions.isMonolith()
        // Temporary disable in UI tests until AT-3942 is resolved
        && !ApplicationManagerEx.isInIntegrationTest()
    }
  }
}