// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel

internal class GitLabChooseAccountDialog @JvmOverloads constructor(project: Project?, parentComponent: Component?,
                                                                   accounts: Collection<GitLabAccount>,
                                                                   showHosts: Boolean, allowDefault: Boolean,
                                                                   title: @Nls(capitalization = Nls.Capitalization.Title) String
                                                                   = GitLabBundle.message("account.choose.title"),
                                                                   description: @Nls(capitalization = Nls.Capitalization.Sentence) String?
                                                                   = null,
                                                                   okText: @Nls(capitalization = Nls.Capitalization.Title) String
                                                                   = GitLabBundle.message("account.choose.action"))
  : DialogWrapper(project, parentComponent, false, IdeModalityType.IDE) {

  private val description: JComponent? = description?.let { SimpleHtmlPane(it) }
  private val accountsList: JBList<GitLabAccount> = JBList(accounts).apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = object : ColoredListCellRenderer<GitLabAccount>() {
      override fun customizeCellRenderer(list: JList<out GitLabAccount>,
                                         value: GitLabAccount,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) {
        append(value.name)
        if (showHosts) {
          append(" ")
          append(value.server.toString(), SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
        border = JBUI.Borders.empty(0, UIUtil.DEFAULT_HGAP)
      }
    }
  }
  private val setDefaultCheckBox: JBCheckBox? = if (allowDefault) JBCheckBox(GitLabBundle.message("account.choose.set.default")) else null

  init {
    this.title = title
    setOKButtonText(okText)
    init()
    accountsList.selectedIndex = 0
  }

  override fun doValidate(): ValidationInfo? {
    return if (accountsList.selectedValue == null) ValidationInfo(GitLabBundle.message("account.choose.not.selected"), accountsList)
    else null
  }

  val account: GitLabAccount get() = accountsList.selectedValue
  val setDefault: Boolean get() = setDefaultCheckBox?.isSelected ?: false

  override fun createCenterPanel(): JComponent {
    return JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
      .addToCenter(JBScrollPane(accountsList).apply {
        preferredSize = JBDimension(150, 20 * (accountsList.itemsCount + 1))
      })
      .apply { description?.run(::addToTop) }
      .apply { setDefaultCheckBox?.run(::addToBottom) }
  }

  override fun getPreferredFocusedComponent() = accountsList
}