// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface AmendCommitAware {
  fun isAmendCommitSupported(): Boolean

  @Throws(VcsException::class)
  fun getLastCommitMessage(root: VirtualFile): String?
}