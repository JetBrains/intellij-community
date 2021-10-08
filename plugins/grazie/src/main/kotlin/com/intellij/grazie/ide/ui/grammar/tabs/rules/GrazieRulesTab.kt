package com.intellij.grazie.ide.ui.grammar.tabs.rules

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.msg.GrazieInitializerManager
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.GrazieDescriptionComponent
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.GrazieTreeComponent
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.allRules
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.ui.layout.migLayout.*
import com.intellij.util.ui.JBUI
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout

internal class GrazieRulesTab : GrazieUIComponent.Delegating, Disposable {
  private val description = GrazieDescriptionComponent()

  override val impl = GrazieTreeComponent(description.listener)

  override val component = panel(MigLayout(createLayoutConstraints(), AC().grow(), AC().grow())) {
    border = JBUI.Borders.empty()
    add(impl.component, CC().grow().width("45%").minWidth("250px"))
    add(description.component, CC().grow().width("55%"))

    impl.reset(GrazieConfig.get())
  }

  // update the tree on language list change in Natural Languages configurable
  private val connection = service<GrazieInitializerManager>().register(object : GrazieStateLifecycle {
    override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
      if (prevState.enabledLanguages != newState.enabledLanguages) {
        impl.resetTreeModel(allRules(newState))
      }
    }
  })

  override fun dispose() {
    Disposer.dispose(impl)
    Disposer.dispose(connection)
  }
}
