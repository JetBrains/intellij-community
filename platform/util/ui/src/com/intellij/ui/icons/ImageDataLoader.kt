// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.scale.ScaleContext
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Image
import java.net.URL

sealed interface ImageDataLoader {
  fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): Image?

  val url: URL?

  @Experimental
  fun getCoords(): Pair<String, ClassLoader>? = null

  fun patch(originalPath: String, transform: IconTransform): ImageDataLoader?

  fun isMyClassLoader(classLoader: ClassLoader): Boolean

  val flags: Int
    get() = 0
}