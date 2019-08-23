// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ui.VcsCloneComponent
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.ItemEvent
import java.util.*
import javax.swing.Icon
import javax.swing.JPanel

class RepositoryUrlCloneDialogExtension : VcsCloneDialogExtension {
  private val tooltip = CheckoutProvider.EXTENSION_POINT_NAME.extensions
    .map { it.vcsName }
    .joinToString { it.replace("_".toRegex(), "") }

  override fun getIcon(): Icon = AllIcons.Welcome.FromVCS

  override fun getName() = "Repository URL"

  override fun getTooltip(): String? {
    return tooltip
  }

  override fun createMainComponent(project: Project): RepositoryUrlMainExtensionComponent {
    return RepositoryUrlMainExtensionComponent(project)
  }

  class RepositoryUrlMainExtensionComponent(private val project: Project) : VcsCloneDialogExtensionComponent() {
    override fun onComponentSelected() {
      dialogStateListener.onOkActionNameChanged(getCurrentVcsComponent()?.getOkButtonText() ?: "Clone")
      dialogStateListener.onOkActionEnabled(true)
    }

    private val vcsComponents = HashMap<CheckoutProvider, VcsCloneComponent>()
    private val cardLayout = CardLayout()
    private val mainPanel = JPanel(BorderLayout())
    private val comboBox: ComboBox<CheckoutProvider> = ComboBox<CheckoutProvider>().apply {
      renderer = SimpleListCellRenderer.create<CheckoutProvider>("") { it.vcsName.removePrefix("_") }
    }

    init {
      val northPanel = panel {
        row ("Version control:") {
            comboBox()
        }
      }
      val insets = UIUtil.PANEL_REGULAR_INSETS
      northPanel.border = JBEmptyBorder(insets.top, insets.left, 0, insets.right)
      mainPanel.add(northPanel, BorderLayout.NORTH)

      val centerPanel = JPanel(cardLayout)
      mainPanel.add(centerPanel, BorderLayout.CENTER)

      comboBox.addItemListener { e: ItemEvent ->
        if (e.stateChange == ItemEvent.SELECTED) {
          val provider = e.item as CheckoutProvider
          onComponentSelected()
          cardLayout.show(centerPanel, provider.vcsName)
        }
      }

      val providers = CheckoutProvider.EXTENSION_POINT_NAME.extensions.sortedArrayWith(CheckoutProvider.CheckoutProviderComparator())
      for (checkoutProvider in providers) {
        comboBox.addItem(checkoutProvider)
        val vcsComponent = checkoutProvider.buildVcsCloneComponent(project)
        vcsComponents[checkoutProvider] = vcsComponent
        val view = vcsComponent.getView()
        centerPanel.add(view, checkoutProvider.vcsName)
      }
    }

    override fun getView() = mainPanel

    fun openForVcs(clazz: Class<out CheckoutProvider>): RepositoryUrlMainExtensionComponent {
      comboBox.selectedItem = CheckoutProvider.EXTENSION_POINT_NAME.findExtension(clazz)
      return this
    }

    override fun doClone() {
      val listener = ProjectLevelVcsManager.getInstance(project).compositeCheckoutListener
      getCurrentVcsComponent()?.doClone(project, listener)
    }

    override fun doValidateAll(): List<ValidationInfo> {
      return getCurrentVcsComponent()?.doValidateAll() ?: emptyList()
    }

    private fun getCurrentVcsComponent() = vcsComponents[comboBox.selectedItem as CheckoutProvider]
  }
}
