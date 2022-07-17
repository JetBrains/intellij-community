// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.IdeBorderFactory.createTitledBorder
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.removeMnemonic
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import javax.swing.Box
import javax.swing.JPanel
import kotlin.collections.set

class CommitOptionsPanel(private val actionNameSupplier: () -> String) : BorderLayoutPanel(), CommitOptionsUi {
  private val perVcsOptionsPanels = mutableMapOf<AbstractVcs, JPanel>()
  private val vcsOptionsPanel = verticalPanel()
  private val beforeOptionsPanel = simplePanel()
  private val afterOptionsPanel = simplePanel()

  private val actionName get() = removeMnemonic(actionNameSupplier())

  private val options = MutableCommitOptions()
  private val vcsOptions get() = options.vcsOptions
  private val beforeOptions get() = options.beforeOptions
  private val afterOptions get() = options.afterOptions

  val isEmpty: Boolean get() = options.isEmpty

  init {
    buildLayout()
  }

  private fun buildLayout() {
    val optionsBox = Box.createVerticalBox().apply {
      add(vcsOptionsPanel)
      add(beforeOptionsPanel)
      add(afterOptionsPanel)
    }
    val optionsPane = createScrollPane(simplePanel().addToTop(optionsBox), true)
    addToCenter(optionsPane)
  }

  override fun setOptions(options: CommitOptions) {
    setVcsOptions(options.vcsOptions)
    setBeforeOptions(options.beforeOptions)
    setAfterOptions(options.afterOptions)
  }

  override fun setVisible(vcses: Collection<AbstractVcs>) =
    perVcsOptionsPanels.forEach { (vcs, vcsPanel) -> vcsPanel.isVisible = vcs in vcses }

  private fun setVcsOptions(newOptions: Map<AbstractVcs, RefreshableOnComponent>) {
    if (vcsOptions != newOptions) {
      vcsOptions.clear()
      perVcsOptionsPanels.clear()
      vcsOptionsPanel.removeAll()

      vcsOptions += newOptions
      vcsOptions.forEach { (vcs, options) ->
        val panel = verticalPanel(vcs.displayName).apply { add(options.component) }
        vcsOptionsPanel.add(panel)
        perVcsOptionsPanels[vcs] = panel
      }
    }
  }

  private fun setBeforeOptions(newOptions: List<RefreshableOnComponent>) {
    if (beforeOptions != newOptions) {
      beforeOptions.clear()
      beforeOptionsPanel.removeAll()

      beforeOptions += newOptions
      if (beforeOptions.isNotEmpty()) {
        val panel = verticalPanel(message("border.standard.checkin.options.group", actionName))
        beforeOptions.forEach { panel.add(it.component) }
        beforeOptionsPanel.add(panel)
      }
    }
  }

  private fun setAfterOptions(newOptions: List<RefreshableOnComponent>) {
    if (afterOptions != newOptions) {
      afterOptions.clear()
      afterOptionsPanel.removeAll()

      afterOptions += newOptions
      if (afterOptions.isNotEmpty()) {
        val panel = verticalPanel(message("border.standard.after.checkin.options.group", actionName))
        afterOptions.forEach { panel.add(it.component) }
        afterOptionsPanel.add(panel)
      }
    }
  }

  companion object {
    fun verticalPanel() = JPanel(VerticalFlowLayout(0, 0))

    fun verticalPanel(title: @Nls String) = JPanel(VerticalFlowLayout(0, 5)).apply {
      border = createTitledBorder(title)
    }
  }
}
