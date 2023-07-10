// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.async.disposingScope
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.childScope
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.plus
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import javax.swing.JComponent

internal class GitLabCloneComponent(
  private val project: Project,
  modalityState: ModalityState,
  accountManager: GitLabAccountManager
) : VcsCloneDialogExtensionComponent() {
  private val cs: CoroutineScope = disposingScope() + modalityState.asContextElement() + Dispatchers.Default
  private val uiCs: CoroutineScope = cs.childScope(Dispatchers.Main)

  private val uiSelectorVm = GitLabCloneUISelectorViewModelImpl(project, cs, accountManager)

  private val wrapper: Wrapper = Wrapper().apply {
    bindContentIn(uiCs, uiSelectorVm.vm) { vm ->
      val innerCs = this
      when (vm) {
        is GitLabCloneLoginViewModel -> GitLabCloneLoginComponentFactory.create(innerCs, vm, uiSelectorVm)
        is GitLabCloneRepositoriesViewModel -> GitLabCloneRepositoriesComponentFactory.create(project, innerCs, vm, uiSelectorVm).apply {
          registerValidators(innerCs.nestedDisposable())

          innerCs.launchNow {
            vm.selectedUrl.collectLatest { selectedUrl ->
              val isUrlSelected = selectedUrl != null
              dialogStateListener.onOkActionEnabled(isUrlSelected)
            }
          }

          innerCs.launchNow {
            vm.accountsUpdatedRequest.collectLatest {
              dialogStateListener.onListItemChanged()
            }
          }
        }
      }
    }
  }

  override fun getView(): JComponent {
    return wrapper
  }

  override fun doClone(checkoutListener: CheckoutProvider.Listener) {
    uiSelectorVm.doClone(checkoutListener)
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

  override fun getPreferredFocusedComponent(): JComponent? {
    return UIUtil.findComponentOfType(wrapper.targetComponent, SearchTextField::class.java)
  }
}