// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.PageProvider
import com.intellij.ide.startup.importSettings.chooser.ui.UiUtils
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.JBrActionsDataProvider
import com.intellij.ide.startup.importSettings.data.Product
import com.intellij.ide.startup.importSettings.data.SyncActionsDataProvider
import com.intellij.ide.ui.laf.darcula.ui.OnboardingDialogButtons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingConstants

class OtherOptions(private val callback: (PageProvider) -> Unit) : ProductChooserAction() {

  private val jbDataProvider = JBrActionsDataProvider.getInstance()
  private val syncDataProvider = SyncActionsDataProvider.getInstance()

  private var jb: List<AnAction>? = null
  private var sync: List<AnAction>? = null
  private val config = ConfigAction(callback)

  init {
    templatePresentation.text = ImportSettingsBundle.message("choose.product.other.options")
  }

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val jbProducts = jbDataProvider.other
    val syncProducts = if(syncDataProvider.settingsService.isLoggedIn()) syncDataProvider.other else emptyList()

    val arr = mutableListOf<AnAction>()
    if (jb == null && jbProducts != null) {
      jb = addActionList(jbProducts, jbDataProvider, ImportSettingsBundle.message("other.options.sub.title.installed"))
    }

    if (sync == null && syncProducts != null && syncDataProvider.settingsService.isSyncEnabled.value) {
      sync = addActionList(syncProducts, syncDataProvider, ImportSettingsBundle.message("other.options.sub.title.setting.sync"))
    }

    sync?.let {
      if (it.isNotEmpty()) {
        arr.addAll(it)
      }
    }

    jb?.let {
      if (it.isNotEmpty()) {
        arr.addAll(it)
      }
    }

    if (arr.isNotEmpty()) {
      arr.add(Separator())
    }
    arr.add(config)

    return arr.toTypedArray()
  }

  private fun addActionList(products: List<Product>, provider: ActionsDataProvider<*>, title: @Nls String? = null): MutableList<AnAction> {
    val list = mutableListOf<AnAction>()
    if (products.isNotEmpty()) {
      title?.let {
        list.add(Separator(it))
      }
      list.addAll(productsToActions(products, provider, callback))
    }
    return list
  }

  init {
    templatePresentation.isPopupGroup = true
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    val ch = getChildren(e)

    if(ch.size == 1) {
      return
    }

    if (ch.isEmpty()) {
      e.presentation.isVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.text = ImportSettingsBundle.message("choose.product.other.options")
    e.presentation.icon = AllIcons.General.LinkDropTriangle
    e.presentation.isPopupGroup = true
  }


  override fun updateButtonFromPresentation(button: JButton, presentation: Presentation) {
    super.updateButtonFromPresentation(button, presentation)
    if (presentation.getClientProperty(UiUtils.POPUP) == true) {
      button.setHorizontalTextPosition(SwingConstants.LEFT)
      button.setForeground(JBUI.CurrentTheme.Link.Foreground.ENABLED)
      button.iconTextGap = 0
    } else {
      button.setHorizontalTextPosition(SwingConstants.RIGHT)
      button.setForeground(JBUI.CurrentTheme.Label.foreground())
      button.iconTextGap = JBUI.scale(4)
    }
  }

  override fun createButton(): JButton {
    return OnboardingDialogButtons.createLinkButton().apply {
      icon = AllIcons.General.LinkDropTriangle
    }
  }

  override fun wrapButton(button: JButton): JComponent {
    return button
  }
}


