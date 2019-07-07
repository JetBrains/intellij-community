// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.vcs.commit.commitProperty
import java.util.*

private val COMMIT_AUTHOR_KEY = Key.create<String>("Git.Commit.Author")
private val COMMIT_AUTHOR_DATE_KEY = Key.create<Date>("Git.Commit.AuthorDate")
private val IS_SIGN_OFF_COMMIT_KEY = Key.create<Boolean>("Git.Commit.IsSignOff")
private val IS_COMMIT_RENAMES_SEPARATELY_KEY = Key.create<Boolean>("Git.Commit.IsCommitRenamesSeparately")

internal var CommitContext.commitAuthor: String? by commitProperty(COMMIT_AUTHOR_KEY, null)
internal var CommitContext.commitAuthorDate: Date? by commitProperty(COMMIT_AUTHOR_DATE_KEY, null)
internal var CommitContext.isSignOffCommit: Boolean by commitProperty(IS_SIGN_OFF_COMMIT_KEY)
internal var CommitContext.isCommitRenamesSeparately: Boolean by commitProperty(IS_COMMIT_RENAMES_SEPARATELY_KEY)