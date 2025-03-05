// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

interface CommitMode {
  fun useCommitToolWindow(): Boolean
  fun hideLocalChangesTab(): Boolean = false
  fun disableDefaultCommitAction(): Boolean = false

  object PendingCommitMode : CommitMode {
    override fun useCommitToolWindow(): Boolean {
      // Enable 'Commit' toolwindow before vcses are activated
      return true
    }

    override fun disableDefaultCommitAction(): Boolean {
      // Disable `Commit` action until vcses are activated
      return true
    }
  }

  object ModalCommitMode : CommitMode {
    override fun useCommitToolWindow(): Boolean = false
  }

  data class NonModalCommitMode(val isToggleMode: Boolean) : CommitMode {
    override fun useCommitToolWindow(): Boolean = true
  }
}