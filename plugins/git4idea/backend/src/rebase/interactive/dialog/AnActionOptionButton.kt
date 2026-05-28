// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.interactive.dialog

import com.intellij.ide.DataManager
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonPainter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.ui.components.JBOptionButton
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent

internal fun JButton.adjustForToolbar() {
  val buttonHeight = JBUI.scale(28)
  preferredSize = Dimension(preferredSize.width, buttonHeight)
  border = object : DarculaButtonPainter() {
    override fun getBorderInsets(c: Component?): Insets {
      return JBInsets.emptyInsets()
    }
  }
  isFocusable = false
}

internal fun JButton.withLeftToolbarBorder() = BorderLayoutPanel().addToCenter(this).apply {
  border = JBUI.Borders.emptyLeft(6)
}

internal class AnActionOptionButton(
  action: AnAction,
  val options: List<AnAction>
) : AnActionWrapper(action), CustomComponentAction {

  override fun actionPerformed(e: AnActionEvent) {
    throw UnsupportedOperationException()
  }

  override fun createCustomComponent(presentation: Presentation, place: String) = JBOptionButton(null, null).apply {
    action = AnActionWrapper(delegate, this)
    setOptions(this@AnActionOptionButton.options)
    adjustForToolbar()
    mnemonic = delegate.templatePresentation.text.first().code
  }.withLeftToolbarBorder()

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    UIUtil.setEnabled(component, presentation.isEnabled, true)
  }

  private class AnActionWrapper(
    private val action: AnAction,
    private val component: JComponent
  ) : AbstractAction(action.templatePresentation.text) {
    override fun actionPerformed(e: ActionEvent?) {
      val context = DataManager.getInstance().getDataContext(component)
      val event = AnActionEvent.createFromAnAction(action, null, GitInteractiveRebaseDialog.PLACE, context)
      ActionUtil.performAction(action, event)
    }
  }
}