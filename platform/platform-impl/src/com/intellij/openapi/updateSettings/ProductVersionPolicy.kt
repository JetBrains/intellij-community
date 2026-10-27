// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus

/**
 * Restricts the target builds that [com.intellij.openapi.updateSettings.impl.UpdateStrategy] offers.
 * Every registered policy must allow a target build. Every caller of the strategy applies the filter.
 */
@ApiStatus.Internal
interface ProductVersionPolicy {
  /**
   * Returns whether the updater can offer [build].
   * This check does not restrict the running IDE, intermediate patches, or external installers.
   */
  fun isVersionAllowed(build: BuildNumber): Boolean

  companion object {
    @ApiStatus.Internal
    @JvmField
    val EP_NAME: ExtensionPointName<ProductVersionPolicy> = ExtensionPointName("com.intellij.productVersionPolicy")
  }
}
