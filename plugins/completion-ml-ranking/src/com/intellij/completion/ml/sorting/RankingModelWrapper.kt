// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.internal.ml.completion.DecoratingItemsPolicy

interface RankingModelWrapper {
  fun version(): String?

  fun canScore(features: RankingFeatures): Boolean

  fun score(features: RankingFeatures): Double?

  fun decoratingPolicy(): DecoratingItemsPolicy
}
