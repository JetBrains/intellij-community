// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Weigher
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.hasher
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.seededHasher
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.ui.svg.loadSvgAndCacheIfApplicable
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SVGLoader
import com.intellij.util.io.URLUtil
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.ImageFilter
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val LOG: Logger
  get() = logger<ImageCache>()

internal fun clearImageCache() {
  ImageCache.clearCache()
}

private object ImageCache {
  @JvmField
  val ioMissCache: MutableSet<String> = ConcurrentHashMap.newKeySet()

  @JvmField
  val imageCache: Cache<CacheKey, BufferedImage> = Caffeine.newBuilder()
    .expireAfterAccess(1, TimeUnit.HOURS)
    // 32 MB
    .maximumWeight(32 * 1024 * 1024)
    .weigher(Weigher { _: CacheKey, value: BufferedImage -> 4 * value.width * value.height })
    .build()

  fun clearCache() {
    imageCache.invalidateAll()
    ioMissCache.clear()
  }
}

@ApiStatus.Internal
fun loadImageByClassLoader(path: String, classLoader: ClassLoader, scaleContext: ScaleContext): Image? {
  return loadImage(path = path, isDark = StartupUiUtil.isDarkTheme, scaleContext = scaleContext, classLoader = classLoader)
}

@TestOnly
fun loadImageFromUrlWithoutCache(path: String): Image? {
  // We can't check all 3rd party plugins and convince the authors to add @2x icons.
  // In IDE-managed HiDPI mode with a scale > 1.0 we scale images manually - pass isUpScaleNeeded = true
  return loadImage(path = path, useCache = false, classLoader = null)
}

internal fun loadImage(path: String,
                       resourceClass: Class<*>? = null,
                       classLoader: ClassLoader?,
                       scaleContext: ScaleContext = ScaleContext.create(),
                       isDark: Boolean = StartupUiUtil.isDarkTheme,
                       colorPatcherProvider: SVGLoader.SvgElementColorPatcherProvider? = null,
                       filters: List<ImageFilter> = emptyList(),
                       useCache: Boolean = true): Image? {
  val start = StartUpMeasurer.getCurrentTimeIfEnabled()
  val descriptors = createImageDescriptorList(path = path,
                                              isDark = isDark,
                                              pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat())

  val lastDotIndex = path.lastIndexOf('.')
  val rawPathWithoutExt: String
  val ext: String
  if (lastDotIndex == -1) {
    rawPathWithoutExt = path
    ext = "svg"
  }
  else {
    rawPathWithoutExt = path.substring(0, lastDotIndex)
    ext = path.substring(lastDotIndex + 1)
  }

  for ((i, descriptor) in descriptors.withIndex()) {
    try {
      // check only for the first one, as io miss cache doesn't have a scale
      var image: Image?
      if (useCache) {
        image = loadByDescriptor(rawPathWithoutExt = rawPathWithoutExt,
                                 ext = ext,
                                 descriptor = descriptor,
                                 resourceClass = resourceClass,
                                 classLoader = classLoader,
                                 ioMissCache = if (i == 0) ImageCache.ioMissCache else null,
                                 imageCache = ImageCache,
                                 ioMissCacheKey = path,
                                 colorPatcherProvider = colorPatcherProvider)
      }
      else {
        if (i == 0 && ImageCache.ioMissCache.contains(path)) {
          return null
        }

        image = loadByDescriptorWithoutCache(rawPathWithoutExt = rawPathWithoutExt,
                                             ext = ext,
                                             descriptor = descriptor,
                                             resourceClass = resourceClass,
                                             classLoader = classLoader,
                                             colorPatcherProvider = colorPatcherProvider)
      }

      if (image != null) {
        if (start != -1L) {
          IconLoadMeasurer.addLoading(isSvg = descriptor.isSvg, start = start)
        }

        return convertImage(image = image,
                            filters = filters,
                            scaleContext = scaleContext,
                            isUpScaleNeeded = !descriptor.isSvg,
                            imageScale = descriptor.scale)
      }
    }
    catch (e: IOException) {
      LOG.debug(e)
    }
  }

  ImageCache.ioMissCache.add(path)
  return null
}

@Suppress("DuplicatedCode")
private fun loadByDescriptorWithoutCache(rawPathWithoutExt: String,
                                         ext: String,
                                         descriptor: ImageDescriptor,
                                         resourceClass: Class<*>?,
                                         classLoader: ClassLoader?,
                                         colorPatcherProvider: SVGLoader.SvgElementColorPatcherProvider?): Image? {
  val path = descriptor.pathTransform(rawPathWithoutExt, ext)
  @Suppress("DEPRECATION")
  return doLoadByDescriptor(path = path,
                            descriptor = descriptor,
                            resourceClass = resourceClass,
                            classLoader = classLoader,
                            colorPatcher = colorPatcherProvider?.attributeForPath(path),
                            deprecatedColorPatcher = colorPatcherProvider?.forPath(path))
}

