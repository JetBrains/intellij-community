package com.intellij.grazie.ide.ui.grammar.tabs.exceptions

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.grammar.tabs.exceptions.component.GrazieExceptionsListComponent
import com.intellij.ui.layout.migLayout.*
import com.intellij.util.ui.JBUI
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout

class GrazieExceptionsTab : GrazieUIComponent.Delegating {
  override val impl = GrazieExceptionsListComponent(GrazieConfig.get().suppressingContext.suppressed.toList())

  override val component = panel(MigLayout(createLayoutConstraints(), AC().grow(), AC().grow())) {
    border = JBUI.Borders.empty()
    add(impl, CC().height("50%").alignY("top").growX())
  }
}
