// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.SwingUtilities

/** TODO
 * should be replaced by card layout
 */

class MultiplePageDialog private constructor() : DialogWrapper(null) {
  companion object {
    fun show(page: PageProvider,
             callback: () -> Unit = {},
             isModal: Boolean = true,
             @NlsContexts.DialogTitle title: String? = null) {
      createDialog(page).apply {
        this.title = title
        this.isModal = isModal
        this.callback = callback

        show()
        pack()
      }
    }

    private fun createDialog(page: PageProvider): MultiplePageDialog {
      val dialog = MultiplePageDialog()
      dialog.showPage(page)
      dialog.isResizable = false
      return dialog
    }
  }

  private lateinit var callback: () -> Unit

  init {
    Disposer.register(disposable) {
      current?.close(OK_EXIT_CODE)
    }

    init()

  }


  override fun getStyle(): DialogStyle {
    return DialogStyle.COMPACT
  }

  private lateinit var panel: JComponent

  private lateinit var southPanel: JComponent

  private var current: PageProvider? = null

  override fun doCancelAction() {
    current?.let {
      val shouldExit = it.showExit()?.ask(peer.contentPane)

      if (shouldExit != false) {
        super.doCancelAction()
        callback()
      }

    } ?: run {
      super.doCancelAction()
    }
  }


  fun showPage(dialog: PageProvider) {
    val gbc = GridBagConstraints()
    gbc.gridx = 0
    gbc.gridy = 0
    gbc.weightx = 1.0
    gbc.weighty = 1.0
    gbc.fill = GridBagConstraints.BOTH

    dialog.parentDialog = this

    current?.let {
      panel.remove(it.content)
      southPanel.remove(it.southPanel)
      it.tracker.onLeave()
    }

    dialog.content?.let {
      dialog.tracker.onEnter()
      panel.add(it, gbc)
    }

    if(dialog.createSouth) {
      southPanel.add(dialog.southPanel, gbc)
    }

    current = dialog

    SwingUtilities.invokeLater {
      pack()
    }
  }

  override fun createCenterPanel(): JComponent {
    panel = JPanel(GridBagLayout()).apply {
      border = JBUI.Borders.empty()
    }
    return panel
  }

  override fun createSouthPanel(): JComponent {
    southPanel = JPanel(GridBagLayout()).apply {
      border = JBUI.Borders.empty()
    }
    return southPanel
  }
}

abstract class PageProvider(val createSouth: Boolean = true) : DialogWrapper(null, null, true, IdeModalityType.IDE, createSouth) {
  var content: JComponent? = null
  var southPanel: JComponent = JPanel()
  open var confirmationNeeded = true

  private var inited = false
  var parentDialog: MultiplePageDialog? = null
    set(value) {
      if (field == value) return
      field = value

      if(!inited) {
        init()
        inited = true
      }
    }

  init {
    Disposer.register(disposable) {
      parentDialog = null
    }
  }

  open fun showExit(): MessageDialogBuilder.YesNo? = null

  override fun getStyle(): DialogStyle {
    return DialogStyle.COMPACT
  }

  override fun getRootPane(): JRootPane {
    return parentDialog?.rootPane ?: super.getRootPane()
  }

  override fun doOKAction() {
    super.doOKAction()
    parentDialog?.performOKAction()
  }

  private fun doAction(exitCode: Int) {
    close(exitCode)
  }

  fun doClose() {
    tracker.onLeave()
    parentDialog?.close(CANCEL_EXIT_CODE) ?: run {
      close(CANCEL_EXIT_CODE)
    }
  }

  override fun doCancelAction() {
    super.doCancelAction()
    parentDialog?.doCancelAction()
  }

  override fun show() {
    if (parentDialog != null) return
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

  protected fun nextStep(page: PageProvider, code: Int? = null) {
    parentDialog?.showPage(page) ?: run {
      page.isModal = false
      page.isResizable = false
      page.show()

      SwingUtilities.invokeLater {
        page.pack()
      }
    }

    code?.let {
      doAction(it)
    }
  }

  abstract val tracker: WizardPageTracker
}
