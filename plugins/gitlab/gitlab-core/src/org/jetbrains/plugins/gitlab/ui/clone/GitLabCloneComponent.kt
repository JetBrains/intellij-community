// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.ComponentStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneLoginViewModel
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneRepositoriesViewModel
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent

internal class GitLabCloneComponent(
  private val project: Project,
  parentCs: CoroutineScope,
  private val vm: GitLabCloneViewModel,
) : VcsCloneDialogExtensionComponent() {
  private val cs: CoroutineScope = parentCs.childScope(javaClass.name, Dispatchers.Main)

  private val wrapper: Wrapper = Wrapper().apply {
    bindContentIn(cs, vm.panelVm) { panelVm ->
      val innerCs = this
      when (panelVm) {
        is GitLabCloneLoginViewModel -> {
          val titlePanel = simplePanel().apply {
            @Suppress("DialogTitleCapitalization")
            val title = JBLabel(GitLabBundle.message("clone.dialog.login.title"), ComponentStyle.LARGE).apply {
              font = JBFont.label().biggerOn(5.0f)
            }
            addToLeft(title)
            border = JBEmptyBorder(UIUtil.getRegularPanelInsets())
          }
          val centralPanel = GitLabCloneLoginComponentFactory.create(innerCs, panelVm, vm)

          VerticalListPanel().apply {
            border = JBEmptyBorder(UIUtil.getRegularPanelInsets())
            add(titlePanel)
            add(centralPanel)
          }
        }
        is GitLabCloneRepositoriesViewModel -> GitLabCloneRepositoriesComponentFactory.create(
          project, innerCs, panelVm, vm
        ).also { panel ->
          panel.registerValidators(innerCs.nestedDisposable())

          innerCs.launchNow {
            panelVm.selectedUrl.collectLatest { selectedUrl ->
              val isUrlSelected = selectedUrl != null
              dialogStateListener.onOkActionEnabled(isUrlSelected)
            }
          }

          innerCs.launchNow {
            panelVm.listVm.allItems.collectLatest {
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
    this.vm.doClone(checkoutListener)
  }

  override fun doValidateAll(): List<ValidationInfo> =
    (wrapper.targetComponent as? DialogPanel)?.validationsOnApply?.values?.flatten()?.mapNotNull {
      it.validate()
    } ?: emptyList()

  override fun onComponentSelected() {
    dialogStateListener.onOkActionNameChanged(DvcsBundle.message("clone.button"))
    CollaborationToolsUIUtil.focusPanel(wrapper.targetComponent)
  }
}