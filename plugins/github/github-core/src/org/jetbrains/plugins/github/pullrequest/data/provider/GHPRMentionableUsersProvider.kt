// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.util.CodeReviewDomainEntity
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.github.api.data.GHUser

@CodeReviewDomainEntity
interface GHPRMentionableUsersProvider {
  fun getMentionableUsersBatches(): Flow<List<GHUser>>
}
