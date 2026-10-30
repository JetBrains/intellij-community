// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GotoSearchEverywhereTestUtil")
package com.intellij.testFramework.fixtures.impl

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeSessionImpl
import com.intellij.platform.searchEverywhere.impl.SeItemRegistry
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder
import com.intellij.platform.searchEverywhere.toProviderId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

private val SEARCH_TIMEOUT = 1.minutes

/**
 * Returns the elements that the Search Everywhere provider with [providerId] finds for [pattern] in [project].
 *
 * The function opens a Search Everywhere session with [SeProvidersHolder], as the popup does.
 * Then it searches in the separate tab of the provider.
 * [isEverywhere] also searches outside the project, as the everywhere toggle does.
 * The elements keep the order in which the provider sends them, and the function waits for all of them.
 * On the EDT, the wait dispatches the EDT events, so a provider can switch to the EDT.
 */
@TestOnly
@ApiStatus.Internal
@Suppress("DEPRECATION")
fun findSearchEverywhereElements(project: Project, providerId: String, pattern: String, isEverywhere: Boolean): List<Any> {
  return runBlockingInTest {
    val session = SeSessionImpl(UUID.randomUUID().toString())
    val dataContext = SimpleDataContext.getProjectContext(project)
    val initEvent = AnActionEvent.createEvent(dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
    val holder = SeProvidersHolder.initialize(initEvent, project, session, "Test", withAdaptedLegacyContributors = true)
    try {
      val provider = checkNotNull(holder.get(providerId.toProviderId(), isAllTab = false)) {
        "The Search Everywhere session has no provider '$providerId'"
      }
      val filter = SeEverywhereFilterImpl(isAllTab = false, isEverywhere = isEverywhere, disabledProviderIds = emptyList())
      provider.getItems(SeParams(pattern, filter.toState())).toList().map { itemData ->
        val item = checkNotNull(itemData.fetchItemIfExists()) { "The Search Everywhere session has no item ${itemData.uuid}" }
        PSIPresentationBgRendererWrapper.getItem(item.rawObject)
      }
    }
    finally {
      Disposer.dispose(holder)
      SeItemRegistry.getInstance().closeSession(session)
    }
  }
}

@OptIn(DelicateCoroutinesApi::class)
private fun <T> runBlockingInTest(block: suspend CoroutineScope.() -> T): T {
  val scope = GlobalScope.childScope("Search Everywhere search in a test", Dispatchers.Default)
  try {
    return PlatformTestUtil.waitForFuture(scope.async(block = block).asCompletableFuture(), SEARCH_TIMEOUT.inWholeMilliseconds)
  }
  finally {
    scope.cancel()
  }
}
