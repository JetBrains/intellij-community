// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.searchEverywhere.frontend

import com.intellij.platform.searchEverywhere.SearchEverywhereItemsProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereTab(
  val name: String,
  val shortName: String = name,
  val providers: Collection<SearchEverywhereItemsProvider>,
  val multiSelectionSupport: Boolean = false
) {


}