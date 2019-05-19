// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import org.jetbrains.annotations.CalledInAwt

interface GithubPullRequestsStateService {
  @CalledInAwt
  fun close(pullRequest: Long)

  @CalledInAwt
  fun reopen(pullRequest: Long)

  @CalledInAwt
  fun merge(pullRequest: Long)

  @CalledInAwt
  fun rebaseMerge(pullRequest: Long)

  @CalledInAwt
  fun squashMerge(pullRequest: Long)
}
