// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.text

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Tells if a PSI element belongs to a commit message.
 *
 * The VCS implementation is in the optional `intellij.grazie.vcs` module.
 * Without this module, Grazie finds no commit messages.
 */
@ApiStatus.Internal
interface CommitMessageDetector {
  fun belongsToCommitMessage(element: PsiElement): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName<CommitMessageDetector>("com.intellij.grazie.commitMessageDetector")

    @JvmStatic
    fun isCommitMessage(element: PsiElement): Boolean {
      return EP_NAME.extensionList.any { it.belongsToCommitMessage(element) }
    }
  }
}
