// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service
internal class TipsOrderUtil {
  /**
   * Reorders tips to show the most useful ones in the beginning
   *
   * @return object that contains sorted tips and describes approach of how the tips are sorted
   */
  fun sort(tips: List<TipAndTrickBean>): RecommendationDescription {
    // todo: implement new sorting algorithm
    return RecommendationDescription("shuffle", tips.shuffled(), "1")
  }

  companion object {
    @JvmStatic
    fun getInstance(): TipsOrderUtil = service()
  }
}

internal data class RecommendationDescription(val algorithm: String, val tips: List<TipAndTrickBean>, val version: String?)

