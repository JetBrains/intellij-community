/*
 * Copyright (C) 2017 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.resources.completion

import com.intellij.compose.ide.plugin.resources.vectorDrawable.preview.BaseVectorDrawablePreviewRenderer
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.Gray
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.Icon

/**
 * Generates [Icon] instances from image resources to display in the gutter
 * Based on [GutterIconFactory]
 */
internal object ComposeResourcesGutterIconFactory {
  private const val ICON_SIZE = 16

  fun renderDrawableIcon(file: VirtualFile): Icon? = try {
    val maxSize = JBUI.scale(ICON_SIZE)
    if (file.extension == "xml") renderXmlDrawableIcon(file, maxSize)
    else renderBitmapDrawable(file, maxSize)
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    null
  }

  fun createScaledIcon(bufferedImage: BufferedImage, maxWidth: Int, maxHeight: Int): Icon {
    var image = ImageUtil.ensureHiDPI(bufferedImage, ScaleContext.create())
    val imageWidth = image.getWidth(null)
    val imageHeight = image.getHeight(null)

    if (imageWidth <= maxWidth && imageHeight <= maxHeight) return IconUtil.createImageIcon(bufferedImage)

    if (bufferedImage.type == BufferedImage.TYPE_BYTE_INDEXED) {
      val bg = ImageUtil.createImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB)
      val g = bg.graphics
      g.color = Gray.TRANSPARENT
      g.fillRect(0, 0, bg.width, bg.height)
      UIUtil.drawImage(g, image, 0, 0, null)
      g.dispose()
      image = bg
    }

    val scale = (maxWidth / imageWidth.toDouble()).coerceAtMost(maxHeight / imageHeight.toDouble())
    image = ImageUtil.scaleImage(image, scale)
    return IconUtil.createImageIcon(image)
  }

  fun renderBitmapDrawable(file: VirtualFile, maxSize: Int): Icon? {
    val bufferedImage = file.inputStream.use { ImageIO.read(it) } ?: return null
    return createScaledIcon(bufferedImage, maxSize, maxSize)
  }

  private fun renderXmlDrawableIcon(file: VirtualFile, maxSize: Int): Icon? {
    val pixelSize = JBUI.pixScale(maxSize.toFloat()).toInt()
    val renderer = BaseVectorDrawablePreviewRenderer.getInstance() ?: return null
    val xmlContent = String(file.contentsToByteArray(), file.charset)
    val result = renderer.renderPreview(xmlContent, pixelSize, pixelSize)
    val bufferedImage = (result as? BaseVectorDrawablePreviewRenderer.RenderResult.Success)?.image ?: return null
    val image = ImageUtil.ensureHiDPI(bufferedImage, ScaleContext.create())
    return IconUtil.createImageIcon(image)
  }
}