// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.rhizome

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.vcs.git.shared.rhizome.repository.GitRepositoryEntity
import com.intellij.vcs.git.shared.rhizome.repository.GitRepositoryFavoriteRefsEntity
import com.intellij.vcs.git.shared.rhizome.repository.GitRepositoryStateEntity

internal class GitEntityTypeProvider : EntityTypeProvider {
  override fun entityTypes() = listOf(
    GitRepositoryEntity,
    GitRepositoryStateEntity,
    GitRepositoryFavoriteRefsEntity,
  )
}
