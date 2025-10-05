// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.gitlab.git.ui

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.asObservableIn
import com.intellij.collaboration.ui.util.*
import com.intellij.collaboration.util.getOrNull
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ClientProperty
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.dsl.builder.*
import git4idea.DialogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.dto.GitLabGroupDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNamespaceDTO
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginSource
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.LoginResult
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * Creates the modal dialog that will be shown when performing a "Share Project on GitLab" action.
 */
internal object GitLabShareProjectDialogComponentFactory {
  fun showIn(
    project: Project,
    projectName: String,
  ): GitLabShareProjectDialogResult? {
    var result: GitLabShareProjectDialogResult? = null

    suspend fun CoroutineScope.createCenterPanelImpl(): DialogPanel = withContext(Dispatchers.EDT) {
      panel {
        val cs = this@createCenterPanelImpl

        val vm = GitLabShareProjectDialogViewModel(project, cs, projectName)
        onApply { result = vm.state }

        row {
          comboBox(vm.accounts.toComboBoxModelIn(cs), renderer = { _, value, _, _, _ ->
            if (value == null) return@comboBox JLabel(GitLabBundle.message("share.dialog.account.none"))
            else JLabel("${value.server.toURI().host} - ${value.name}")
          })
            .label(GitLabBundle.message("share.dialog.account.label"), LabelPosition.TOP)
            .bindSelectedItemIn(cs, vm.account.valueFlow)
            .bindValidationOnApplyIn(cs, vm.account)
            .align(AlignX.FILL).resizableColumn()

          link(GitLabBundle.message("share.dialog.account.addButton")) { event ->
            val (account, token) = GitLabLoginUtil.logInViaToken(project, event.source as JComponent, loginSource = GitLabLoginSource.SHARE, uniqueAccountPredicate = { server, username ->
              vm.accounts.value.none { it.server == server && it.name == username }
            }) as? LoginResult.Success ?: return@link

            vm.updateAccount(account, token)
          }.align(AlignX.RIGHT)
        }

        row {
          icon(AllIcons.General.Warning)

          link(GitLabBundle.message("share.dialog.tokenExpired.label")) { event ->
            val account = vm.account.value ?: return@link
            val (_, token) = GitLabLoginUtil.updateToken(project, event.source as JComponent, account, loginSource = GitLabLoginSource.SHARE, uniqueAccountPredicate = { server, username ->
              vm.accounts.value.none { it.server == server && it.name == username }
            }) as? LoginResult.Success ?: return@link

            vm.updateAccount(account, token)
          }
        }.visibleIf(vm.reloginRequired.asObservableIn(cs))

        panel {
          row {
            comboBox(vm.namespaces.mapState { it.getOrNull() ?: listOf() }.toComboBoxModelIn(cs), renderer = { _, value, _, _, _ ->
              when (value) {
                // User namespace
                is GitLabNamespaceDTO -> JLabel(GitLabBundle.message("share.dialog.namespace.personal", value.fullName))
                is GitLabGroupDTO -> JLabel(value.fullName)
                else -> JLabel(GitLabBundle.message("share.dialog.namespace.none"))
              }
            }).label(GitLabBundle.message("share.dialog.namespace.label"), LabelPosition.TOP)
              .bindSelectedItemIn(cs, vm.namespace.valueFlow)
              .bindValidationOnApplyIn(cs, vm.namespace)
              .align(AlignX.FILL).resizableColumn()
              .applyToComponent {
                ComboboxSpeedSearch.installSpeedSearch(this) { it.fullName }
              }

            actionButton(iconAction(AllIcons.General.Refresh) { vm.reloadNamespaces() })
              .enabledIf(vm.namespaces.mapState { !it.isInProgress }.asObservableIn(cs))
              .applyToComponent {
                ClientProperty.put(this, AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
                cs.launchNow {
                  vm.hasValidAccount.collectLatest { hasValidAccount ->
                    presentation.disabledIcon = if (hasValidAccount) AnimatedIcon.Default.INSTANCE else AllIcons.General.Refresh
                  }
                }
              }

            contextHelp(GitLabBundle.message("share.dialog.namespace.contextHelp"))
          }

          panel {
            row {
              textField()
                .label(GitLabBundle.message("share.dialog.repositoryName.label"), LabelPosition.TOP)
                .bindTextIn(cs, vm.repositoryName.valueFlow)
                .bindValidationOnApplyIn(cs, vm.repositoryName)
                .align(AlignX.FILL)

              checkBox(GitLabBundle.message("share.dialog.private.label"))
                .selected(true)
                .bindSelectedIn(cs, vm.isPrivate)
            }

            row {
              textField()
                .label(GitLabBundle.message("share.dialog.remoteName.label"), LabelPosition.TOP)
                .bindTextIn(cs, vm.remoteName.valueFlow)
                .bindValidationOnApplyIn(cs, vm.remoteName)
                .align(AlignX.FILL)
            }

            row {
              expandableTextField()
                .label(GitLabBundle.message("share.dialog.description.label"), LabelPosition.TOP)
                .bindTextIn(cs, vm.description)
                .align(AlignX.FILL)
            }
          }
        }.enabledIf(vm.hasValidAccount.asObservableIn(cs))
      }
    }

    val dialog = object : DialogWrapperAsync(project) {
      init {
        title = GitLabBundle.message("action.GitLab.Share.text")
        setOKButtonText(GitLabBundle.message("share.dialog.okButton.text"))

        init()
      }

      override suspend fun CoroutineScope.createCenterPanelAsync(): DialogPanel = createCenterPanelImpl()
    }

    DialogManager.show(dialog)

    return if (dialog.isOK) result else null
  }
}
