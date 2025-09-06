// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import org.jetbrains.icons.api.BitmapImageResource
import org.jetbrains.icons.api.ImageResource
import org.jetbrains.icons.api.ImageResourceProvider

interface AwtImageResource : BitmapImageResource {
  val image: java.awt.Image
}

interface AwtImageResourceProvider : ImageResourceProvider {
  fun fromAwtImage(image: java.awt.Image): AwtImageResource
}

fun ImageResource.Companion.fromAwtImage(image: java.awt.Image): AwtImageResource {
  val provider = ImageResourceProvider.getInstance() as? AwtImageResourceProvider
  return provider?.fromAwtImage(image) ?: error("Current ImageResource provider doesn't support AWT images: $provider")
}