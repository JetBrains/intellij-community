// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.FeaturePromoBundle"

@ApiStatus.Internal
object FeaturePromoBundle {
  private val instance = DynamicBundle(FeaturePromoBundle::class.java, BUNDLE)

  @Nls
  @JvmStatic
  fun message(@NonNls @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    return instance.getMessage(key, *params)
  }
}
