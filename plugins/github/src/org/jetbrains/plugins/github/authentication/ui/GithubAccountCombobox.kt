// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import java.awt.event.ItemEvent

class GithubAccountCombobox(accounts: Set<GithubAccount>,
                            defaultAccount: GithubAccount?,
                            onChange: ((GithubAccount) -> Unit)? = null) : ComboBox<GithubAccount>() {
  init {
    val accountList = accounts.toList()
    model = CollectionComboBoxModel(accountList)
    if (defaultAccount != null) {
      selectedItem = defaultAccount
    }
    else {
      selectedIndex = 0
    }
    if (onChange != null) addItemListener { if (it.stateChange == ItemEvent.SELECTED) onChange(model.selectedItem as GithubAccount) }
    isEnabled = accounts.size > 1
  }
}
