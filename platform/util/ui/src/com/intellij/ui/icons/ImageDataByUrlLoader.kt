// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader.createNewResolverIfNeeded
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.loadImage
import org.jetbrains.annotations.ApiStatus
import java.awt.Image
import java.net.URL

private val UNRESOLVED_URL = URL("file:///unresolved")

@ApiStatus.Internal
class ImageDataByUrlLoader private constructor(
  private val ownerClass: Class<*>? = null,
  private val classLoader: ClassLoader? = null,
  private val useCacheOnLoad: Boolean = true,
  private val overriddenPath: String? = null,
  override val url: URL,
) : ImageDataLoader {
  internal constructor(url: URL, classLoader: ClassLoader?, useCacheOnLoad: Boolean) :
    this(classLoader = classLoader, useCacheOnLoad = useCacheOnLoad, overriddenPath = null, url = url)

  constructor(url: URL, path: String, classLoader: ClassLoader?) : this(overriddenPath = path, classLoader = classLoader, url = url)

  override fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): Image? {
    var path = overriddenPath
    if (path == null || ownerClass == null && (classLoader == null || !path.startsWith('/'))) {
      path = url.toString()
    }
    return loadImage(path = path,
                     filters = parameters.filters,
                     colorPatcherProvider = parameters.colorPatcher,
                     resourceClass = ownerClass,
                     classLoader = classLoader,
                     isDark = parameters.isDark,
                     useCache = useCacheOnLoad,
                     scaleContext = scaleContext)
  }

  override fun patch(originalPath: String, transform: IconTransform): ImageDataLoader? {
    return createNewResolverIfNeeded(originalClassLoader = classLoader, originalPath = originalPath, transform = transform)
  }

  override fun isMyClassLoader(classLoader: ClassLoader): Boolean = this.classLoader === classLoader

  override fun toString(): String {
    return "UrlResolver(ownerClass=${ownerClass?.name}, classLoader=$classLoader, overriddenPath=$overriddenPath, url=$url, useCacheOnLoad=$useCacheOnLoad)"
  }
}

internal class ImageDataByPathResourceLoader(
  private val ownerClass: Class<*>? = null,
  private val classLoader: ClassLoader? = null,
  private val strict: Boolean,
  private val path: String,
) : ImageDataLoader {
  @Volatile
  override var url: URL? = UNRESOLVED_URL
    get() {
      var result = field
      if (result === UNRESOLVED_URL) {
        result = try {
          resolveUrl(path = path, classLoader = classLoader, ownerClass = ownerClass, strict = strict)
        }
        finally {
          field = result
        }
      }
      return result
    }

  override fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): Image? {
    return loadImage(path = path,
                     filters = parameters.filters,
                     colorPatcherProvider = parameters.colorPatcher,
                     resourceClass = ownerClass,
                     classLoader = classLoader,
                     isDark = parameters.isDark,
                     useCache = false,
                     scaleContext = scaleContext)
  }

  override fun patch(originalPath: String, transform: IconTransform): ImageDataLoader? {
    return createNewResolverIfNeeded(originalClassLoader = classLoader, originalPath = originalPath, transform = transform)
  }

  override fun isMyClassLoader(classLoader: ClassLoader): Boolean = this.classLoader === classLoader

  override fun toString(): String = "UrlResolver(ownerClass=${ownerClass?.name}, classLoader=$classLoader, path=$path)"
}

private fun resolveUrl(path: String?,
                       classLoader: ClassLoader?,
                       ownerClass: Class<*>?,
                       strict: Boolean): URL? {
  var effectivePath = path
  var url: URL? = null
  if (effectivePath != null) {
    if (classLoader != null) {
      // paths in ClassLoader getResource must not start with "/"
      effectivePath = effectivePath.removePrefix("/")
      url = findUrl(path = effectivePath, urlProvider = classLoader::getResource)
    }
    if (url == null && ownerClass != null) {
      // some plugins use findIcon("icon.png",IconContainer.class)
      url = findUrl(path = effectivePath, urlProvider = ownerClass::getResource)
    }
  }
  if (url == null && strict) {
    throw RuntimeException("Can't find icon in '$effectivePath' near $classLoader")
  }
  return url
}

private inline fun findUrl(path: String, urlProvider: (String) -> URL?): URL? {
  urlProvider(path)?.let {
    return it
  }

  // Find either PNG or SVG icon.
  // The icon will then be wrapped into CachedImageIcon,
  // which will load a proper icon version depending on the context - UI theme, DPI.
  // SVG version, when present, has more priority than PNG.
  // See for details: com.intellij.util.ImageLoader.ImageDescList#create
  var effectivePath = path
  when {
    effectivePath.endsWith(".png") -> effectivePath = effectivePath.substring(0, effectivePath.length - 4) + ".svg"
    effectivePath.endsWith(".svg") -> effectivePath = effectivePath.substring(0, effectivePath.length - 4) + ".png"
    else -> logger<ImageDataLoader>().debug("unexpected path: ", effectivePath)
  }
  return urlProvider(effectivePath)
}
