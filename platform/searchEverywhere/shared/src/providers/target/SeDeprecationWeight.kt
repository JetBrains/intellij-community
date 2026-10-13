// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.searchEverywhere.SeComposedWeight
import com.intellij.platform.searchEverywhere.SeWeightComponent
import com.intellij.platform.searchEverywhere.SeWeightKey
import com.intellij.platform.searchEverywhere.presentations.isPresentableTextStrikethrough
import org.jetbrains.annotations.ApiStatus

/**
 * Puts the [SeWeightKey.NOT_DEPRECATED] component of [presentation] into [weight].
 *
 * A deprecated item has a strikethrough on the presentable text. The color scheme sets the strikethrough.
 */
fun withDeprecationComponent(weight: SeComposedWeight, presentation: TargetPresentation): SeComposedWeight {
  val component = SeWeightComponent(SeWeightKey.NOT_DEPRECATED, if (presentation.isPresentableTextStrikethrough()) 0 else 1)
  return weight.with(component)
}
