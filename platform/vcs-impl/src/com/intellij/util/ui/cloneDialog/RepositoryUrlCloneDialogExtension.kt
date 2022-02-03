// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.ui.VcsCloneComponent
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class RepositoryUrlCloneDialogExtension : VcsCloneDialogExtension {

  override fun getIcon(): Icon = AllIcons.Vcs.FromVCSDialog

  override fun getName() = VcsBundle.message("clone.dialog.repository.url.item")

  override fun getTooltip(): String? {
    return CheckoutProvider.EXTENSION_POINT_NAME.extensions
      .map { it.vcsName }
      .joinToString { it.replace("_", "") }
  }

  override fun createMainComponent(project: Project): VcsCloneDialogExtensionComponent {
    throw AssertionError("Shouldn't be called") // NON-NLS
  }

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent {
    return RepositoryUrlMainExtensionComponent(project, modalityState)
  }

  class RepositoryUrlMainExtensionComponent(private val project: Project,
                                            private val modalityState: ModalityState) : VcsCloneDialogExtensionComponent() {
    override fun onComponentSelected() {
      dialogStateListener.onOkActionNameChanged(getCurrentVcsComponent()?.getOkButtonText() ?: VcsBundle.message("clone.dialog.clone.button"))
      dialogStateListener.onOkActionEnabled(true)

      getCurrentVcsComponent()?.onComponentSelected(dialogStateListener)
    }

    private val vcsComponents = HashMap<CheckoutProvider, VcsCloneComponent>()
    private val mainPanel = JPanel(BorderLayout())
    private val centerPanel = Wrapper()

    private val comboBox: ComboBox<CheckoutProvider> = ComboBox<CheckoutProvider>().apply {
      renderer = SimpleListCellRenderer.create<CheckoutProvider>("") { it.vcsName.removePrefix("_") }
    }

    init {
      val northPanel = panel {
        row(VcsBundle.message("vcs.common.labels.version.control")) {
          comboBox()
        }
      }
      val insets = UIUtil.PANEL_REGULAR_INSETS
      northPanel.border = JBUI.Borders.empty(insets)
      mainPanel.add(northPanel, BorderLayout.NORTH)
      mainPanel.add(centerPanel, BorderLayout.CENTER)

      val providers = CheckoutProvider.EXTENSION_POINT_NAME.extensions
      val selectedByDefaultProvider: CheckoutProvider? = if (providers.isNotEmpty()) providers[0] else null
      providers.sortWith(CheckoutProvider.CheckoutProviderComparator())
      comboBox.model = DefaultComboBoxModel(providers).apply {
        selectedItem = null
      }
      comboBox.addItemListener { e: ItemEvent ->
        if (e.stateChange == ItemEvent.SELECTED) {
          val provider = e.item as CheckoutProvider
          centerPanel.setContent(vcsComponents.getOrPut(provider, {
            val cloneComponent = provider.buildVcsCloneComponent(project, modalityState, dialogStateListener)
            Disposer.register(this, cloneComponent)
            cloneComponent
          }).getView())
          centerPanel.revalidate()
          centerPanel.repaint()
          onComponentSelected()
        }
      }
      comboBox.selectedItem = selectedByDefaultProvider
    }

    override fun getView() = mainPanel

    fun openForVcs(clazz: Class<out CheckoutProvider>): RepositoryUrlMainExtensionComponent {
      comboBox.selectedItem = CheckoutProvider.EXTENSION_POINT_NAME.findExtension(clazz)
      return this
    }

    override fun doClone(checkoutListener: CheckoutProvider.Listener) {
      getCurrentVcsComponent()?.doClone(project, checkoutListener)
    }

    override fun doValidateAll(): List<ValidationInfo> {
      return getCurrentVcsComponent()?.doValidateAll() ?: emptyList()
    }

    override fun getPreferredFocusedComponent(): JComponent? = getCurrentVcsComponent()?.getPreferredFocusedComponent()

    private fun getCurrentVcsComponent() = vcsComponents[comboBox.selectedItem as CheckoutProvider]
  }
}
