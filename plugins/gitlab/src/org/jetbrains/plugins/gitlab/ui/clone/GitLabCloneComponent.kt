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
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneLoginViewModel
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneRepositoriesViewModel
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneViewModelImpl
import javax.swing.JComponent

internal class GitLabCloneComponent(
  private val project: Project,
  modalityState: ModalityState,
  accountManager: GitLabAccountManager
) : VcsCloneDialogExtensionComponent() {
  private val cs: CoroutineScope = disposingScope() + modalityState.asContextElement() + Dispatchers.Default
  private val uiCs: CoroutineScope = cs.childScope(Dispatchers.Main)

  private val cloneVm = GitLabCloneViewModelImpl(project, cs, accountManager)

  private val wrapper: Wrapper = Wrapper().apply {
    bindContentIn(uiCs, cloneVm.panelVm) { panelVm ->
      val innerCs = this
      when (panelVm) {
        is GitLabCloneLoginViewModel -> GitLabCloneLoginComponentFactory.create(innerCs, panelVm, cloneVm)
        is GitLabCloneRepositoriesViewModel -> GitLabCloneRepositoriesComponentFactory.create(
          project, innerCs, panelVm, cloneVm
        ).also { panel ->
          panel.registerValidators(innerCs.nestedDisposable())

          innerCs.launchNow {
            panelVm.selectedUrl.collectLatest { selectedUrl ->
              val isUrlSelected = selectedUrl != null
              dialogStateListener.onOkActionEnabled(isUrlSelected)
            }
          }

          innerCs.launchNow {
            panelVm.accountsUpdatedRequest.collectLatest {
              dialogStateListener.onListItemChanged()
            }
          }

          innerCs.launch {
            yield()
            CollaborationToolsUIUtil.focusPanel(panel)
          }
        }
      }
    }
  }

  override fun getView(): JComponent {
    return wrapper
  }

  override fun doClone(checkoutListener: CheckoutProvider.Listener) {
    cloneVm.doClone(checkoutListener)
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
    CollaborationToolsUIUtil.focusPanel(wrapper.targetComponent)
  }
}