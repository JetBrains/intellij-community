// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.grammar.tabs.exceptions.GrazieExceptionsTab
import com.intellij.grazie.ide.ui.grammar.tabs.rules.GrazieRulesTab
import com.intellij.grazie.ide.ui.grammar.tabs.scope.GrazieScopeTab
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

internal class GrazieSettingsPanel : ConfigurableUi<GrazieConfig>, Disposable {
  private val scope = GrazieScopeTab()
  internal val rules = GrazieRulesTab()
  private val exceptions = GrazieExceptionsTab()

  override fun isModified(settings: GrazieConfig): Boolean = rules.isModified(settings.state) ||
                                                             scope.isModified(settings.state) ||
                                                             exceptions.isModified(settings.state)

  override fun apply(settings: GrazieConfig) {
    GrazieConfig.update { state ->
      exceptions.apply(scope.apply(rules.apply(state)))
    }

    rules.reset(settings.state)
  }

  override fun reset(settings: GrazieConfig) {
    rules.reset(settings.state)
    scope.reset(settings.state)
    exceptions.reset(settings.state)
  }

  internal val component: JBTabbedPane = JBTabbedPane().apply {
    this.tabComponentInsets = JBUI.insetsTop(8)
    add(msg("grazie.settings.grammar.tabs.scope"), scope.component)
    add(msg("grazie.settings.grammar.tabs.rules"), rules.component)
    add(msg("grazie.settings.grammar.tabs.exceptions"), exceptions.component)
  }

  override fun getComponent(): JComponent = component

  override fun dispose() = rules.dispose()

}
