// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import org.jdom.Element
import org.jetbrains.annotations.Contract

/**
 * Provides methods to convert paths from absolute to portable form and vice versa.
 *
 * @see com.intellij.openapi.application.PathMacros
 */
interface PathMacroSubstitutor {

  /**
   * Convert a path to absolute by replacing all names of path variables by its values
   */
  @Contract("null -> null; !null -> !null")
  fun expandPath(text: String?): String?

  fun expandPathNonNull(text: String): String {
    return requireNotNull(expandPath(text)) { "expandPath must not return null if text is not null: $text" }
  }

  @Contract("null -> null; !null -> !null")
  fun collapsePath(text: String?): String? {
    return collapsePath(text, recursively = false)
  }

  fun collapsePathNonNull(text: String): String {
    return requireNotNull(collapsePath(text)) { "collapsePath must not return null if text is not null: $text" }
  }

  /**
   * Convert paths inside [text] to portable form by replacing all values of path variables by their names.
   * @param recursively if true all occurrences of paths inside [text] will be processed, otherwise [text] will be converted
   *                    only if its entire content is a path (or URL)
   */
  @Contract("null, _ -> null; !null, _ -> !null")
  fun collapsePath(text: String?, recursively: Boolean): String?

  /**
   * Process sub tags of [element] recursively and convert paths to absolute forms in all tag texts and attribute values.
   */
  fun expandPaths(element: Element)

  fun collapsePaths(element: Element) {
    collapsePaths(element, recursively = false)
  }

  fun collapsePathsRecursively(element: Element) {
    collapsePaths(element, recursively = true)
  }

  /**
   * Process sub tags of [element] recursively and convert paths to portable forms in all tag texts and attribute values.
   * @param recursively if true all occurrences of paths inside tag texts and attribute values will be processed, otherwise
   * they will be converted only if their entire content is a path (or URL)
   */
  fun collapsePaths(element: Element, recursively: Boolean)

  @Contract("null -> null; !null -> !null")
  fun collapsePathsRecursively(text: String?): String? {
    return collapsePath(text, recursively = true)
  }
}