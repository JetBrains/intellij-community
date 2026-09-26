// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.async.BatchesLoader
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import kotlin.time.Duration.Companion.seconds


@OptIn(ExperimentalCoroutinesApi::class)
internal class GHPRMentionableUsersProviderImpl(
  parentCs: CoroutineScope,
  repositoryDataService: GHPRRepositoryDataService,
) : GHPRMentionableUsersProvider {

  private val cs = parentCs.childScope(javaClass.name)

  private val mentionableUsersBatchesLoader = BatchesLoader(cs, repositoryDataService.mentionableUsersBatchesFlow(),
                                                            60.seconds)

  override fun getMentionableUsersBatches(): Flow<List<GHUser>> {
    return mentionableUsersBatchesLoader.getBatches()
  }
}