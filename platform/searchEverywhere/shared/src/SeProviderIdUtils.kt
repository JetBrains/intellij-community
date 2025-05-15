// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.find.impl.TextSearchContributor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SeProviderIdUtils {
  const val WILDCARD_ID: String = "com.intellij.WildcardProviderId"
  const val ACTIONS_ID: String = "ActionSearchEverywhereContributor"
  const val CLASSES_ID: String = "ClassSearchEverywhereContributor"
  const val FILES_ID: String = "FileSearchEverywhereContributor"
  const val SYMBOLS_ID: String = "SymbolSearchEverywhereContributor"
  const val TEXT_ID: String = TextSearchContributor.ID
  const val RECENT_FILES_ID: String = "RecentFilesSEContributor"
  const val RUN_CONFIGURATIONS_ID: String = "RunConfigurationsSEContributor"

  const val TOP_HIT_ID: String = "TopHitSEContributor"
  const val TOP_HIT_HOST_ID: String = "TopHitSEContributor-Host"
}

@get:ApiStatus.Internal
val SeProviderId.isWildcard: Boolean
  get() = value == SeProviderIdUtils.WILDCARD_ID

@get:ApiStatus.Internal
val SeProviderId.isText: Boolean
  get() = value == SeProviderIdUtils.TEXT_ID
