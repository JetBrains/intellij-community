// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.util.ui.ImageUtil
import java.awt.GraphicsConfiguration
import java.awt.image.VolatileImage

/**
 * The volatile image of one renderer, into which a frame is painted before it goes to the screen.
 * Only one thread at a time uses it.
 */
internal class EditorSkeletonBackBuffer {
  private var image: VolatileImage? = null

  /**
   * Returns an image of [width] by [height] for [gc]. It is the last image while that image is still valid.
   */
  fun createOrUpdateImageBuffer(gc: GraphicsConfiguration, width: Int, height: Int): VolatileImage {
    val image = image
    if (image != null && image.width == width && image.height == height && ImageUtil.isVolatileImageValid(image, gc)) {
      return image
    }
    release()
    return gc.createCompatibleVolatileImage(width, height).also { this.image = it }
  }

  /**
   * Frees the image. The next call of [createOrUpdateImageBuffer] creates a new one.
   */
  fun release() {
    image?.flush()
    image = null
  }
}
