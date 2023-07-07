// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.async.disposingMainScope
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.dvcs.repo.ClonePathProvider
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.dvcs.ui.FilePathDocumentChildPathHandle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.panels.Wrapper
import git4idea.GitUtil
import git4idea.remote.GitRememberedInputs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.ui.clone.GitLabCloneUISelectorViewModel.UIState
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

internal class GitLabCloneComponent(
  private val project: Project,
  modalityState: ModalityState,
  accountManager: GitLabAccountManager
) : VcsCloneDialogExtensionComponent() {
  private val cs: CoroutineScope = disposingMainScope() + modalityState.asContextElement()

  private val uiSelectorVm = GitLabCloneUISelectorViewModelImpl(cs, accountManager)
  private val loginVm = GitLabCloneLoginViewModelImpl(cs, accountManager)
  private val repositoriesVm = GitLabCloneRepositoriesViewModelImpl(project, cs, accountManager)

  private val searchField: SearchTextField = SearchTextField(false).apply {
    addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        repositoriesVm.setSearchValue(text)
      }
    })
  }
  private val directoryField: TextFieldWithBrowseButton = createDirectoryField()
  private val cloneDirectoryChildHandle: FilePathDocumentChildPathHandle = FilePathDocumentChildPathHandle.install(
    directoryField.textField.document,
    ClonePathProvider.defaultParentDirectoryPath(project, GitRememberedInputs.getInstance())
  )

  private val wrapper: Wrapper = Wrapper().apply {
    bindContentIn(cs, uiSelectorVm.uiState) { ui ->
      val innerCs = this
      when (ui) {
        is UIState.Login -> GitLabCloneLoginComponentFactory.create(innerCs, loginVm, uiSelectorVm, ui.account)
        UIState.Repositories -> GitLabCloneRepositoriesComponentFactory.create(
          innerCs, repositoriesVm, uiSelectorVm, searchField, directoryField
        ).apply {
          registerValidators(innerCs.nestedDisposable())
        }
      }
    }
  }

  init {
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      repositoriesVm.selectedUrl.collectLatest { selectedUrl ->
        val isUrlSelected = selectedUrl != null
        dialogStateListener.onOkActionEnabled(isUrlSelected)
        if (isUrlSelected) {
          val path = ClonePathProvider.relativeDirectoryPathForVcsUrl(project, selectedUrl!!).removeSuffix(GitUtil.DOT_GIT)
          cloneDirectoryChildHandle.trySetChildPath(path)
        }
      }
    }

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      repositoriesVm.accountsUpdatedRequest.collectLatest {
        dialogStateListener.onListItemChanged()
      }
    }
  }

  override fun getView(): JComponent {
    return wrapper
  }

  override fun doClone(checkoutListener: CheckoutProvider.Listener) {
    repositoriesVm.doClone(checkoutListener, directoryField.text)
  }

  override fun doValidateAll(): List<ValidationInfo> {
    val dialogPanel = wrapper.targetComponent as? DialogPanel ?: return emptyList()
    dialogPanel.apply()
    val errors = dialogPanel.validateAll()
    if (errors.isNotEmpty()) {
      errors.first().component?.let {
        CollaborationToolsUIUtil.focusPanel(it)
      }
    }

    return errors
  }

  override fun onComponentSelected() {
    dialogStateListener.onOkActionNameChanged(DvcsBundle.message("clone.button"))
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return searchField
  }

  private fun createDirectoryField(): TextFieldWithBrowseButton {
    return TextFieldWithBrowseButton().apply {
      val fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
        isShowFileSystemRoots = true
        isHideIgnored = false
      }
      addBrowseFolderListener(
        DvcsBundle.message("clone.destination.directory.browser.title"),
        DvcsBundle.message("clone.destination.directory.browser.description"),
        project,
        fcd
      )
    }
  }
}