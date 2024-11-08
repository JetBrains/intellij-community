// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.core

import com.intellij.searchEverywhere.SearchEverywhereViewItemsProvider

class SearchEverywhereTab(
  val name: String,
  val shortName: String = name,
  val providers: Collection<SearchEverywhereViewItemsProvider<*, *, *>>,
  val multiSelectionSupport: Boolean = false
) {


}