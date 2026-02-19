/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui.accessibility

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext

object AccessibleContextUtil {
  @ApiStatus.Internal
  const val PUNCTUATION_CHARACTER: String = "."

  @ApiStatus.Internal
  const val PUNCTUATION_SEPARATOR: String = "  "

  @JvmStatic
  fun setName(component: Component, @Nls name: String?) {
    setAccessibleName(component, name)
  }

  @JvmStatic
  fun setName(component: Component, source: Component) {
    setName(component, getAccessibleName(source))
  }

  @JvmStatic
  fun setCombinedName(component: Component, j1: Component?, @NlsSafe separator: String, j2: Component?) {
    setAccessibleName(component, combineAccessibleStrings(
      getAccessibleName(j1),
      separator,
      getAccessibleName(j2)))
  }

  @JvmStatic
  fun setCombinedName(
    component: Component,
    j1: Component?, @NlsSafe separator1: String,
    j2: Component?, @NlsSafe separator2: String, j3: Component?,
  ) {
    setAccessibleName(component, combineAccessibleStrings(
      getAccessibleName(j1),
      separator1,
      getAccessibleName(j2),
      separator2,
      getAccessibleName(j3)))
  }

  @JvmStatic
  @Nls
  fun getCombinedName(j1: Component?, @NlsSafe separator: String, j2: Component?): String? {
    return combineAccessibleStrings(getAccessibleName(j1), separator, getAccessibleName(j2))
  }

  @JvmStatic
  @Nls
  fun getCombinedName(
    j1: Component?, @NlsSafe separator1: String,
    j2: Component?, @NlsSafe separator2: String, j3: Component?,
  ): String? {
    return combineAccessibleStrings(getAccessibleName(j1), separator1, getAccessibleName(j2), separator2, getAccessibleName(j3))
  }

  @JvmStatic
  @Nls
  fun getCombinedName(@NlsSafe separator: String, vararg components: Component?): String? {
    var result: String? = ""
    for (c in components) {
      result = combineAccessibleStrings(result, separator, getAccessibleName(c))
    }
    return result
  }

  @JvmStatic
  fun setDescription(component: Component, source: Component) {
    setAccessibleDescription(component, getAccessibleDescription(source))
  }

  @JvmStatic
  fun setDescription(component: Component, @Nls description: String?) {
    setAccessibleDescription(component, description)
  }

  @JvmStatic
  fun setCombinedDescription(
    component: Component, j1: Component?,
    @NlsSafe separator: String, j2: Component?,
  ) {
    setAccessibleDescription(component, combineAccessibleStrings(getAccessibleDescription(j1), separator, getAccessibleDescription(j2)))
  }

  @JvmStatic
  fun setCombinedDescription(
    component: Component, j1: Component?, @NlsSafe separator1: String,
    j2: Component?, @NlsSafe separator2: String, j3: Component?,
  ) {
    setAccessibleDescription(component,
                             combineAccessibleStrings(
                               getAccessibleDescription(j1),
                               separator1,
                               getAccessibleDescription(j2),
                               separator2,
                               getAccessibleDescription(j3)))
  }

  @JvmStatic
  fun getCombinedDescription(j1: Component?, @NlsSafe separator: String, j2: Component?): String? {
    return combineAccessibleStrings(getAccessibleDescription(j1), separator, getAccessibleDescription(j2))
  }

  @JvmStatic
  fun getCombinedDescription(
    j1: Component?, @NlsSafe separator1: String,
    j2: Component?, @NlsSafe separator2: String, j3: Component?,
  ): String? {
    return combineAccessibleStrings(getAccessibleDescription(j1),
                                    separator1,
                                    getAccessibleDescription(j2),
                                    separator2,
                                    getAccessibleDescription(j3))
  }

  /**
   * Returns `description` if it is different from the accessible
   * name, `null` otherwise.
   *
   * Calling this method is useful from custom implementations of
   * [@getAccessibleDescription][AccessibleContext] to ensure screen
   * readers don't announce the same text twice (name and description) when
   * a component receives the focus.
   */
  @JvmStatic
  @Nls
  fun getUniqueDescription(context: AccessibleContext, @Nls description: String?): String? {
    return if (description == context.accessibleName) null else description
  }

  @JvmStatic
  fun setParent(component: Component, newParent: Component?) {
    component.accessibleContext.accessibleParent = newParent as? Accessible
  }

  @JvmStatic
  @Nls
  fun combineAccessibleStrings(@Nls s1: String?, @Nls s2: String?): String? {
    return combineAccessibleStrings(s1, " ", s2)
  }

  @JvmStatic
  @Nls
  fun combineAccessibleStrings(@Nls s1: String?, @NlsSafe separator: String, @Nls s2: String?): String? {
    return when {
      s1.isNullOrEmpty() && s2.isNullOrEmpty() -> null
      s1.isNullOrEmpty() -> s2
      s2.isNullOrEmpty() -> s1
      else -> s1 + separator + s2
    }
  }

  @JvmStatic
  @Nls
  fun combineAccessibleStrings(
    @Nls s1: String?, @Nls separator1: String, @Nls s2: String?,
    @Nls separator2: String, @Nls s3: String?,
  ): String? {
    return combineAccessibleStrings(combineAccessibleStrings(s1, separator1, s2), separator2, s3)
  }

  @JvmStatic
  @Nls
  fun joinAccessibleStrings(@NlsSafe separator: String, @Nls vararg strings: String?): String? {
    var result: String? = null
    for (s in strings) {
      result = combineAccessibleStrings(result, separator, s)
    }
    return result
  }

  /**
   * Given a multi-line string, return a single line string where new line separators
   * are replaced with a punctuation character. This is useful for returning text to
   * screen readers, as they tend to ignore new line separators during speech, but
   * they do pause at punctuation characters.
   */
  @JvmStatic
  fun replaceLineSeparatorsWithPunctuation(text: String?): String {
    if (text.isNullOrEmpty()) return ""

    return StringUtil.splitByLines(text)
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .joinToString(PUNCTUATION_SEPARATOR) { line ->
        if (line.endsWith(PUNCTUATION_CHARACTER)) line else line + PUNCTUATION_CHARACTER
      }
  }

  @Nls
  private fun getAccessibleName(component: Component?): String? {
    return (component as? Accessible)?.accessibleContext?.accessibleName
  }

  private fun setAccessibleName(component: Component, @Nls name: String?) {
    (component as? Accessible)?.accessibleContext?.accessibleName = name
  }

  @Nls
  private fun getAccessibleDescription(component: Component?): String? {
    return (component as? Accessible)?.accessibleContext?.accessibleDescription
  }

  private fun setAccessibleDescription(component: Component, @Nls description: String?) {
    (component as? Accessible)?.accessibleContext?.accessibleDescription = description
  }
}
