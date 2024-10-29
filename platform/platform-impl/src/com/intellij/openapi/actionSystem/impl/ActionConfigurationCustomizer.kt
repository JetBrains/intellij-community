// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.OverrideOnly

/**
 * Allows customizing actions during the [ActionManager] service initialization.
 * Register in `com.intellij.actionConfigurationCustomizer` extension point.
 *
 * Please avoid using this - opt for the declarative approach instead.
 *
 * @see DynamicActionConfigurationCustomizer
 */
@Internal
interface ActionConfigurationCustomizer {
  companion object {
    @Internal
    @JvmField val EP: ExtensionPointName<ActionConfigurationCustomizer> = ExtensionPointName("com.intellij.actionConfigurationCustomizer")
  }

  /**
   * Your extension should implement one of [CustomizeStrategy]. Do not override this method.
   */
  @ApiStatus.Experimental
  fun customize(): CustomizeStrategy {
    if (this is CustomizeStrategy) {
      return this
    }

    return object : SyncHeavyCustomizeStrategy {
      override fun customize(actionManager: ActionManager) {
        @Suppress("DEPRECATION")
        this@ActionConfigurationCustomizer.customize(actionManager)
      }
    }
  }

  @Deprecated("Implement one of [CustomizeStrategy]")
  fun customize(actionManager: ActionManager) {
  }

  /**
   * The `Async` prefix in a strategy means that a call to `customize`
   * is not guarantied to occur as part of `ActionManager` constructor.
   */
  sealed interface CustomizeStrategy

  /**
   * This is the only recommended strategy for action customization.
   */
  @OverrideOnly
  interface LightCustomizeStrategy : CustomizeStrategy {
    /**
     * [actionRegistrar] is not thread-safe.
     */
    suspend fun customize(actionRegistrar: ActionRuntimeRegistrar)
  }

  @OverrideOnly
  @Internal
  interface SyncHeavyCustomizeStrategy : CustomizeStrategy {
    fun customize(actionManager: ActionManager)
  }

  @OverrideOnly
  interface AsyncLightCustomizeStrategy : CustomizeStrategy {
    /**
     * [actionRegistrar] is thread-safe.
     */
    suspend fun customize(actionRegistrar: ActionRuntimeRegistrar)
  }
}