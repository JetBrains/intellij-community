// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector.Companion.CLONE
import com.intellij.openapi.vcs.ui.VcsCloneComponent
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame
import com.intellij.ui.*
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.cloneDialog.RepositoryUrlCloneDialogExtension.RepositoryUrlMainExtensionComponent
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.ListSelectionListener

/**
 * Top-level UI-component for new clone/checkout dialog
 */
class VcsCloneDialog private constructor(private val project: Project) : DialogWrapper(project) {
  private lateinit var extensionList: VcsCloneDialogExtensionList
  private val cardLayout = CardLayout()
  private val mainPanel = JPanel(cardLayout)
  private val extensionComponents: MutableMap<String, VcsCloneDialogExtensionComponent> = HashMap()
  private val listModel = CollectionListModel<VcsCloneDialogExtension>(VcsCloneDialogExtension.EP_NAME.extensionList)

  private val listener = object : VcsCloneDialogComponentStateListener {
    override fun onOkActionNameChanged(name: String) = setOKButtonText(name)

    override fun onOkActionEnabled(enabled: Boolean) {
      isOKActionEnabled = enabled
    }

    override fun onListItemChanged() = listModel.allContentsChanged()
  }

  init {
    init()
    title = VcsBundle.message("get.from.version.control")
    JBUI.size(FlatWelcomeFrame.MAX_DEFAULT_WIDTH, FlatWelcomeFrame.DEFAULT_HEIGHT).let {
      rootPane.minimumSize = it
      rootPane.preferredSize = it
    }
  }

  override fun getStyle() = DialogStyle.COMPACT

  override fun createCenterPanel(): JComponent {
    extensionList = VcsCloneDialogExtensionList(listModel).apply {
      addListSelectionListener(ListSelectionListener { e ->
        val source = e.source as VcsCloneDialogExtensionList
        switchComponent(source.selectedValue)
      })
      preferredSize = JBDimension(200, 0) // width fixed by design
    }
    extensionList.accessibleContext.accessibleName = VcsBundle.message("get.from.vcs.extension.list.accessible.name")
    val scrollableList = ScrollPaneFactory.createScrollPane(extensionList, true).apply {

      border = IdeBorderFactory.createBorder(SideBorder.RIGHT)
    }
    return JBUI.Panels.simplePanel()
      .addToCenter(mainPanel)
      .addToLeft(scrollableList)
  }

  override fun doValidateAll(): List<ValidationInfo> {
    return getSelectedComponent()?.doValidateAll() ?: emptyList()
  }

  override fun getPreferredFocusedComponent(): JComponent? = getSelectedComponent()?.getPreferredFocusedComponent()

  fun doClone(checkoutListener: CheckoutProvider.Listener) {
    val selectedComponent = getSelectedComponent()
    if (selectedComponent != null) {
      CLONE.log(project, selectedComponent.javaClass)
      selectedComponent.doClone(checkoutListener)
    }
  }

  private fun switchComponent(extension: VcsCloneDialogExtension) {
    val extensionId = extension.id
    val mainComponent = extensionComponents.getOrPut(extensionId) {
      val component = extension.createMainComponent(project, ModalityState.stateForComponent(window))
      mainPanel.add(component.getView(), extensionId)
      Disposer.register(disposable, component)
      component.addComponentStateListener(listener)
      component
    }
    mainComponent.onComponentSelected()
    cardLayout.show(mainPanel, extensionId)
  }

  private fun getSelectedComponent(): VcsCloneDialogExtensionComponent? {
    return extensionComponents[extensionList.selectedValue.javaClass.name]
  }

  private fun selectExtension(extension: VcsCloneDialogExtension) {
    ScrollingUtil.selectItem(extensionList, extension)
  }

  class Builder(private val project: Project) {
    fun forExtension(clazz: Class<out VcsCloneDialogExtension> = RepositoryUrlCloneDialogExtension::class.java): VcsCloneDialog {
      return VcsCloneDialog(project).apply {
        VcsCloneDialogExtension.EP_NAME.findExtension(clazz)?.let { selectExtension(it) }
      }
    }

    @JvmOverloads
    fun forVcs(clazz: Class<out CheckoutProvider>, url: String? = null): VcsCloneDialog {
      return VcsCloneDialog(project).apply {
        VcsCloneDialogExtension.EP_NAME.findExtension(RepositoryUrlCloneDialogExtension::class.java)?.let { selectExtension(it) }
        val repoComponent = (getSelectedComponent() as? RepositoryUrlMainExtensionComponent)?.openForVcs(clazz)
        if(url != null) {
          (repoComponent?.getCurrentVcsComponent() as? VcsCloneComponent.WithSettableUrl)?.setUrl(url)
        }
      }
    }
  }
}

private val VcsCloneDialogExtension.id: String
  get() = javaClass.name