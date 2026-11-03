// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.equalityProviders

import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import org.jetbrains.annotations.ApiStatus

/**
 * The priorities of the platform providers. A provider with a higher priority is more important.
 * Each priority comes from the sort weight of the legacy contributor with the same id. [SeEqualityChecker] converts one to the other.
 */
private val platformProviderPriorities: Map<String, Int> = mapOf(
  SeProviderIdUtils.TOP_HIT_HOST_ID to 9950,
  SeProviderIdUtils.TOP_HIT_ID to 9950,
  SeProviderIdUtils.RECENT_FILES_ID to 9930,
  SeProviderIdUtils.CLASSES_ID to 9900,
  SeProviderIdUtils.FILES_ID to 9800,
  SeProviderIdUtils.SYMBOLS_ID to 9700,
  SeProviderIdUtils.RUN_CONFIGURATIONS_ID to 9650,
  SeProviderIdUtils.ACTIONS_ID to 9600,
  SeProviderIdUtils.NON_INDEXABLE_FILES_ID to 9000,
  SeProviderIdUtils.TEXT_ID to 8500,
)

/** The priority of a provider that has no platform priority. */
@ApiStatus.Internal
const val DEFAULT_PLATFORM_PROVIDER_PRIORITY: Int = 0

/**
 * Returns the priority of the platform provider with [providerId], or [DEFAULT_PLATFORM_PROVIDER_PRIORITY] for another provider.
 * A provider with a higher priority is more important.
 */
@ApiStatus.Internal
fun platformProviderPriority(providerId: String): Int = platformProviderPriorities[providerId] ?: DEFAULT_PLATFORM_PROVIDER_PRIORITY
