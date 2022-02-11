// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IconLoader.CachedImageIcon
import com.intellij.openapi.util.ImageDataByUrlLoader
import com.intellij.openapi.util.Pair
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.ui.icons.IconTransform
import com.intellij.ui.icons.ImageDataLoader
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ImageLoader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Image
import java.awt.image.ImageFilter
import java.net.MalformedURLException
import java.net.URL
import javax.swing.Icon

@Suppress("HardCodedStringLiteral")
@ApiStatus.Internal
class ImageDataByPathLoader private constructor(val path: String,
                                                val classLoader: ClassLoader,
                                                private val original: ImageDataByPathLoader?) : ImageDataLoader {
  companion object {
    // cache is not used - image data resolved using cache in any case
    @JvmStatic
    fun findIcon(@NonNls originalPath: String,
                 originalClassLoader: ClassLoader,
                 cache: MutableMap<Pair<String, ClassLoader>, CachedImageIcon>?): Icon? {
      val startTime = StartUpMeasurer.getCurrentTimeIfEnabled()

      @Suppress("NAME_SHADOWING")
      val originalPath = normalizePath(originalPath)
      val patched = IconLoader.patchPath(originalPath, originalClassLoader)
      val path = patched?.first ?: originalPath
      val classLoader = patched?.second ?: originalClassLoader
      val icon: Icon? = when {
        IconLoader.isReflectivePath(path) -> IconLoader.getReflectiveIcon(path, classLoader)
        cache == null -> createIcon(originalPath, originalClassLoader, patched, path, classLoader)
        else -> {
           cache.computeIfAbsent(Pair(originalPath, originalClassLoader)) {
            createIcon(it.first, it.second, patched, path, classLoader)
          }
        }
      }
      if (startTime != -1L) {
        IconLoadMeasurer.findIcon.end(startTime)
      }
      return icon
    }

    private fun createIcon(originalPath: @NonNls String,
                           originalClassLoader: ClassLoader,
                           patched: Pair<String, ClassLoader?>?,
                           path: String,
                           classLoader: ClassLoader): CachedImageIcon {
      val loader = ImageDataByPathLoader(originalPath, originalClassLoader, null)
      val resolver = if (patched == null) loader else ImageDataByPathLoader(path, classLoader, loader)
      return CachedImageIcon(null, resolver, null, null)
    }

    private fun normalizePath(patchedPath: String): String {
      return if (patchedPath[0] == '/') patchedPath.substring(1) else patchedPath
    }

    private fun doPatch(originalLoader: ImageDataByPathLoader,
                        transform: IconTransform,
                        isOriginal: Boolean): ImageDataLoader? {
      val patched = transform.patchPath(originalLoader.path, originalLoader.classLoader) ?: return if (isOriginal) null else originalLoader
      val classLoader = if (patched.second == null) originalLoader.classLoader else patched.second!!
      return if (patched.first.startsWith("file:/")) {
        try {
          ImageDataByUrlLoader(URL(patched.first), patched.first, classLoader, false)
        }
        catch (e: MalformedURLException) {
          throw RuntimeException(e)
        }
      }
      else {
        ImageDataByPathLoader(normalizePath(patched.first), classLoader, originalLoader)
      }
    }
  }

  override fun loadImage(filters: List<ImageFilter?>,
                         scaleContext: ScaleContext,
                         isDark: Boolean): Image? {
    var flags = ImageLoader.ALLOW_FLOAT_SCALING or ImageLoader.USE_CACHE
    if (isDark) {
      flags = flags or ImageLoader.USE_DARK
    }
    return ImageLoader.loadImage(path, filters, null, classLoader, flags, scaleContext, !path.endsWith(".svg"))
  }

  override fun getURL(): URL? {
    return classLoader.getResource(path)
  }

  override fun patch(originalPath: String, transform: IconTransform): ImageDataLoader? {
    val isOriginal = original == null
    return doPatch((if (isOriginal) this else original)!!, transform, isOriginal)
  }

  override fun isMyClassLoader(classLoader: ClassLoader): Boolean {
    return this.classLoader === classLoader || original != null && original.classLoader === classLoader
  }

  override fun toString(): String {
    return "ImageDataByPathLoader(classLoader=$classLoader, path=$path, original=$original)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ImageDataByPathLoader) return false

    if (path != other.path) return false
    if (classLoader != other.classLoader) return false
    if (original != other.original) return false

    return true
  }

  override fun hashCode(): Int {
    var result = path.hashCode()
    result = 31 * result + classLoader.hashCode()
    result = 31 * result + (original?.hashCode() ?: 0)
    return result
  }
}