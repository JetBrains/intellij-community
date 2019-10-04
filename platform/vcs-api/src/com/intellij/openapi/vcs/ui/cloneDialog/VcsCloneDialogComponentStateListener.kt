// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui.cloneDialog

import java.util.*

/**
 * Informs the parent dialog about need to update its state regarding to the state of internal component
 */
interface VcsCloneDialogComponentStateListener : EventListener {
  fun onOkActionNameChanged(name: String)

  fun onOkActionEnabled(enabled: Boolean)

  fun onListItemChanged()
}