// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.vcs.log.VcsUser
import java.util.*

interface CommitAuthorTracker {
  var commitAuthor: VcsUser?
  var commitAuthorDate: Date?

  fun addCommitAuthorListener(listener: CommitAuthorListener, parent: Disposable)
}

interface CommitAuthorListener : EventListener {
  fun commitAuthorChanged()
  fun commitAuthorDateChanged()
}