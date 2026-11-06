// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.actions.searcheverywhere.PersistentSearchEverywhereContributorFilter
import com.intellij.ide.util.TypeVisibilityStateHolder
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeTypeFilterKeys
import com.intellij.platform.searchEverywhere.providers.findSearchScopesInfo
import com.intellij.platform.searchEverywhere.providers.findTypeVisibilityStates
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * A third-party provider gives its scopes and its types to the tab through [SeTargetsFilterProvider].
 *
 * The internal [com.intellij.platform.searchEverywhere.SeSearchScopesProvider] and
 * [com.intellij.platform.searchEverywhere.SeTypeVisibilityStateProvider] give them for a platform provider.
 * The tab must find them in both cases.
 */
@TestApplication
class SeTargetsFilterProviderTest {
  private val projectFixture = projectFixture()

  @Test
  fun theTabFindsTheTypesOfTheTargetProvider() = timeoutRunBlocking {
    withProvider(supportsScopes = false) { provider ->
      val states = provider.findTypeVisibilityStates(SeTypeFilterKeys.DEFAULT)

      assertEquals(listOf("Kotlin" to true, "Java" to false), states?.map { it.name to it.isEnabled })
    }
  }

  @Test
  fun theTabFindsNoTypesForAnUnknownKey() = timeoutRunBlocking {
    withProvider(supportsScopes = false) { provider ->
      assertEquals(emptyList<SeTypeVisibilityStatePresentation>(), provider.findTypeVisibilityStates("unknown"))
    }
  }

  @Test
  fun theTabFindsTheScopesOfTheTargetProvider() = timeoutRunBlocking {
    withProvider(supportsScopes = true) { provider ->
      val scopes = provider.findSearchScopesInfo()

      assertNotNull(scopes, "a target provider that supports scopes must give them to the tab")
      assertNotNull(scopes!!.projectScopeId, "the scope chooser needs the project scope")
    }
  }

  @Test
  fun theTabFindsNoScopesWhenTheTargetProviderHasNone() = timeoutRunBlocking {
    withProvider(supportsScopes = false) { provider ->
      assertNull(provider.findSearchScopesInfo())
    }
  }

  @Test
  fun theTabFindsNothingForAPlainProvider() = timeoutRunBlocking {
    val provider = PlainProvider()

    assertNull(provider.findSearchScopesInfo())
    assertNull(provider.findTypeVisibilityStates(SeTypeFilterKeys.DEFAULT))
  }

  private suspend fun withProvider(supportsScopes: Boolean, block: suspend (SeItemsProvider) -> Unit) {
    val project = projectFixture.get()
    val targetItemsProvider = SeTargetItemsProvider.create<String>(
      project = project,
      dataContext = DataContext.EMPTY_CONTEXT,
      operationDisposable = null,
      label = "Test",
      gotoModelProvider = { project, _, _ -> GotoFileModel(project) },
      typeFilterProvider = {
        mapOf(SeTypeFilterKeys.DEFAULT to PersistentSearchEverywhereContributorFilter(listOf("Kotlin", "Java"),
                                                                                      LanguageVisibility(hidden = setOf("Java")),
                                                                                      { it },
                                                                                      { null }))
      },
      supportsScopes = supportsScopes,
    )
    val provider = TargetsFilterProvider(targetItemsProvider)
    try {
      block(provider)
    }
    finally {
      Disposer.dispose(provider)
    }
  }

  private class LanguageVisibility(hidden: Set<String>) : TypeVisibilityStateHolder<String> {
    private val hidden = hidden.toMutableSet()

    override fun isVisible(type: String): Boolean = type !in hidden

    override fun setVisible(type: String, value: Boolean) {
      if (value) hidden.remove(type) else hidden.add(type)
    }
  }

  private open class PlainProvider : SeItemsProvider {
    override val id: String = "Test"
    override val displayName: String = "Test"
    override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {}
    override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean = false
    override suspend fun canBeShownInFindResults(): Boolean = false
    override fun dispose() {}
  }

  private class TargetsFilterProvider(override val targetItemsProvider: SeTargetItemsProvider<*>) : PlainProvider(), SeTargetsFilterProvider {
    override fun dispose() {
      Disposer.dispose(targetItemsProvider)
    }
  }
}
