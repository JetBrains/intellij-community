// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.settings

import com.intellij.grazie.ide.ui.components.rules.GrazieRulesPanel
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.Disposable

class GrazieRulesComponent(onSelectionChanged: (Any) -> Unit) : Disposable {
  private val rules = GrazieRulesPanel(onSelectionChanged)

  val component = rules.panel
  val state
    get() = rules.state()
  val isModified
    get() = rules.isModified

  fun reset() = rules.reset()
  fun filter(str: String?) = rules.filter(str ?: "")
  fun addLang(lang: Lang) = rules.addLang(lang)
  fun removeLang(lang: Lang) = rules.removeLang(lang)

  override fun dispose() = rules.dispose()
}
