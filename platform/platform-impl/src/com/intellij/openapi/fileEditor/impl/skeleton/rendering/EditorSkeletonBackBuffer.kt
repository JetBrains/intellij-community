// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.util.ui.ImageUtil
import java.awt.GraphicsConfiguration
import java.awt.image.VolatileImage

internal class EditorSkeletonBackBuffer {
  private var image: VolatileImage? = null

  fun createOrUpdateImageBuffer(gc: GraphicsConfiguration, width: Int, height: Int): VolatileImage {
    val image = image
    if (image != null && image.width == width && image.height == height && ImageUtil.isVolatileImageValid(image, gc)) {
      return image
    }
    release()
    return gc.createCompatibleVolatileImage(width, height).also { this.image = it }
  }

  fun release() {
    image?.flush()
    image = null
  }
}
