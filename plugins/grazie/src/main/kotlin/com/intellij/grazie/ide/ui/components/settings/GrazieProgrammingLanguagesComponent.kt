// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.settings

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CheckBoxListListener
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.ScrollPaneFactory
import javax.swing.JCheckBox

class GrazieProgrammingLanguagesComponent : CheckBoxListListener {
  val enabledStrategiesIDs = HashSet<String>()
  val disabledStrategiesIDs = HashSet<String>()
  val list = CheckBoxList<GrammarCheckingStrategy>(this).apply {
    ListSpeedSearch(this) { box: JCheckBox -> box.text }
  }

  override fun checkBoxSelectionChanged(index: Int, selected: Boolean) {
    val strategy = list.getItemAt(index) ?: return

    if (selected) {
      enabledStrategiesIDs.add(strategy.getID())
      disabledStrategiesIDs.remove(strategy.getID())
    }
    else {
      enabledStrategiesIDs.remove(strategy.getID())
      disabledStrategiesIDs.add(strategy.getID())
    }
  }

  fun reset(enabledStrategiesIDs: Set<String>, disabledStrategiesIDs: Set<String>) {
    this.enabledStrategiesIDs.clear()
    this.enabledStrategiesIDs.addAll(enabledStrategiesIDs)

    this.disabledStrategiesIDs.clear()
    this.disabledStrategiesIDs.addAll(disabledStrategiesIDs)

    val strategies = LanguageGrammarChecking.getLanguageExtensionPoints().map { it.instance }.sortedBy { it.getName() }

    list.clear()
    for (strategy in strategies) {
      if (strategy.isEnabledByDefault()) {
        list.addItem(strategy, strategy.getName(), strategy.getID() !in disabledStrategiesIDs)
      } else {
        list.addItem(strategy, strategy.getName(), strategy.getID() in enabledStrategiesIDs)
      }
    }
  }

  val component = ScrollPaneFactory.createScrollPane(list)
}
