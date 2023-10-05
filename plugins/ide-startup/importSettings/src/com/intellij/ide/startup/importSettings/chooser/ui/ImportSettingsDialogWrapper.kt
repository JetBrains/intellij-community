package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBDimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class ImportSettingsDialogWrapper private constructor() : DialogWrapper(null) {
  companion object{
    private var sdw: ImportSettingsDialogWrapper? = null

    fun doOk() {
      sdw?.doOk()
    }

    fun doAction(exitCode: Int) {
      sdw?.close(exitCode)
    }

    fun show(dialog: ProductProvider): DialogWrapper {
      var dw = sdw ?: ImportSettingsDialogWrapper()
      if(dw.isDisposed) {
        dw = ImportSettingsDialogWrapper()
        Disposer.register(dw.disposable) { sdw = null }
      }

      if(!dw.isShowing) {
        dw.isResizable = true
        dw.isModal = false
        dw.show()
      }

      dw.showDialog(dialog)
      SwingUtilities.invokeLater{
        dw.pack()
      }
      sdw = dw
      return dw
    }
  }

  init {
    init()
  }

  private lateinit var panel: JComponent

  private lateinit var southPanel: JComponent

  private var current: ProductProvider? = null

  fun doOk() {
    applyFields()
    close(OK_EXIT_CODE)
  }

  fun showDialog(dialog: ProductProvider) {
    val gbc = GridBagConstraints()
    gbc.gridx = 0
    gbc.gridy = 0
    gbc.weightx = 1.0
    gbc.weighty = 1.0
    gbc.fill = GridBagConstraints.BOTH

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

abstract class ProductProvider : DialogWrapper(null) {
  var content: JComponent? = null
  var southPanel: JComponent = JPanel()
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
