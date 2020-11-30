// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.codeInsight.AutoPopupController
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.InplaceButton
import com.intellij.util.textCompletion.TextCompletionProvider
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

object GHPRSearchPanel {

  private const val SHOW_SEARCH_HISTORY_ACTION = "ShowSearchHistory"

  fun create(project: Project,
             model: SingleValueModel<String>,
             completionProvider: TextCompletionProvider,
             pullRequestUiSettings: GithubPullRequestsProjectUISettings): JComponent {

    var showSearchHistoryButton: JComponent? = null

    val showSearchHistoryAction = {
      JBPopupFactory.getInstance()
        .createListPopup(object : BaseListPopupStep<String>(null, pullRequestUiSettings.getRecentSearchFilters()) {
          override fun onChosen(selectedValue: String?, finalChoice: Boolean) = doFinalStep {
            selectedValue?.let {
              model.value = it
            }
          }
        })
        .showUnderneathOf(showSearchHistoryButton!!)
    }

    showSearchHistoryButton = InplaceButton(
      IconButton(
        GithubBundle.message("pull.request.list.search.history", KeymapUtil.getFirstKeyboardShortcutText(SHOW_SEARCH_HISTORY_ACTION)),
        AllIcons.Actions.SearchWithHistory)) {
      showSearchHistoryAction()
    }.let { JBUI.Panels.simplePanel(it).withBorder(JBUI.Borders.emptyLeft(5)).withBackground(UIUtil.getListBackground()) }

    val searchField = object : TextFieldWithCompletion(project, completionProvider, "", true, true, false, false) {
      override fun setupBorder(editor: EditorEx) {
        editor.setBorder(JBUI.Borders.empty(6, 5))
      }

      override fun processKeyBinding(ks: KeyStroke?, e: KeyEvent?, condition: Int, pressed: Boolean): Boolean {
        if (e?.keyCode == KeyEvent.VK_ENTER && pressed) {
          val query = text.trim()
          if (query.isNotEmpty()) {
            pullRequestUiSettings.addRecentSearchFilter(query)
          }
          model.value = query
          return true
        }
        return super.processKeyBinding(ks, e, condition, pressed)
      }
    }.apply {
      addSettingsProvider {
        it.putUserData(AutoPopupController.NO_ADS, true)
        UIUtil.setNotOpaqueRecursively(it.component)
      }

      DumbAwareAction.create { showSearchHistoryAction() }
        .registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(SHOW_SEARCH_HISTORY_ACTION), this)
    }

    model.addAndInvokeValueChangedListener {
      searchField.text = model.value
    }

    return JBUI.Panels.simplePanel(searchField).addToLeft(showSearchHistoryButton).withBackground(UIUtil.getListBackground())
  }
}