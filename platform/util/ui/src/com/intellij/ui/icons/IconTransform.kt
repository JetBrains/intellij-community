// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.util.ArrayUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.image.ImageFilter

private val LOG = Logger.getInstance(IconTransform::class.java)

/**
 * Immutable representation of a global transformation applied to all icons
 */
@ApiStatus.Internal
class IconTransform
/**
 * Creates a new instance of IconTransform with the specified parameters.
 *
 * @param isDark             true if the icon should be transformed for dark mode, false otherwise
 * @param patchers         an array of IconPathPatcher objects used to modify the icon path before transforming it
 * @param postPatchers     an array of IconPathPatcher objects used to modify the icon path before transforming it.
 * During icon path patching patchers are iterated first, and if neither of them worked out, postPatchers are iterated.
 * @param filter           the ImageFilter to apply to the transformed icon, or null if no filter should be applied
 */ private constructor(@JvmField val isDark: Boolean,
                        private val patchers: Array<IconPathPatcher>,
                        private val postPatchers: Array<IconPathPatcher>,
                        @JvmField val filter: ImageFilter?) {
  constructor(dark: Boolean, patchers: Array<IconPathPatcher>, filter: ImageFilter?)
    : this(isDark = dark, patchers = patchers, postPatchers = emptyArray(), filter = filter)

  fun withPathPatcher(patcher: IconPathPatcher): IconTransform {
    return IconTransform(isDark = isDark, patchers = patchers + patcher, postPatchers = postPatchers, filter = filter)
  }

  @ApiStatus.Internal
  fun withPostPathPatcher(patcher: IconPathPatcher): IconTransform {
    return IconTransform(isDark, patchers, ArrayUtil.append(postPatchers, patcher), filter)
  }

  fun withoutPathPatcher(patcher: IconPathPatcher): IconTransform {
    val newPatchers = ArrayUtil.remove(patchers, patcher)
    var newLastPatchers = postPatchers
    if (newPatchers === patchers) {
      newLastPatchers = ArrayUtil.remove(postPatchers, patcher)
    }

    if (newPatchers === patchers && newLastPatchers === postPatchers) {
      return this
    }
    else {
      return IconTransform(isDark = isDark, patchers = newPatchers, postPatchers = postPatchers, filter = filter)
    }
  }

  fun withFilter(filter: ImageFilter): IconTransform {
    if (filter === this.filter) {
      return this
    }
    else {
      return IconTransform(isDark = isDark, patchers = patchers, postPatchers = postPatchers, filter = filter)
    }
  }

  fun withDark(dark: Boolean): IconTransform {
    if (dark == this.isDark) {
      return this
    }
    else {
      return IconTransform(isDark = dark, patchers = patchers, postPatchers = postPatchers, filter = filter)
    }
  }

  fun patchPath(path: String, classLoader: ClassLoader?): Pair<String, ClassLoader?>? {
    val prePatchResult = applyPatchers(path, classLoader, patchers)
    return if (prePatchResult == null) {
      applyPatchers(path, classLoader, postPatchers)
    }
    else {
      applyPatchers(prePatchResult.first, prePatchResult.second, postPatchers) ?: prePatchResult
    }
  }

  private fun applyPatchers(path: String, classLoader: ClassLoader?, patchers: Array<IconPathPatcher>): Pair<String, ClassLoader?>? {
    val pathWithLeadingSlash = if (path[0] == '/') path else "/$path"
    for (patcher in patchers) {
      var newPath: String?
      try {
        newPath = patcher.patchPath(pathWithLeadingSlash, classLoader)
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Exception) {
        LOG.error("$patcher cannot patch icon path", e)
        continue
      }

      if (newPath == null) {
        continue
      }

      LOG.debug {
        "replace '$path' with '$newPath'"
      }

      var contextClassLoader = patcher.getContextClassLoader(pathWithLeadingSlash, classLoader)
      if (contextClassLoader == null) {
        @Suppress("DEPRECATION")
        contextClassLoader = patcher.getContextClass(pathWithLeadingSlash)?.getClassLoader()
      }
      return Pair(newPath, contextClassLoader)
    }

    return null
  }

  fun copy(): IconTransform {
    return IconTransform(isDark = isDark, patchers = patchers, postPatchers = postPatchers, filter = filter)
  }
}
