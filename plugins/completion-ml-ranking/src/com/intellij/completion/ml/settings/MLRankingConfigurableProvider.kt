// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.settings

import com.intellij.completion.ml.sorting.RankingSupport
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider

internal class MLRankingConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable? {
    val availableRankers = RankingSupport.availableRankers()
    if (availableRankers.isEmpty()) return null
    return MLRankingConfigurable(availableRankers)
  }
}
