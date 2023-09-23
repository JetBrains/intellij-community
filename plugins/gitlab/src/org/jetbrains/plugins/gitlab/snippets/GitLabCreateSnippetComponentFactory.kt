// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import com.intellij.util.childScope
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.util.GitLabBundle.message
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.ListCellRenderer

internal object GitLabCreateSnippetComponentFactory {
  /**
   * Creates the 'Create Snippet' dialog.
   */
  fun create(parentCs: CoroutineScope,
             project: Project?,
             createSnippetVm: GitLabCreateSnippetViewModel): DialogWrapper =
    object : DialogWrapper(project, false) {
      private lateinit var titleField: JTextField
      private val cs: CoroutineScope = parentCs.childScope(ModalityState.stateForComponent(window).asContextElement())

      init {
        title = message("snippet.create.dialog.title")

        init()

        cs.launch {
          val hasNonEmptyContents = createSnippetVm.nonEmptyContents.await().isNotEmpty()
          createSnippetVm.glAccounts.collectLatest {
            isOKActionEnabled = hasNonEmptyContents && it.isNotEmpty()
          }
        }
      }

      override fun createCenterPanel(): JComponent =
        createPanel(createSnippetVm)

      override fun doValidate(): ValidationInfo? {
        if (titleField.text.isBlank()) {
          return ValidationInfo(message("snippet.create.dialog.validation.no-title"), titleField)
        }
        return null
      }

      override fun dispose() {
        cs.cancel()
        super.dispose()
      }

      /**
       * Creates the central form panel for filling in Snippet information.
       */
      private fun createPanel(createSnippetVm: GitLabCreateSnippetViewModel): JComponent {
        val data = createSnippetVm.data

        fun setAccount(glAccount: GitLabAccount?) {
          if (glAccount != null) {
            cs.launch {
              createSnippetVm.glAccount.update { glAccount }
            }
          }
        }

        return panel {
          row(message("snippet.create.project.label")) {
            val selectProject = comboBox(listOf<GitLabProjectCoordinates?>(null),
                                         ListCellRenderer<GitLabProjectCoordinates?> { _, value, _, _, _ ->
                                           JBLabel(value?.projectPath?.toString() ?: message("snippet.create.project.none"))
                                         })
              .align(AlignX.FILL)
              .bindItem({ data.value.onProject }, { v -> data.update { data.value.copy(onProject = v) } })

            cs.launch {
              createSnippetVm.glRepositories.collectLatest { glProjects ->
                selectProject.component.removeAllItems()
                selectProject.component.addItem(null)
                glProjects.forEach { selectProject.component.addItem(it) }
              }
            }
          }

          row(message("snippet.create.title.label")) {
            titleField = textField().applyToComponent {
              toolTipText = message("snippet.create.title.tooltip")
            }
              .align(AlignX.FILL)
              .bindText({ data.value.title }, { v -> data.update { data.value.copy(title = v) } })
              .onChanged { initValidation() }
              .component
          }

          row {
            label(message("snippet.create.description.label"))
              .align(AlignY.TOP)

            scrollCell(JBTextArea())
              .align(Align.FILL)
              .rows(4)
              .bindText({ data.value.description }, { v -> data.update { data.value.copy(description = v) } })
              .applyToComponent {
                lineWrap = true
              }
          }.layout(RowLayout.LABEL_ALIGNED).resizableRow()

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
              .align(Align.FILL)
              .bindItem({ data.value.pathHandlingMode }, { v -> data.update { data.value.copy(pathHandlingMode = v!!) } })
          }

          row {
            checkBox(message("snippet.create.private.label"))
              .bindSelected({ data.value.isPrivate }, { v -> data.update { data.value.copy(isPrivate = v) } })
            checkBox(message("snippet.create.copy-url.label"))
              .bindSelected({ data.value.isCopyUrl }, { v -> data.update { data.value.copy(isCopyUrl = v) } })
            checkBox(message("snippet.create.open-in-browser.label"))
              .bindSelected({ data.value.isOpenInBrowser }, { v -> data.update { data.value.copy(isOpenInBrowser = v) } })
          }

          // Account selection if >1 accounts available
          row(message("snippet.create.account.label")) {
            val selectAccount = comboBox(listOf<GitLabAccount>(), ListCellRenderer<GitLabAccount?> { _, accountOrNull, _, _, _ ->
              // The list shouldn't contain nulls, but if they do, don't render anything
              val account = accountOrNull ?: return@ListCellRenderer JBLabel()

              JBLabel(account.name).apply {
                toolTipText = "@ ${account.server.uri}"
              }
            })
              .align(Align.FILL)
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
                if (isVisible != accounts.size > 1) {
                  visible(accounts.size > 1)
                  pack()
                }
              }
            }
          }

          // Error line - some contents empty/no non-empty contents
          row {
            val emptyContentsLabel = label(message("snippet.create.error.some-empty-contents")).applyToComponent {
              foreground = NamedColorUtil.getErrorForeground()
            }.component

            cs.launch {
              val emptyContents = createSnippetVm.emptyContents.await()
              val nonEmptyContents = createSnippetVm.nonEmptyContents.await()
              visible(emptyContents.isNotEmpty())

              if (nonEmptyContents.isEmpty()) {
                emptyContentsLabel.text = message("snippet.create.error.no-contents")
              }
              else if (emptyContents.isNotEmpty()) {
                emptyContentsLabel.toolTipText = message("snippet.create.error.some-empty-contents.tooltip",
                                                         emptyContents.mapNotNull { it.file }.joinToString(", ") { it.name })
              }
            }
          }.visible(false)
        }
      }
    }
}