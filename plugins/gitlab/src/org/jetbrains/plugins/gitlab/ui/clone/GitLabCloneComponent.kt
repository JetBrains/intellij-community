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
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.panels.Wrapper
import git4idea.GitUtil
import git4idea.remote.GitRememberedInputs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import javax.swing.JComponent

internal class GitLabCloneComponent(
  private val project: Project,
  modalityState: ModalityState,
  accountManager: GitLabAccountManager
) : VcsCloneDialogExtensionComponent() {
  private val cs: CoroutineScope = disposingMainScope() + modalityState.asContextElement()
  private val cloneVm = GitLabCloneViewModelImpl(project, cs, accountManager)

  private val searchField: SearchTextField = SearchTextField(false)
  private val directoryField: TextFieldWithBrowseButton = createDirectoryField()
  private val cloneDirectoryChildHandle: FilePathDocumentChildPathHandle = FilePathDocumentChildPathHandle.install(
    directoryField.textField.document,
    ClonePathProvider.defaultParentDirectoryPath(project, GitRememberedInputs.getInstance())
  )

  private val repositoriesPanel: DialogPanel = GitLabCloneRepositoriesComponentFactory.create(
    project, cs, cloneVm, searchField, directoryField
  ).apply {
    registerValidators(cs.nestedDisposable())
  }
  private val wrapper: Wrapper = Wrapper().apply {
    bindContentIn(cs, cloneVm.uiState) { componentState ->
      when (componentState) {
        GitLabCloneViewModel.UIState.LOGIN  -> GitLabCloneLoginComponentFactory.create(this, cloneVm, accountManager)
        GitLabCloneViewModel.UIState.REPOSITORY_LIST -> repositoriesPanel
      }
    }
  }

  init {
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      cloneVm.selectedItem.collect { selectedItem ->
        val cloneUrl = (selectedItem as? GitLabCloneListItem.Repository)?.projectMember?.project?.httpUrlToRepo
        if (cloneUrl != null) {
          val path = ClonePathProvider.relativeDirectoryPathForVcsUrl(project, cloneUrl).removeSuffix(GitUtil.DOT_GIT)
          cloneDirectoryChildHandle.trySetChildPath(path)
        }
        dialogStateListener.onOkActionEnabled(cloneUrl != null)
      }
    }

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      cloneVm.accountsRefreshRequest.collect {
        dialogStateListener.onListItemChanged()
      }
    }
  }

  override fun getView(): JComponent {
    return wrapper
  }

  override fun doClone(checkoutListener: CheckoutProvider.Listener) {
    cloneVm.doClone(checkoutListener, directoryField.text)
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