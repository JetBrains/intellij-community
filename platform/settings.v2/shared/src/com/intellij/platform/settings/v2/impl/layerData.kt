// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

import com.intellij.platform.settings.v2.LayerData
import com.intellij.platform.settings.v2.LayerLocality
import com.intellij.platform.settings.v2.LayerProvider
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsValue.MapValue

/**
 * Builds the [LayerData] of [provider] for [context] at [locality] from [map]. An empty map gives an empty layer, which
 * removes the entry.
 */
internal fun layerData(context: SettingsContext, provider: LayerProvider, map: MapValue, locality: LayerLocality): LayerData =
  if (map.isEmpty) LayerData.empty(context, provider, locality)
  else LayerData.Builder(context, provider, locality).apply { map.values.forEach { (id, value) -> this[id] = value } }.build()
