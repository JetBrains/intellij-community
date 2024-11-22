// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.core

import com.intellij.searchEverywhere.shared.SearchEverywhereItemsProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereTab(
  val name: String,
  val shortName: String = name,
  val providers: Collection<SearchEverywhereItemsProvider>,
  val multiSelectionSupport: Boolean = false
) {


}