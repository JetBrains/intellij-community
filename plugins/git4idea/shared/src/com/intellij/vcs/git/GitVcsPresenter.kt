// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git

import com.intellij.openapi.vcs.VcsKey
import com.intellij.platform.vcs.impl.shared.VcsPresentation
import com.intellij.platform.vcs.impl.shared.VcsPresenter
import git4idea.i18n.GitBundle

internal class GitVcsPresenter : VcsPresenter {
  override fun getPresentation(vcsKey: VcsKey): VcsPresentation? {
    if (vcsKey.name != GIT_VCS_NAME) return null
    return VcsPresentation(GitBundle.message("git4idea.vcs.name.with.mnemonic"), isWithCustomMenu = true)
  }
}

//The same value as `git4idea.GitVcs.NAME`
private const val GIT_VCS_NAME = "Git"
