// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import org.jetbrains.icons.api.CrossApiImageBitmapCache
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KClass

class CrossApiImageBitmapCacheImpl : CrossApiImageBitmapCache {
  private val cache: ConcurrentMap<KClass<*>, Any> = ConcurrentHashMap()

  override fun <TBitmap : Any> cachedBitmap(bitmapClass: KClass<TBitmap>, generator: () -> TBitmap): TBitmap {
    val bitmap = cache.computeIfAbsent(bitmapClass) { generator() }
    if (bitmap == null || !bitmapClass.isInstance(bitmap)) error("Unexpected type of cached bitmap")
    @Suppress("UNCHECKED_CAST")
    return bitmap as TBitmap
  }
}