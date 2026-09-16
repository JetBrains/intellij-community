// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.asRef
import fleet.kernel.DurableRef
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class SeLegacyContributors(
  val allTab: Map<SeProviderId, SearchEverywhereContributor<Any>>,
  val separateTab: Map<SeProviderId, SearchEverywhereContributor<Any>>,
)

/**
 * Publishes the legacy contributors of a session to a reader in the same process.
 *
 * The frontend uses it in monolith mode to get the actions of the original backend contributor.
 * A reader in another process gets `null` from [get].
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class SeLegacyContributorsRegistry {
  private val contributorsBySession = ConcurrentHashMap<DurableRef<SeSessionEntity>, SeLegacyContributors>()

  /** Publishes [contributors] until [parentDisposable] is disposed. */
  fun register(session: SeSession, contributors: SeLegacyContributors, parentDisposable: Disposable) {
    val sessionRef = session.asRef()
    contributorsBySession[sessionRef] = contributors
    Disposer.register(parentDisposable) {
      contributorsBySession.remove(sessionRef)
    }
  }

  fun get(session: SeSession): SeLegacyContributors? = contributorsBySession[session.asRef()]

  companion object {
    fun getInstance(): SeLegacyContributorsRegistry = service()
  }
}
