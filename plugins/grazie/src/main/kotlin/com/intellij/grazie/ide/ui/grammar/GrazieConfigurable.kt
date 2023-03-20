// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules.GrazieRulesTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JComponent

class GrazieConfigurable :
  ConfigurableBase<GrazieSettingsPanel, GrazieConfig>(
    "reference.settingsdialog.project.grazie", GraziePlugin.settingsPageName, "reference.settings.ide.settings.grammar"),
  WithEpDependencies,
  SearchableConfigurable
{
  private val ui: GrazieSettingsPanel by lazy { GrazieSettingsPanel() }

  override fun getSettings() = service<GrazieConfig>()

  override fun createUi(): GrazieSettingsPanel = ui

  override fun getPreferredFocusedComponent(): JComponent? {
    if (ui.component.selectedComponent == ui.rules.component) {
      return ui.rules.impl
    }

    return super<ConfigurableBase>.getPreferredFocusedComponent()
  }

  override fun enableSearch(option: String?): Runnable? {
    if (option != null) {
      return Runnable {
        ui.component.selectedComponent = ui.rules.component
        ui.rules.impl.filter(option)
      }
    }
    return null
  }

  internal fun selectRule(globalId: String) {
    ui.component.selectedComponent = ui.rules.component
    val tree = ui.rules.impl
    val ruleNode = (tree.model.root as GrazieRulesTreeNode).findRuleNode(globalId)
    if (ruleNode != null) {
      TreeUtil.selectNode(tree, ruleNode)
    }
  }

  // used in Grazie Pro
  @Suppress("unused", "SpellCheckingInspection")
  fun ruleEnablednessChanged(state: GrazieConfig.State) {
    val tree = ui.rules.impl
    (tree.model.root as GrazieRulesTreeNode).resetMark(tree.apply(state))
  }

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
    return setOf(LanguageGrammarChecking.EP_NAME,
                 ExtensionPointName("com.intellij.grazie.textExtractor"),
                 ExtensionPointName("com.intellij.grazie.textChecker"))
  }
}
