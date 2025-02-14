// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.trialPromotion

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

object TrialPromotionBundle {
  private const val PATH_TO_BUNDLE = "messages.TrialPromotionBundle"
  private val bundle by lazy { DynamicBundle(TrialPromotionBundle::class.java, PATH_TO_BUNDLE); }

  @Nls
  fun message(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) key: String, vararg params: Any): String =
    bundle.getMessage(key, *params)
}
