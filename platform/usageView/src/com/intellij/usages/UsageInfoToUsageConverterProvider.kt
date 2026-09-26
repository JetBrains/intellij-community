// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.usageView.UsageInfo
import org.jetbrains.annotations.ApiStatus

/** Converts a [UsageInfo] when the default usage adapter is not suitable. */
@ApiStatus.Internal
interface UsageInfoToUsageConverterProvider {
  fun convert(usageInfo: UsageInfo): Usage?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<UsageInfoToUsageConverterProvider> =
      ExtensionPointName.create("com.intellij.usageInfoToUsageConverterProvider")
  }
}
