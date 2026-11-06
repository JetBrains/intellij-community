// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeTypeFilterKeys
import org.jetbrains.annotations.ApiStatus

/**
 * Marks a [SeItemsProvider] that searches with a [SeTargetItemsProvider], so that its tab can show the standard filter.
 */
@ApiStatus.Experimental
interface SeTargetsFilterProvider {
  /** The target provider that the search of this provider runs on. */
  val targetItemsProvider: SeTargetItemsProvider<*>
}
