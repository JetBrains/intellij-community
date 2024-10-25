// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere

class SearchEverywhereTab(
  private val name: String,
  private val shortName: String = name,
  private val contributors: Collection<SearchEverywhereViewItemsProvider<*, *, *>>,
  private val multiSelectionSupport: Boolean = false
) {


}