// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame.getSeparatorColor
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.border.CustomLineBorder
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.RepositoryUrlCloneDialogExtension.RepositoryUrlMainExtensionComponent
import java.awt.CardLayout
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.ListSelectionListener

/**
 * Top-level UI-component for new clone/checkout dialog
 */
internal class VcsCloneDialog private constructor(private val project: Project,
                                                  initialExtensionClass: Class<out VcsCloneDialogExtension>,
                                                  private var initialVcs: Class<out CheckoutProvider>? = null) : DialogWrapper(project) {
  private lateinit var extensionList: VcsCloneDialogExtensionList
  private val cardLayout = CardLayout()
  private val mainPanel = JPanel(cardLayout)
  private val extensionComponents: MutableMap<String, VcsCloneDialogExtensionComponent> = HashMap()

  init {
    init()
    title = "Get From Version Control"
    JBUI.size(FlatWelcomeFrame.MAX_DEFAULT_WIDTH, FlatWelcomeFrame.DEFAULT_HEIGHT).let {
      rootPane.minimumSize = it
      rootPane.preferredSize = it
    }

    val withoutRightInset = UIUtil.PANEL_REGULAR_INSETS.let {
      // use empty right inset to align the scroll bar to the edge of panel
      JBInsets(it.top, it.left, it.bottom, 0)
    }
    mainPanel.border = JBEmptyBorder(withoutRightInset)

    VcsCloneDialogExtension.EP_NAME.findExtension(initialExtensionClass)?.let {
      ScrollingUtil.selectItem(extensionList, it)
    }
  }

  override fun getStyle() = DialogStyle.COMPACT

  override fun createCenterPanel(): JComponent {
    val extensions = VcsCloneDialogExtension.EP_NAME.extensionList
    val listModel = CollectionListModel<VcsCloneDialogExtension>(extensions)

    extensionList = VcsCloneDialogExtensionList(listModel).apply {
      addListSelectionListener(ListSelectionListener { e ->
        val source = e.source as VcsCloneDialogExtensionList
        switchComponent(source.selectedValue)
      })
    }
    val scrollableList = ScrollPaneFactory.createScrollPane(extensionList, true).apply {
      border = CustomLineBorder(getSeparatorColor(), JBUI.insetsRight(1))
    }
    return JBUI.Panels.simplePanel()
      .addToCenter(mainPanel)
      .addToLeft(scrollableList).apply {
        border = CustomLineBorder(getSeparatorColor(), JBUI.insetsBottom(1))
      }
  }

  override fun isOKActionEnabled(): Boolean {
    return getSelectedComponent()?.isOkEnabled() ?: false
  }
  
  override fun doValidateAll(): List<ValidationInfo> {
    return getSelectedComponent()?.doValidateAll() ?: emptyList()
  }

  fun doClone() {
    getSelectedComponent()?.doClone()
  }

  private fun switchComponent(extension: VcsCloneDialogExtension) {
    val extensionId = extension.javaClass.name
    val mainComponent = extensionComponents.getOrPut(extensionId, {
      val component = extension.createMainComponent(project)
      val scrollableMainPanel = ScrollPaneFactory.createScrollPane(component.getView(), true)
      scrollableMainPanel.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
      scrollableMainPanel.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      mainPanel.add(scrollableMainPanel, extensionId)
      component
    })

    setOKButtonText(mainComponent.getOkButtonText())
    if (mainComponent is RepositoryUrlMainExtensionComponent) {
      initialVcs?.let { mainComponent.openForVcs(it) }
    }
    cardLayout.show(mainPanel, extensionId)
  }

  private fun getSelectedComponent(): VcsCloneDialogExtensionComponent? {
    return extensionComponents[extensionList.selectedValue.javaClass.name]
  }

  class Builder(private val project: Project) {
    fun forExtension(clazz: Class<out VcsCloneDialogExtension> = RepositoryUrlCloneDialogExtension::class.java): VcsCloneDialog {
      return VcsCloneDialog(project, clazz, null)
    }

    fun forVcs(clazz: Class<out CheckoutProvider>): VcsCloneDialog {
      return VcsCloneDialog(project, RepositoryUrlCloneDialogExtension::class.java, clazz)
    }
  }
}