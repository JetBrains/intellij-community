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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.ItemEvent
import java.util.*
import javax.swing.Icon
import javax.swing.JPanel

class RepositoryUrlCloneDialogExtension : VcsCloneDialogExtension {
  override fun getIcon(): Icon = AllIcons.Welcome.FromVCS

  override fun getName() = "Repository URL"

  override fun createMainComponent(project: Project): RepositoryUrlMainExtensionComponent {
    return RepositoryUrlMainExtensionComponent(project)
  }

  class RepositoryUrlMainExtensionComponent(private val project: Project) : VcsCloneDialogExtensionComponent {
    private val vcsComponents = HashMap<String, VcsCloneComponent>()
    private val cardLayout = CardLayout()
    private val mainPanel = JPanel(BorderLayout())
    private val comboBox: ComboBox<CheckoutProvider> = ComboBox<CheckoutProvider>().apply {
      renderer = SimpleListCellRenderer.create<CheckoutProvider>("") { it.vcsName.removePrefix("_") }
    }

    init {
      mainPanel.border = JBUI.Borders.emptyRight(UIUtil.DEFAULT_VGAP)
      val northPanel = panel {
        row("Version control:") { comboBox() }
      }
      mainPanel.add(northPanel, BorderLayout.NORTH)

      val centerPanel = JPanel(cardLayout)
      mainPanel.add(centerPanel, BorderLayout.CENTER)

      comboBox.addItemListener { e: ItemEvent ->
        if (e.stateChange == ItemEvent.SELECTED) {
          val provider = e.item as CheckoutProvider
          cardLayout.show(centerPanel, provider.vcsName)
        }
      }

      for (checkoutProvider in CheckoutProvider.EXTENSION_POINT_NAME.extensions) {
        comboBox.addItem(checkoutProvider)
        val vcsComponent = checkoutProvider.buildVcsCloneComponent(project)
        vcsComponents[checkoutProvider.vcsName] = vcsComponent
        val view = vcsComponent.getView()
        centerPanel.add(view, checkoutProvider.vcsName)
      }
    }

    override fun getView() = mainPanel

    override fun getOkButtonText(): String {
      return getCurrentVcsComponent()?.getOkButtonText() ?: "Clone"
    }

    fun openForVcs(clazz: Class<out CheckoutProvider>): RepositoryUrlMainExtensionComponent {
      comboBox.selectedItem = CheckoutProvider.EXTENSION_POINT_NAME.findExtension(clazz)
      return this
    }

    override fun doClone() {
      val listener = ProjectLevelVcsManager.getInstance(project).compositeCheckoutListener
      getCurrentVcsComponent()?.doClone(project, listener)
    }

    override fun isOkEnabled(): Boolean {
      return getCurrentVcsComponent()?.isOkEnabled() ?: true
    }

    override fun doValidateAll(): List<ValidationInfo> {
      return getCurrentVcsComponent()?.doValidateAll() ?: emptyList()
    }

    private fun getCurrentVcsComponent() = vcsComponents[getCurrentVcsName()]

    private fun getCurrentVcsName(): String {
      val checkoutProvider = comboBox.selectedItem as CheckoutProvider
      return checkoutProvider.vcsName
    }
  }
}
