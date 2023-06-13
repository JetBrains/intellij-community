// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.swing.JComponent

internal class GitLabCloneComponent(
  parentCs: CoroutineScope,
  private val cloneVm: GitLabCloneViewModel
) : VcsCloneDialogExtensionComponent() {
  private val cs: CoroutineScope = parentCs.childScope()

  private val searchField: SearchTextField = SearchTextField(false)
  private val repositoriesPanel: JComponent = GitLabCloneRepositoriesComponentFactory.create(cs, cloneVm, searchField)
  private val wrapper: Wrapper = Wrapper().apply {
    bindContentIn(cs, cloneVm.accounts.map { accounts ->
      if (accounts.isEmpty()) null else repositoriesPanel
    })
  }

  init {
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      cloneVm.selectedItem.collect { selectedItem ->
        dialogStateListener.onOkActionEnabled(selectedItem != null)
      }
    }
  }

  override fun getView(): JComponent {
    return wrapper
  }

  override fun doClone(checkoutListener: CheckoutProvider.Listener) {}

  override fun doValidateAll(): List<ValidationInfo> {
    return emptyList()
  }

  override fun onComponentSelected() {
    dialogStateListener.onOkActionNameChanged(DvcsBundle.message("clone.button"))
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return searchField
  }
}