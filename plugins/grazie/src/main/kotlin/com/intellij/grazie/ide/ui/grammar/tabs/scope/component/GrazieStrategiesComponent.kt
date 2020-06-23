// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar.tabs.scope.component

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.utils.configure
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CheckBoxListListener
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.containers.CollectionFactory

class GrazieStrategiesComponent : CheckBoxListListener, GrazieUIComponent {
  override val component by lazy { ScrollPaneFactory.createScrollPane(list) }

  private val list by lazy {
    CheckBoxList<GrammarCheckingStrategy>(this).configure {
      ListSpeedSearch(this) { it.text }
    }
  }

  private val myEnabledStrategyIDs = CollectionFactory.createSmallMemoryFootprintSet<String>()
  private val myDisabledStrategyIDs = CollectionFactory.createSmallMemoryFootprintSet<String>()

  override fun checkBoxSelectionChanged(index: Int, selected: Boolean) {
    val strategy = list.getItemAt(index) ?: return

    if (selected) {
      myEnabledStrategyIDs.add(strategy.getID())
      myDisabledStrategyIDs.remove(strategy.getID())
    }
    else {
      myEnabledStrategyIDs.remove(strategy.getID())
      myDisabledStrategyIDs.add(strategy.getID())
    }
  }

  override fun isModified(state: GrazieConfig.State) = state.enabledGrammarStrategies != myEnabledStrategyIDs ||
                                                       state.disabledGrammarStrategies != myDisabledStrategyIDs

  override fun reset(state: GrazieConfig.State) {
    val enabledStrategiesIDs = state.enabledGrammarStrategies
    val disabledStrategiesIDs = state.disabledGrammarStrategies

    myEnabledStrategyIDs.clear()
    myEnabledStrategyIDs.addAll(enabledStrategiesIDs)

    myDisabledStrategyIDs.clear()
    myDisabledStrategyIDs.addAll(disabledStrategiesIDs)

    val strategies = LanguageGrammarChecking.getLanguageExtensionPoints().map { it.instance }.sortedBy { it.getName() }

    list.clear()
    for (strategy in strategies) {
      val isEnabled = strategy.getID() in enabledStrategiesIDs || (strategy.isEnabledByDefault() && strategy.getID() !in disabledStrategiesIDs)
      list.addItem(strategy, strategy.getName(), isEnabled)
    }
  }

  override fun apply(state: GrazieConfig.State) = state.copy(
    enabledGrammarStrategies = myEnabledStrategyIDs,
    disabledGrammarStrategies = myDisabledStrategyIDs
  )

}
