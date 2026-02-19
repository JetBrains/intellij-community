// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.application.options

import com.intellij.application.options.PathMacroProtocolHolder.protocols
import com.intellij.openapi.application.PathMacroFilter
import com.intellij.openapi.components.CompositePathMacroFilter
import com.intellij.openapi.components.PathMacroMap
import com.intellij.openapi.extensions.ExtensionPointName
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.util.regex.Pattern

class PathMacrosCollector private constructor() : PathMacroMap() {
  private val matcher = MACRO_PATTERN.matcher("")
  private val macroMap = LinkedHashMap<String, String?>()

  companion object {
    @JvmField
    val MACRO_FILTER_EXTENSION_POINT_NAME: ExtensionPointName<PathMacroFilter> =
      ExtensionPointName<PathMacroFilter>("com.intellij.pathMacroFilter")

    @JvmField
    val MACRO_PATTERN: Pattern = Pattern.compile("\\$([\\w\\-.]+?)\\$")

    fun getMacroNames(element: Element): Set<String> {
      return getMacroNames(root = element, filter = CompositePathMacroFilter(MACRO_FILTER_EXTENSION_POINT_NAME.extensionList))
    }

    @ApiStatus.Internal
    fun getMacroNames(
      root: Element,
      filter: PathMacroFilter?,
      pathMacrosGetter: () -> PathMacrosImpl = { PathMacrosImpl.getInstanceEx() },
    ): Set<String> {
      val collector = PathMacrosCollector()
      collector.substitute(root, true, false, filter)
      val preResult = collector.macroMap.keys
      if (preResult.isEmpty()) {
        return emptySet()
      }

      val pathMacros = pathMacrosGetter()
      val result = HashSet<String>(preResult)
      result.removeAll(pathMacros.getSystemMacroNames())
      @Suppress("ConvertArgumentToSet")
      result.removeAll(pathMacros.getLegacyMacroNames())
      pathMacros.removeToolMacroNames(result)
      for (string in pathMacros.getIgnoredMacroNames()) {
        result.remove(string)
      }
      return result
    }
  }

  override fun substituteRecursively(text: String, caseSensitive: Boolean): CharSequence {
    if (text.isEmpty()) {
      return text
    }

    matcher.reset(text)
    while (matcher.find()) {
      macroMap.put(matcher.group(1), null)
    }

    return text
  }

  override fun substitute(text: String, caseSensitive: Boolean): String {
    if (text.isEmpty()) {
      return text
    }

    var startPos = -1
    if (text.get(0) == '$') {
      startPos = 0
    }
    else {
      for (protocol in protocols) {
        if (text.length > protocol.length + 4 && text.startsWith(protocol) && text.get(protocol.length) == ':') {
          startPos = protocol.length + 1
          if (text.get(startPos) == '/') {
            startPos++
          }
          if (text.get(startPos) == '/') {
            startPos++
          }
        }
      }
    }
    if (startPos < 0) {
      return text
    }

    matcher.reset(text).region(startPos, text.length)
    if (matcher.lookingAt()) {
      macroMap.put(matcher.group(1), null)
    }

    return text
  }

  override fun hashCode(): Int = macroMap.hashCode()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PathMacrosCollector) return false

    if (macroMap != other.macroMap) return false

    return true
  }
}
