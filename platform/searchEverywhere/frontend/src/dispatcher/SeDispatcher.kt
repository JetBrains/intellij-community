// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.dispatcher

import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.api.SeItemDataProvider
import fleet.kernel.DurableRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.annotations.ApiStatus

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Internal
class SeDispatcher(
  private val providers: Collection<SeItemDataProvider>,
  private val providersAndLimits: Map<SeProviderId, Int>,
) {
  fun getItems(sessionRef: DurableRef<SeSessionEntity>,
               params: SeParams,
               alreadyFoundResults: List<SeItemData>): Flow<SeItemData> {

    val accumulator = SeResultsAccumulator(providersAndLimits, alreadyFoundResults)

    return providers.asFlow().flatMapMerge { provider ->
      provider.getItems(params).mapNotNull {
        val event = accumulator.add(it)
        when {
          event.isAdded || event.isReplaced -> it
          else -> null
        }
      }
    }
  }
}