internal fun loadByDescriptor(rawPathWithoutExt: String, ext: String, descriptor: ImageDescriptor): Image? {
  return loadByDescriptor(rawPathWithoutExt = rawPathWithoutExt,
                          ext = ext,
                          descriptor = descriptor,
                          resourceClass = null,
                          classLoader = null,
                          imageCache = ImageCache,
                          ioMissCache = null,
                          ioMissCacheKey = null,
                          colorPatcherProvider = null)
}

private fun loadByDescriptor(rawPathWithoutExt: String,
                             ext: String,
                             descriptor: ImageDescriptor,
                             resourceClass: Class<*>?,
                             classLoader: ClassLoader?,
                             ioMissCache: Set<String?>?,
                             imageCache: ImageCache,
                             ioMissCacheKey: String?,
                             colorPatcherProvider: SVGLoader.SvgElementColorPatcherProvider?): Image? {
  var tmpPatcher = false
  var digest: LongArray? = null

  val path = descriptor.pathTransform(rawPathWithoutExt, ext)

  var colorPatcher: SvgAttributePatcher? = null
  var deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher? = null
  if (colorPatcherProvider != null) {
    colorPatcher = colorPatcherProvider.attributeForPath(path)
    if (colorPatcher == null) {
      @Suppress("DEPRECATION")
      deprecatedColorPatcher = colorPatcherProvider.forPath(path)
      if (deprecatedColorPatcher != null) {
        digest = deprecatedColorPatcher.digest()?.let { longArrayOf(hasher.hashBytesToLong(it), seededHasher.hashBytesToLong(it)) }
        if (digest == null) {
          tmpPatcher = true
        }
      }
    }
    else {
      digest = colorPatcher.digest()
      if (digest == null) {
        tmpPatcher = true
      }
    }
  }

  if (digest == null) {
    digest = ArrayUtilRt.EMPTY_LONG_ARRAY!!
  }

  var cacheKey: CacheKey? = null
  if (!tmpPatcher) {
    cacheKey = CacheKey(path = path, scale = (if (descriptor.isSvg) descriptor.scale else 0f), digest = digest)
    imageCache.imageCache.getIfPresent(cacheKey)?.let {
      return it
    }
  }

  if (ioMissCache != null && ioMissCache.contains(ioMissCacheKey)) {
    return null
  }

  val image = doLoadByDescriptor(path = path,
                                 descriptor = descriptor,
                                 resourceClass = resourceClass,
                                 classLoader = classLoader,
                                 colorPatcher = colorPatcher,
                                 deprecatedColorPatcher = deprecatedColorPatcher) ?: return null
  if (cacheKey != null) {
    imageCache.imageCache.put(cacheKey, image)
  }
  return image
}

private fun doLoadByDescriptor(path: String,
                               descriptor: ImageDescriptor,
                               resourceClass: Class<*>?,
                               classLoader: ClassLoader?,
                               colorPatcher: SvgAttributePatcher?,
                               deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher?): BufferedImage? {
  var image: BufferedImage?
  val start = StartUpMeasurer.getCurrentTimeIfEnabled()
  if (resourceClass == null && (classLoader == null || URLUtil.containsScheme(path)) && !path.startsWith("file://")) {
    val connection = URL(path).openConnection()
    (connection as? HttpURLConnection)?.addRequestProperty("User-Agent", "IntelliJ")
    connection.getInputStream().use { stream ->
      image = if (descriptor.isSvg) {
        loadSvgAndCacheIfApplicable(path = path,
                                    scale = descriptor.scale,
                                    compoundCacheKey = descriptor.toSvgMapper(),
                                    colorPatcher = colorPatcher,
                                    deprecatedColorPatcher = deprecatedColorPatcher) { stream.readAllBytes() }
      }
      else {
        loadPng(stream = stream)
      }
    }
    if (start != -1L) {
      IconLoadMeasurer.loadFromUrl.end(start)
    }
  }
  else {
    image = if (descriptor.isSvg) {
      loadSvgAndCacheIfApplicable(path = path,
                                  scale = descriptor.scale,
                                  compoundCacheKey = descriptor.toSvgMapper(),
                                  colorPatcher = colorPatcher,
                                  deprecatedColorPatcher = deprecatedColorPatcher) {
        getResourceData(path = path, resourceClass = resourceClass, classLoader = classLoader)
      }
    }
    else {
      loadPngFromClassResource(path = path, classLoader = classLoader, resourceClass = resourceClass)
    }
    if (start != -1L) {
      IconLoadMeasurer.loadFromResources.end(start)
    }
  }
  return image
}

private class CacheKey(private val path: String, private val scale: Float, private val digest: LongArray) {
  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }
    val key = other as CacheKey
    return key.scale == scale && path == key.path && key.digest.contentEquals(digest)
  }

  override fun hashCode(): Int {
    var result = path.hashCode()
    result = 31 * result + java.lang.Float.floatToIntBits(scale)
    result = 31 * result + digest.contentHashCode()
    return result
  }
}