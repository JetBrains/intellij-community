// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.actions

import com.intellij.platform.searchEverywhere.api.SeFilterData
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeActionsFilterData(val includeDisabled: Boolean) {
  fun toFilterData(): SeFilterData = SeFilterData(mapOf(KEY_INCLUDE_DISABLED to includeDisabled.toString()))

  companion object {
    private const val KEY_INCLUDE_DISABLED = "INCLUDE_DISABLED"

    fun fromTabData(data: SeFilterData?): SeActionsFilterData {
      val map = data?.map?.toMutableMap() ?: emptyMap()
      return SeActionsFilterData(map[KEY_INCLUDE_DISABLED] == "true")
    }
  }
}