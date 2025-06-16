// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SeLegacyItem : SeItem {
  val contributor: SearchEverywhereContributor<*>
  val rawObject: Any
}