// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.settings

import com.intellij.lang.Language
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CheckBoxListListener
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.ScrollPaneFactory
import javax.swing.JCheckBox

class GrazieProgrammingLanguagesComponent : CheckBoxListListener {
  val enabledProgrammingLanguagesIDs = HashSet<String>()
  val list = CheckBoxList<Language>(this).apply {
    ListSpeedSearch(this) { box: JCheckBox -> box.text }
  }

  override fun checkBoxSelectionChanged(index: Int, selected: Boolean) {
    val lang = list.getItemAt(index) ?: return

    if (selected) {
      enabledProgrammingLanguagesIDs.add(lang.id)
    }
    else {
      enabledProgrammingLanguagesIDs.remove(lang.id)
    }
  }

  fun reset(langsIDs: Set<String>) {
    enabledProgrammingLanguagesIDs.clear()
    enabledProgrammingLanguagesIDs.addAll(langsIDs)

    list.clear()
    for (lang in Language.getRegisteredLanguages().filter { it != Language.ANY }.sortedWith(compareBy<Language> ({it.id !in langsIDs}, { it.displayName }))) {
      list.addItem(lang, lang.displayName, lang.id in langsIDs)
    }
  }

  val component = ScrollPaneFactory.createScrollPane(list)
}
