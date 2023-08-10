// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.scale.ScaleContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.awt.Image
import java.lang.invoke.MethodHandles
import java.net.URL
import java.util.function.Supplier
import javax.swing.Icon

@Internal
class ImageDataByPathLoader private constructor(private val path: String,
                                                private val classLoader: ClassLoader,
                                                private val original: ImageDataByPathLoader?) : ImageDataLoader {
  companion object {
    fun findIconByPath(@NonNls path: String,
                       classLoader: ClassLoader,
                       cache: MutableMap<Pair<String, ClassLoader?>, CachedImageIcon>?,
                       toolTip: Supplier<String?>? = null): Icon? {
      val startTime = StartUpMeasurer.getCurrentTimeIfEnabled()

      val originalPath = normalizePath(path)
      val patched = patchIconPath(originalPath, classLoader)
      val effectivePath = patched?.first ?: originalPath
      val effectiveClassLoader = patched?.second ?: classLoader
      val icon: Icon? = when {
        isReflectivePath(effectivePath) -> getReflectiveIcon(effectivePath, effectiveClassLoader)
        cache == null -> createIcon(originalPath = originalPath,
                                    originalClassLoader = effectiveClassLoader,
                                    patched = patched,
                                    path = effectivePath,
                                    classLoader = effectiveClassLoader,
                                    toolTip = toolTip)
        else -> {
           cache.computeIfAbsent(Pair(originalPath, effectiveClassLoader)) {
            createIcon(originalPath = it.first,
                       originalClassLoader = it.second!!,
                       patched = patched,
                       path = effectivePath,
                       classLoader = effectiveClassLoader,
                       toolTip = toolTip)
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
                           classLoader: ClassLoader,
                           toolTip: Supplier<String?>? = null): CachedImageIcon {
      val loader = ImageDataByPathLoader(path = originalPath, classLoader = originalClassLoader, original = null)
      val resolver = if (patched == null) loader else ImageDataByPathLoader(path = path, classLoader = classLoader, original = loader)
      return CachedImageIcon(originalPath = null, resolver = resolver, toolTip = toolTip)
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
        ImageDataByFilePathLoader(patched.first)
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

private val LOOKUP = MethodHandles.lookup()

@Internal
fun isReflectivePath(path: String): Boolean {
  return !path.startsWith('/') && path.contains("Icons.") && !path.endsWith(".svg")
}

@Internal
fun getReflectiveIcon(path: String, classLoader: ClassLoader): Icon? {
  try {
    var dotIndex = path.lastIndexOf('.')
    val fieldName = path.substring(dotIndex + 1)
    val builder = StringBuilder(path.length + 20)
    builder.append(path, 0, dotIndex)
    var separatorIndex = -1
    do {
      dotIndex = path.lastIndexOf('.', dotIndex - 1)
      // if starts with a lower case, char - it is a package name
      if (dotIndex == -1 || Character.isLowerCase(path[dotIndex + 1])) {
        break
      }
      if (separatorIndex != -1) {
        builder.setCharAt(separatorIndex, '$')
      }
      separatorIndex = dotIndex
    }
    while (true)
    if (!builder[0].isLowerCase()) {
      if (separatorIndex != -1) {
        builder.setCharAt(separatorIndex, '$')
      }
      builder.insert(0, if (path.startsWith("AllIcons.")) "com.intellij.icons." else "icons.")
    }
    val aClass = classLoader.loadClass(builder.toString())
    return LOOKUP.findStaticGetter(aClass, fieldName, Icon::class.java).invoke() as Icon
  }
  catch (e: Throwable) {
    logger<CachedImageIcon>().warn("Cannot get reflective icon (path=$path)", e)
    return null
  }
}