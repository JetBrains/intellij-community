// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.util.GitLabBundle.message
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.ListCellRenderer

internal object GitLabCreateSnippetComponentFactory {
  /**
   * Creates the 'Create Snippet' dialog.
   *
   * @param cs Coroutine Scope expected to be on the Main dispatcher to deal with UI changes.
   */
  fun create(cs: CoroutineScope,
             project: Project?,
             createSnippetVm: GitLabCreateSnippetViewModel): DialogWrapper =
    object : DialogWrapper(project, false) {
      init {
        title = message("snippet.create.dialog.title")

        init()
      }

      override fun createCenterPanel(): JComponent =
        createPanel(cs, createSnippetVm) {
          this.isOKActionEnabled = it
        }
    }

  /**
   * Creates the central form panel for filling in Snippet information.
   */
  private fun createPanel(cs: CoroutineScope, createSnippetVm: GitLabCreateSnippetViewModel, whenValid: (Boolean) -> Unit): JComponent {
    // Start with empty title, thus an incomplete form
    whenValid(false)

    val data = createSnippetVm.data

    fun setAccount(glAccount: GitLabAccount?) {
      if (glAccount != null) {
        cs.launch {
          createSnippetVm.setAccount(glAccount)
        }
      }
    }

    return panel {
      row(message("snippet.create.project.label")) {
        val selectProject = comboBox(listOf<GitLabProjectCoordinates?>(null),
                                     ListCellRenderer<GitLabProjectCoordinates?> { _, value, _, _, _ ->
                                       JBLabel(value?.projectPath?.toString() ?: message("snippet.create.project.none"))
                                     })
          .widthGroup("right")
          .bindItem(data::onProject)

        cs.launch {
          createSnippetVm.glRepositories.collectLatest { glProjects ->
            selectProject.component.removeAllItems()
            selectProject.component.addItem(null)
            glProjects.forEach { selectProject.component.addItem(it) }
          }
        }
      }

      row(message("snippet.create.title.label")) {
        textField().applyToComponent {
          toolTipText = message("snippet.create.title.tooltip")
        }
          .widthGroup("right")
          .onChanged {
            whenValid(it.text.isNotEmpty())
          }
          .bindText(data::title)
      }

      row(message("snippet.create.description.label")) {
        textArea()
          .widthGroup("right")
          .bindText(data::description)
      }

      row(message("snippet.create.path-mode")) {
        comboBox(PathHandlingMode.values().toList(),
                 ListCellRenderer { _, value, _, _, _ ->
                   val selectable = value in createSnippetVm.availablePathModes
                   object : JLabel(value?.displayName), ComboBox.SelectableItem {
                     init {
                       toolTipText = if (selectable) value?.tooltip else message("snippet.create.path-mode.unavailable.tooltip")
                       isEnabled = selectable
                     }

                     override fun isSelectable(): Boolean = selectable
                   }
                 }).applyToComponent {
          toolTipText = message("snippet.create.path-mode.tooltip")
          isSwingPopup = false
        }
          .widthGroup("right")
          .bindItem({ data.pathHandlingMode }, { data.pathHandlingMode = it!! })
      }

      row {
        checkBox(message("snippet.create.private.label")).bindSelected(data::isPrivate)
        checkBox(message("snippet.create.copy-url.label")).bindSelected(data::isCopyUrl)
        checkBox(message("snippet.create.open-in-browser.label")).bindSelected(data::isOpenInBrowser)
      }

      row(message("snippet.create.account.label")) {
        val selectAccount = comboBox(listOf<GitLabAccount>(), ListCellRenderer<GitLabAccount?> { _, accountOrNull, _, _, _ ->
          // The list shouldn't contain nulls, but if they do, don't render anything
          val account = accountOrNull ?: return@ListCellRenderer null

          JBLabel(account.name).apply {
            toolTipText = "@ ${account.server.uri}" // TODO: Check that this makes sense to show
          }
        })
          .widthGroup("right")
          .bindItem({ createSnippetVm.glAccount.value }, ::setAccount)

        selectAccount.component.addItemListener {
          setAccount(selectAccount.component.selectedItem as GitLabAccount?)
        }
        cs.launch {
          createSnippetVm.glAccounts.collectLatest { accounts ->
            // Re-fill the list of accounts
            selectAccount.component.removeAllItems()
            val selected = selectAccount.component.selectedItem
            accounts.forEach { selectAccount.component.addItem(it) }
            selectAccount.component.selectedItem = selected

            // If none is selected yet, select the first account
            if (selectAccount.component.selectedItem == null && selectAccount.component.itemCount > 0) {
              selectAccount.component.selectedIndex = 0
            }

            // If there are multiple accounts make the row visible
            visible(accounts.size > 1)
          }
        }
      }
    }
  }
}