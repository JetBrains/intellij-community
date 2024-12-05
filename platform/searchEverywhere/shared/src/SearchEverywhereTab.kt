// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface SearchEverywhereTab {
  val name: String
  val shortName: String
  val providers: Collection<String> // [GIT_COMMITS, HG_COMMITS]\
}