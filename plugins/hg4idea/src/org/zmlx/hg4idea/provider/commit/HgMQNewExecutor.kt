// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider.commit

import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitSession
import org.jetbrains.annotations.Nls
import org.zmlx.hg4idea.HgBundle

//todo:should be moved to create patch dialog as an EP -> create patch with...  MQ
class HgMQNewExecutor : CommitExecutor {
  @Nls
  override fun getActionText(): String = HgBundle.message("action.hg4idea.QNew")

  override fun createCommitSession(commitContext: CommitContext): CommitSession {
    commitContext.isMqNewPatch = true
    return CommitSession.VCS_COMMIT
  }
}