// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.util.ui.cloneDialog.VcsCloneWithExtensionAction

class GHCloneRepositoryAction : VcsCloneWithExtensionAction() {
  override fun getExtension(): Class<out VcsCloneDialogExtension> {
    return GHCloneDialogExtension::class.java
  }
}