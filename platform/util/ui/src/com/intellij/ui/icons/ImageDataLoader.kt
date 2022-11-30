// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import org.jetbrains.annotations.ApiStatus
import java.awt.Image
import java.net.URL

@ApiStatus.Internal
interface ImageDataLoader {
  fun loadImage(parameters: LoadIconParameters): Image?

  val url: URL?

  fun patch(originalPath: String, transform: IconTransform): ImageDataLoader?

  fun isMyClassLoader(classLoader: ClassLoader): Boolean

  val flags: Int
    get() = 0
}