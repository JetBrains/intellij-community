// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SeTypeVisibilityStateProvider {
  suspend fun getTypeVisibilityStates(key: String = SeTypeFilterKeys.DEFAULT): List<SeTypeVisibilityStatePresentation>
}

@ApiStatus.Internal
object SeTypeFilterKeys {
  /** The only type filter of a provider that shows one. Every default in this area uses it. */
  const val DEFAULT: String = "default"
}
