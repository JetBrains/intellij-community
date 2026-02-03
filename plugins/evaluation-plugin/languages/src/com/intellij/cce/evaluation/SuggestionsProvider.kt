// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.core.Lookup
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface SuggestionsProvider {
  val name: String
  fun getSuggestions(expectedText: String, editor: Editor, language: Language,
                     comparator: (String, String) -> Boolean): Lookup

  fun getSuggestions(expectedText: String, editor: Editor, language: Language,
                     comparator: (String, String) -> Boolean, collectContextOnly: Boolean?): Lookup {
    return getSuggestions(expectedText, editor, language, comparator)
  }
  companion object {
    private val EP_NAME = ExtensionPointName.create<SuggestionsProvider>("com.intellij.cce.suggestionsProvider")

    fun find(project: Project, name: String): SuggestionsProvider? {
      return EP_NAME.getExtensionList(project).singleOrNull { it.name == name }
    }
  }
}

interface ContextAwareSuggestionsProvider