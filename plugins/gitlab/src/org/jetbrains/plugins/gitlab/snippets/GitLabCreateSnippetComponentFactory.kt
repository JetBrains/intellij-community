// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
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

      override fun createCenterPanel(): JComponent = createPanel(cs, createSnippetVm)
    }

  /**
   * Creates the central form panel for filling in Snippet information.
   */
  private fun createPanel(cs: CoroutineScope, createSnippetVm: GitLabCreateSnippetViewModel): JComponent {
    val data = createSnippetVm.data

    fun setAccount(glAccount: GitLabAccount?) {
      if (glAccount != null) {
        cs.async {
          createSnippetVm.setAccount(glAccount)
        }
      }
    }

    return panel {
      row(message("snippet.create.account.label")) {
        val selectAccount = comboBox(listOf<GitLabAccount>(), ListCellRenderer<GitLabAccount?> { _, accountOrNull, _, _, _ ->
          val account = accountOrNull ?: return@ListCellRenderer JBLabel("<none>")
          JBLabel(account.name).apply {
            toolTipText = "@ ${account.server.uri}"
          }
        })

        selectAccount.bindItem({ createSnippetVm.glAccount.value }, ::setAccount)

        val selectAccountComponent = selectAccount.component
        selectAccountComponent.addItemListener {
          setAccount(selectAccountComponent.selectedItem as GitLabAccount?)
        }
        cs.async {
          // If accounts are updated, set the entries of the accounts list
          createSnippetVm.glAccounts.collectLatest { accounts ->
            selectAccountComponent.removeAllItems()
            val selected = selectAccountComponent.selectedItem
            accounts.forEach { selectAccountComponent.addItem(it) }
            selectAccountComponent.selectedItem = selected
          }
        }
      }

      row(message("snippet.create.project.label")) {
        val selectProject = comboBox(listOf<GitLabProjectCoordinates?>(null),
                                     ListCellRenderer<GitLabProjectCoordinates?> { _, value, _, _, _ ->
                                       JBLabel(value?.projectPath?.toString() ?: message("snippet.create.project.none"))
                                     })
          .bindItem(data::onProject)

        cs.async {
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
        }.bindText(data::title)
      }

      row(message("snippet.create.description.label")) {
        textArea().bindText(data::description)
      }

      row(message("snippet.create.path-mode")) {
        comboBox(PathHandlingMode.values().toList(),
                 ListCellRenderer { _, value, _, _, _ ->
                   JLabel(value?.displayName).apply {
                     toolTipText = value?.tooltip
                   }
                 }).applyToComponent {
          toolTipText = message("snippet.create.path-mode.tooltip")
        }
          .bindItem({ data.pathHandlingMode }, { data.pathHandlingMode = it!! })
      }

      row(message("snippet.create.private.label")) {
        checkBox("").bindSelected(data::isPrivate)
      }
    }
  }
}