// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.scale.ScaleContext
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Image
import java.net.URL

@ApiStatus.Internal
interface ImageDataLoader {
  val path: String?

  val expUIPath: String?
    get() = null

  fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): Image?

  val url: URL?

  @Experimental
  fun getCoords(): Pair<String, ClassLoader>?

  fun patch(transform: IconTransform): ImageDataLoader?

  fun isMyClassLoader(classLoader: ClassLoader): Boolean

  fun serializeToByteArray(): ImageDataLoaderDescriptor? = null

  /**
   * [com.intellij.ui.icons.ImageDescriptor]
   */
  val flags: Int
    get() = 0

  /**
   * -1 for immutable loaders
   */
  val modificationCount: Int
    get() = -1
}

@Serializable
@ApiStatus.Internal
sealed interface ImageDataLoaderDescriptor {
  fun createIcon(): ImageDataLoader?
}

@Serializable
@ApiStatus.Internal
data object EmptyImageDataLoaderDescriptor : ImageDataLoaderDescriptor {
  override fun createIcon(): ImageDataLoader? = null
}