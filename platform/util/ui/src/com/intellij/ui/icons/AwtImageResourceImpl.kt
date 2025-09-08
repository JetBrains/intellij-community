// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import org.jetbrains.icons.api.CrossApiImageBitmapCache
import org.jetbrains.icons.api.ImageResourceWithCrossApiCache
import java.awt.Image
import java.awt.image.BufferedImage

class AwtImageResourceImpl(override val image: Image) : AwtImageResource, ImageResourceWithCrossApiCache {
  override fun getRGB(x: Int, y: Int): Int {
    if (image is BufferedImage) {
      return image.getRGB(x, y)
    } else error("Reading RGB values from non buffered image is not supported yet.")
  }

  override fun getRGBOrNull(x: Int, y: Int): Int? {
    if (x < 0 || y < 0 || x >= width || y >= height) { return null}
    return getRGB(x, y)
  }

  override val width: Int = image.getWidth(null)
  override val height: Int = image.getHeight(null)

  override val crossApiCache: CrossApiImageBitmapCache = CrossApiImageBitmapCacheImpl()

  init {
    crossApiCache.cachedBitmap(Image::class) { image }
  }
}

class AwtImageResourceProviderImpl : AwtImageResourceProvider {
  override fun fromAwtImage(image: Image): AwtImageResource {
    return AwtImageResourceImpl(image)
  }
}