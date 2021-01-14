// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview.avatar

import com.google.common.cache.CacheBuilder
import com.intellij.ui.DeferredIconImpl
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import java.util.concurrent.TimeUnit
import javax.swing.Icon

abstract class CachingAvatarIconsProvider<T>(private val defaultIcon: Icon) : AvatarIconsProvider<T> {

  private val iconsCache = CacheBuilder.newBuilder()
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .build<Pair<T, Int>, Icon>()

  override fun getIcon(key: T?, iconSize: Int): Icon {
    if (key == null) return IconUtil.resizeSquared(defaultIcon, iconSize)
    return iconsCache.get(key to iconSize) {
      DeferredAvatarIcon(key, iconSize)
    }
  }

  protected abstract fun loadImage(key: T): Image?

  private inner class DeferredAvatarIcon(private val key: T, size: Int) : Icon {
    private val baseIcon = IconUtil.resizeSquared(defaultIcon, size)

    private val scaledIconCache = ScaleContext.Cache<Icon> { scaleCtx ->
      DeferredIconImpl(baseIcon, key, false) {
        try {
          val image = loadImage(it)
          val hidpiImage = ImageUtil.ensureHiDPI(image, scaleCtx)
          val scaledSize = scaleCtx.apply(size.toDouble(), ScaleType.USR_SCALE).toInt()
          val scaledImage = ImageUtil.scaleImage(hidpiImage, scaledSize, scaledSize)
          IconUtil.createImageIcon(scaledImage)
        }
        catch (e: Exception) {
          baseIcon
        }
      }
    }

    override fun getIconHeight() = baseIcon.iconHeight
    override fun getIconWidth() = baseIcon.iconWidth

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
      scaledIconCache.getOrProvide(ScaleContext.create(c))?.paintIcon(c, g, x, y)
    }
  }
}