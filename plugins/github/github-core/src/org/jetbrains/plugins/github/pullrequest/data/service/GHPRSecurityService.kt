// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

@ApiStatus.Internal
interface GHPRSecurityService {
  val ghostUser: GHUser
  val account: GithubAccount
  val currentUser: GHUser

  fun isCurrentUser(user: GithubUser): Boolean

  fun currentUserHasPermissionLevel(level: GHRepositoryPermissionLevel): Boolean

  fun isMergeAllowed(): Boolean
  fun isRebaseMergeAllowed(): Boolean
  fun isSquashMergeAllowed(): Boolean

  fun isMergeForbiddenForProject(): Boolean
}