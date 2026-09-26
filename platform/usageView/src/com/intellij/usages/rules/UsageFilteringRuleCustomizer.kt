// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.rules

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface UsageFilteringRuleCustomizer {

  companion object {
    val EP: ExtensionPointName<UsageFilteringRuleCustomizer> = ExtensionPointName("com.intellij.usageFilteringRuleCustomizer")
  }

  /**
   * @return true if this particular rule state (active/inactive) should not be persisted between different usage sessions (views)
   */
  fun transientRule(ruleId: String?): Boolean
}