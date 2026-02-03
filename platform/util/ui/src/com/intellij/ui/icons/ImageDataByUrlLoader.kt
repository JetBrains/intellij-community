// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.scale.ScaleContext
import org.jetbrains.annotations.ApiStatus
import java.awt.Image
import java.lang.ref.WeakReference
import java.net.MalformedURLException
import java.net.URL

private val UNRESOLVED_URL = URL("$FILE_SCHEME_PREFIX//unresolved")

@ApiStatus.Internal
internal class ImageDataByUrlLoader internal constructor(
  private val ownerClass: Class<*>? = null,
  private val classLoader: ClassLoader? = null,
  override val url: URL,
) : ImageDataLoader {
  override val path: String
    get() = url.toString()

  override fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): Image? {
    return loadImage(path = url.toString(),
                     filters = parameters.filters,
                     colorPatcherProvider = parameters.colorPatcher,
                     resourceClass = ownerClass,
                     classLoader = classLoader,
                     isDark = parameters.isDark,
                     useCache = false,
                     scaleContext = scaleContext)
  }

  override fun patch(transform: IconTransform): ImageDataLoader? {
    return createNewResolverIfNeeded(originalClassLoader = classLoader, originalPath = path, transform = transform)
  }

  override fun isMyClassLoader(classLoader: ClassLoader): Boolean = this.classLoader === classLoader

  override fun getCoords(): Pair<String, ClassLoader>? = classLoader?.let { path to classLoader }

  override fun toString(): String {
    return "ImageDataByUrlLoader(ownerClass=${ownerClass?.name}, classLoader=$classLoader, url=$url"
  }
}

internal class ImageDataByPathResourceLoader(
  private val ownerClass: Class<*>? = null,
  private val classLoader: ClassLoader? = null,
  private val strict: Boolean,
  override val path: String,
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

  override fun patch(transform: IconTransform): ImageDataLoader? {
    return createNewResolverIfNeeded(originalClassLoader = classLoader, originalPath = path, transform = transform)
  }

  override fun isMyClassLoader(classLoader: ClassLoader): Boolean = this.classLoader === classLoader

  override fun getCoords(): Pair<String, ClassLoader>? = classLoader?.let { path to classLoader }

  override fun toString(): String = "ImageDataByPathResourceLoader(ownerClass=${ownerClass?.name}, classLoader=$classLoader, path=$path)"
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
  // The SVG version, when present, has more priority than PNG.
  // See for details: com.intellij.util.ImageLoader.ImageDescList#create
  var effectivePath = path
  when {
    effectivePath.endsWith(".png") -> effectivePath = effectivePath.substring(0, effectivePath.length - 4) + ".svg"
    effectivePath.endsWith(".svg") -> effectivePath = effectivePath.substring(0, effectivePath.length - 4) + ".png"
    else -> logger<ImageDataLoader>().warn("unexpected path: $effectivePath")
  }
  return urlProvider(effectivePath)
}

internal class ImageDataByFilePathLoader(override val path: String) : PatchedImageDataLoader {
  override val url: URL
    get() = URL(path)

  override fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): Image? {
    return loadImage(path = path,
                     filters = parameters.filters,
                     colorPatcherProvider = parameters.colorPatcher,
                     resourceClass = null,
                     classLoader = null,
                     isDark = parameters.isDark,
                     useCache = false,
                     scaleContext = scaleContext)
  }

  override fun getCoords(): Pair<String, ClassLoader>? = null

  override fun toString(): String = "ImageDataByFilePathLoader(path=$path)"
}

private fun createNewResolverIfNeeded(originalClassLoader: ClassLoader?, originalPath: String, transform: IconTransform): ImageDataLoader? {
  val patchedPath = transform.patchPath(originalPath, originalClassLoader) ?: return null
  val classLoader = if (patchedPath.second == null) originalClassLoader else patchedPath.second
  val path = patchedPath.first
  if (path.startsWith('/')) {
    return FinalImageDataLoader(path = path.substring(1), classLoader = classLoader ?: transform.javaClass.classLoader)
  }

  // This uses case for temp themes only. Here we want to immediately replace the existing icon with a local one.
  if (path.startsWith(FILE_SCHEME_PREFIX)) {
    try {
      return ImageDataByFilePathLoader(path)
    }
    catch (ignore: MalformedURLException) {
    }
  }
  return null
}

private class FinalImageDataLoader(override val path: String, classLoader: ClassLoader) : PatchedImageDataLoader {
  private val classLoaderRef = WeakReference(classLoader)

  override fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): Image? {
    val classLoader = classLoaderRef.get() ?: return null
    return loadImage(path = path,
                     useCache = false,
                     isDark = parameters.isDark,
                     filters = parameters.filters,
                     colorPatcherProvider = parameters.colorPatcher,
                     scaleContext = scaleContext,
                     resourceClass = null,
                     classLoader = classLoader)
  }

  override val url: URL?
    get() = classLoaderRef.get()?.getResource(path)

  override fun isMyClassLoader(classLoader: ClassLoader): Boolean = classLoaderRef.get() === classLoader

  override fun getCoords(): Pair<String, ClassLoader>? = classLoaderRef.get()?.let { path to it }

  override fun toString(): String = "FinalImageDataLoader(classLoader=${classLoaderRef.get()}, path='$path')"
}

private interface PatchedImageDataLoader : ImageDataLoader {
  // this resolver is already produced as a result of a patch
  override fun patch(transform: IconTransform): ImageDataLoader? = null

  override fun isMyClassLoader(classLoader: ClassLoader): Boolean = false
}

internal object EmptyImageDataLoader : ImageDataLoader {
  override val path: String? get() = null
  override val url: URL? get() = null

  override fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): Image? = null
  override fun patch(transform: IconTransform): ImageDataLoader? = null
  override fun isMyClassLoader(classLoader: ClassLoader): Boolean = false
  override fun getCoords(): Pair<String, ClassLoader>? = null
}
