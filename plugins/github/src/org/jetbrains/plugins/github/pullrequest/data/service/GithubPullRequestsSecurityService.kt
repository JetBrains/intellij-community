// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import org.jetbrains.plugins.github.api.data.GithubUser

interface GithubPullRequestsSecurityService {
  fun isCurrentUser(user: GithubUser): Boolean

  fun isCurrentUserWithPushAccess(): Boolean

  fun isMergeAllowed(): Boolean
  fun isRebaseMergeAllowed(): Boolean
  fun isSquashMergeAllowed(): Boolean

  fun isMergeForbiddenForProject(): Boolean
}