// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SeProviderIdUtils {
  const val WILDCARD_ID: String = "com.intellij.WildcardProviderId"
  const val ACTIONS_ID: String = "com.intellij.ActionsItemsProvider"
  const val CLASSES_ID: String = "com.intellij.ClassSearchEverywhereItemProvider"
  const val FILES_ID: String = "com.intellij.FileSearchEverywhereItemProvider"
  const val SYMBOLS_ID: String = "com.intellij.SymbolSearchEverywhereItemProvider"
  const val TEXT_ID: String = "com.intellij.TextSearchEverywhereItemProvider"
}

@get:ApiStatus.Internal
val SeProviderId.isWildcard: Boolean
  get() = value == SeProviderIdUtils.WILDCARD_ID

@get:ApiStatus.Internal
val SeProviderId.isText: Boolean
  get() = value == SeProviderIdUtils.TEXT_ID
