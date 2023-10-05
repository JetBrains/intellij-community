package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBDimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane

class MultiplePageDialog private constructor(): DialogWrapper(null) {
  companion object{
    fun show(page: PageProvider) {
      val dialog = MultiplePageDialog()
      dialog.showPage(page)
      dialog.isModal = false
      dialog.isResizable = false
      dialog.show()

      dialog.pack()
    }
  }
  init {
    init()
  }

  private lateinit var panel: JComponent

  private lateinit var southPanel: JComponent

  private var current: PageProvider? = null

  fun showPage(dialog: PageProvider) {
    val gbc = GridBagConstraints()
    gbc.gridx = 0
    gbc.gridy = 0
    gbc.weightx = 1.0
    gbc.weighty = 1.0
    gbc.fill = GridBagConstraints.BOTH

    dialog.parentDialog = this

    dialog.content?.let {
      panel.add(it, gbc)
    }

    southPanel.add(dialog.southPanel, gbc)

    current?.let {
      panel.remove(it.content)
      southPanel.remove(it.southPanel)
    }

    current = dialog
  }

  override fun createCenterPanel(): JComponent {
    panel = JPanel(GridBagLayout()).apply {
      preferredSize = JBDimension(640, 410)
    }
    return panel
  }

  override fun createSouthPanel(): JComponent {
    southPanel = JPanel(GridBagLayout()).apply {
      preferredSize = JBDimension(640, 57)
    }
    return southPanel
  }
}

abstract class PageProvider() : DialogWrapper(null) {
  var content: JComponent? = null
  var southPanel: JComponent = JPanel()
  var parentDialog: MultiplePageDialog? = null
    set(value) {
      field = value
      init()
    }

  override fun getRootPane(): JRootPane {
    return parentDialog?.rootPane ?: super.getRootPane()
  }

  override fun doOKAction() {
    super.doOKAction()
    parentDialog?.performOKAction()
  }

  fun doAction(exitCode: Int){
    close(exitCode)
  }

  override fun doCancelAction() {
    super.doCancelAction()
    parentDialog?.doCancelAction()
  }

  override fun show() {
    if(parentDialog != null) return
    init()
    super.show()
  }

  final override fun createCenterPanel(): JComponent? {
    content = createContent()
    return content
  }

  abstract fun createContent(): JComponent?

  final override fun createSouthPanel(): JComponent {
    southPanel = createCustomSouthPanel()
    return southPanel
  }

  open fun createCustomSouthPanel(): JComponent {
   return super.createSouthPanel()
  }
}
