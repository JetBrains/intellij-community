// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.files

import com.intellij.platform.searchEverywhere.api.SeFilterData
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFilesFilterData(val isProjectOnly: Boolean) {
  fun toFilterData(): SeFilterData = SeFilterData(mapOf(KEY_IS_PROJECT_ONLY to isProjectOnly.toString()))

  companion object {
    private const val KEY_IS_PROJECT_ONLY = "IS_PROJECT_ONLY"

    fun fromTabData(data: SeFilterData?): SeFilesFilterData {
      val map = data?.map?.toMutableMap() ?: emptyMap()
      return SeFilesFilterData(map[KEY_IS_PROJECT_ONLY] == "true")
    }
  }
}