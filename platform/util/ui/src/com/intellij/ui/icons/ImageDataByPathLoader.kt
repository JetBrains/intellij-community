// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.loadImage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Image
import java.net.URL
import javax.swing.Icon

@ApiStatus.Internal
class ImageDataByPathLoader private constructor(private val path: String,
                                                private val classLoader: ClassLoader,
                                                private val original: ImageDataByPathLoader?) : ImageDataLoader {
  companion object {
    // cache is not used - image data resolved using cache in any case
    fun findIcon(@NonNls originalPath: String,
                 originalClassLoader: ClassLoader,
                 cache: MutableMap<Pair<String, ClassLoader?>, CachedImageIcon>?): Icon? {
      val startTime = StartUpMeasurer.getCurrentTimeIfEnabled()

      @Suppress("NAME_SHADOWING")
      val originalPath = normalizePath(originalPath)
      val patched = IconLoader.patchPath(originalPath, originalClassLoader)
      val path = patched?.first ?: originalPath
      val classLoader = patched?.second ?: originalClassLoader
      val icon: Icon? = when {
        IconLoader.isReflectivePath(path) -> IconLoader.getReflectiveIcon(path, classLoader)
        cache == null -> createIcon(originalPath = originalPath,
                                    originalClassLoader = originalClassLoader,
                                    patched = patched,
                                    path = path,
                                    classLoader = classLoader)
        else -> {
           cache.computeIfAbsent(Pair(originalPath, originalClassLoader)) {
            createIcon(originalPath = it.first,
                       originalClassLoader = it.second!!,
                       patched = patched,
                       path = path,
                       classLoader = classLoader)
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
      return CachedImageIcon(originalPath = null, resolver = resolver)
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
        ImageDataByUrlLoader(url = URL(patched.first), path = patched.first, classLoader = classLoader)
      }
      else {
        ImageDataByPathLoader(path = normalizePath(patched.first), classLoader = classLoader, original = originalLoader)
      }
    }
  }

  override fun getCoords(): Pair<String, ClassLoader> = path to classLoader

  override fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): Image? {
    return loadImage(path = path,
                     isDark = parameters.isDark,
                     filters = parameters.filters,
                     colorPatcherProvider = parameters.colorPatcher,
                     scaleContext = scaleContext,
                     classLoader = classLoader,
                     // CachedImageIcon instance cache the resolved image
                     useCache = false)
  }

  override val url: URL?
    get() = classLoader.getResource(path)

  override fun patch(originalPath: String, transform: IconTransform): ImageDataLoader? {
    val isOriginal = original == null
    return doPatch(originalLoader = (if (isOriginal) this else original)!!, transform = transform, isOriginal = isOriginal)
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
    return original == other.original
  }

  override fun hashCode(): Int {
    var result = path.hashCode()
    result = 31 * result + classLoader.hashCode()
    result = 31 * result + (original?.hashCode() ?: 0)
    return result
  }
}