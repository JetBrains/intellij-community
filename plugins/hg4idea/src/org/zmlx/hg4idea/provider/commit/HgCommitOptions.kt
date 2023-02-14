// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider.commit

import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.vcs.commit.commitExecutorProperty

private val IS_CLOSE_BRANCH_KEY = Key.create<Boolean>("Hg.Commit.IsCloseBranch")
private val IS_MQ_NEW_PATCH_KEY = Key.create<Boolean>("Hg.Commit.IsMqNewPatch")
private val IS_COMMIT_SUBREPOSITORIES_KEY = Key.create<Boolean>("Hg.Commit.IsCommitSubrepositories")

internal var CommitContext.isCloseBranch: Boolean by commitExecutorProperty(IS_CLOSE_BRANCH_KEY)
internal var CommitContext.isMqNewPatch: Boolean by commitExecutorProperty(IS_MQ_NEW_PATCH_KEY)
internal var CommitContext.isCommitSubrepositories: Boolean by commitExecutorProperty(IS_COMMIT_SUBREPOSITORIES_KEY)