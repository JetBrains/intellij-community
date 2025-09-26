// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.layout.selected
import javax.swing.JCheckBox

internal class VcsUpdateInfoScopeFilter(
  project: Project,
  private val myVcsConfiguration: VcsConfiguration,
  parenDisposable: Disposable,
) {
  private val myCheckbox = JCheckBox(VcsBundle.message("settings.filter.update.project.info.by.scope"))
  private val myComboBox = ComboBox<String>()
  private val myNamedScopeHolders = NamedScopesHolder.getAllNamedScopeHolders(project)

  init {
    for (holder in myNamedScopeHolders) {
      holder.addScopeListener(::reset, parenDisposable)
    }
  }

  fun createContent(panel: Panel, manageScopesAction: Runnable) {
    with(panel) {
      row {
        cell(myCheckbox).gap(RightGap.SMALL)
        cell(myComboBox).enabledIf(myCheckbox.selected)
        link(VcsBundle.message("configurable.vcs.manage.scopes")) {
          manageScopesAction.run()
        }
      }

      onIsModified { myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME != scopeFilterName }
      onApply { myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME = scopeFilterName }
      onReset(::reset)
    }
  }

  private fun reset() {
    myComboBox.removeAllItems()
    var selection = false
    for (holder in myNamedScopeHolders) {
      for (scope in holder.editableScopes) {
        val name = scope.scopeId
        myComboBox.addItem(name)
        if (!selection && name == myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME) {
          selection = true
        }
      }
    }
    if (selection) {
      myComboBox.item = myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME
    }
    myCheckbox.setSelected(selection)
  }

  private val scopeFilterName: String?
    get() {
      return if (myCheckbox.isSelected) myComboBox.getItem()
      else null
    }
}
