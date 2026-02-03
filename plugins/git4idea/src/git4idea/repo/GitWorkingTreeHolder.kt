// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import git4idea.GitWorkingTree
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface GitWorkingTreeHolder {

  fun getWorkingTrees(): Collection<GitWorkingTree>

  fun reload()

}