// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.lang.IdeLanguageCustomization
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import org.jetbrains.annotations.ApiStatus

private val EP = LanguageExtension<FloatingToolbarCustomizer>("com.intellij.lang.floatingToolbarCustomizer")

internal fun findActionGroupFor(language: Language): String? {
  return EP.allForLanguage(language).firstNotNullOfOrNull { it.getActionGroup() }
}

/**
 * Extension point to configure and enable floating code toolbar for the specific language.
 *
 * @see CodeFloatingToolbar
 * @see [FloatingToolbarCustomizer.DefaultGroup]
 * @see [FloatingToolbarCustomizer.MinimalGroup]
 */
@ApiStatus.Experimental
interface FloatingToolbarCustomizer {
  /**
   * @return id of the action group to be shown in the toolbar, or null if the toolbar shouldn't be available
   */
  fun getActionGroup(): String?

  @ApiStatus.Experimental
  class DefaultGroup : FloatingToolbarCustomizer {
    override fun getActionGroup(): String {
      return "Floating.CodeToolbar"
    }
  }

  /**
   * Actions for languages that do not support refactoring capabilities such as Extract and Surround, e.g., JSON and XML.
   */
  @ApiStatus.Experimental
  open class MinimalGroup : FloatingToolbarCustomizer {
    override fun getActionGroup(): String? {
      val hasPrimaryToolbar = IdeLanguageCustomization.getInstance().primaryIdeLanguages
        .any { EP.allForLanguage(it).isNotEmpty() }

      // primary language does not support floating toolbar, ignore in this IDE
      if (!hasPrimaryToolbar) return null

      return "Floating.CodeToolbar.Minimal"
    }
  }
}