// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.actions.searcheverywhere.PersistentSearchEverywhereContributorFilter
import com.intellij.ide.util.TypeVisibilityStateHolder
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeTypeFilterKeys
import com.intellij.platform.searchEverywhere.providers.getSearchScopesInfo
import com.intellij.platform.searchEverywhere.providers.getTypeVisibilityStates
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

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
      val states = provider.getTypeVisibilityStates(SeTypeFilterKeys.DEFAULT)

      assertEquals(listOf("Kotlin" to true, "Java" to false), states?.map { it.name to it.isEnabled })
    }
  }

  @Test
  fun theTabFindsNoTypesForAnUnknownKey() = timeoutRunBlocking {
    withProvider(supportsScopes = false) { provider ->
      assertEquals(emptyList<SeTypeVisibilityStatePresentation>(), provider.getTypeVisibilityStates("unknown"))
    }
  }

  @Test
  fun theTabFindsTheScopesOfTheTargetProvider() = timeoutRunBlocking {
    withProvider(supportsScopes = true) { provider ->
      val scopes = provider.getSearchScopesInfo()

      assertNotNull(scopes, "a target provider that supports scopes must give them to the tab")
      assertNotNull(scopes!!.projectScopeId, "the scope chooser needs the project scope")
    }
  }

  @Test
  fun theTabFindsNoScopesWhenTheTargetProviderHasNone() = timeoutRunBlocking {
    withProvider(supportsScopes = false) { provider ->
      assertNull(provider.getSearchScopesInfo())
    }
  }

  @Test
  fun theTabFindsTheScopesOfACustomScopeList() = timeoutRunBlocking {
    val project = projectFixture.get()
    val testScope = TestScope(project)
    withProvider(supportsScopes = true, scopesProvider = TestScopesProvider(testScope)) { provider ->
      val scopes = provider.getSearchScopesInfo()!!
      val projectScopeName = GlobalSearchScope.projectScope(project).displayName
      val idByName = scopes.scopes.associate { it.name to it.scopeId }

      assertEquals(listOf(projectScopeName, TestScope.NAME), scopes.scopes.map { it.name })
      assertEquals(idByName[TestScope.NAME], scopes.everywhereScopeId, "the everywhere toggle must use the scope of the custom list")
      assertEquals(idByName[projectScopeName], scopes.projectScopeId, "a new session must start on the scope of the custom list")
    }
  }

  @Test
  fun theSearchRunsInTheScopeThatTheUserSelects() = timeoutRunBlocking {
    val testScope = TestScope(projectFixture.get())
    val searchScope = AtomicReference<SearchScope?>()
    withProvider(supportsScopes = true,
                 scopesProvider = TestScopesProvider(testScope),
                 onModelCreated = { searchScope.set(it?.scope) }) { provider ->
      val scopeId = provider.getSearchScopesInfo()!!.scopes.single { it.name == TestScope.NAME }.scopeId
      val filter = SeTargetsFilter(selectedScopeId = scopeId, isAutoTogglePossible = false, hiddenTypes = emptyMap())

      (provider as SeTargetsFilterProvider).targetItemsProvider.getItemsFlow(SeParams("test", filter.toState())).toList()

      assertSame(testScope, searchScope.get(), "the search must run in the scope that the user selects")
    }
  }

  @Test
  fun theTabFindsNothingForAPlainProvider() = timeoutRunBlocking {
    val provider = PlainProvider()

    assertNull(provider.getSearchScopesInfo())
    assertNull(provider.getTypeVisibilityStates(SeTypeFilterKeys.DEFAULT))
  }

  private suspend fun withProvider(
    supportsScopes: Boolean,
    scopesProvider: SeTargetScopesProvider = SeTargetScopesProvider.DEFAULT,
    onModelCreated: (ScopeDescriptor?) -> Unit = {},
    block: suspend (SeItemsProvider) -> Unit,
  ) {
    val project = projectFixture.get()
    val targetItemsProvider = SeTargetItemsProvider.create<String>(
      project = project,
      dataContext = DataContext.EMPTY_CONTEXT,
      operationDisposable = null,
      label = "Test",
      gotoModelProvider = { project, scope, _ ->
        onModelCreated(scope)
        GotoFileModel(project)
      },
      typeFilterProvider = {
        mapOf(SeTypeFilterKeys.DEFAULT to PersistentSearchEverywhereContributorFilter(listOf("Kotlin", "Java"),
                                                                                      LanguageVisibility(hidden = setOf("Java")),
                                                                                      { it },
                                                                                      { null }))
      },
      supportsScopes = supportsScopes,
      scopesProvider = scopesProvider,
    )
    val provider = TargetsFilterProvider(targetItemsProvider)
    try {
      block(provider)
    }
    finally {
      Disposer.dispose(provider)
    }
  }

  private class TestScope(project: Project) : DelegatingGlobalSearchScope(projectScope(project), NAME) {
    override fun getDisplayName(): String = NAME

    companion object {
      const val NAME: String = "Test scope"
    }
  }

  /** A custom scope list: the project scope, and the [TestScope] for the everywhere toggle. */
  private class TestScopesProvider(private val testScope: TestScope) : SeTargetScopesProvider {
    override fun createDescriptors(project: Project, psiContext: SmartPsiElementPointer<PsiElement?>?): List<ScopeDescriptor> =
      listOf(ScopeDescriptor(GlobalSearchScope.projectScope(project)), ScopeDescriptor(testScope))

    override fun findEverywhereScope(project: Project, descriptors: List<ScopeDescriptor>): ScopeDescriptor? =
      descriptors.firstOrNull { it.scope === testScope }

    override fun findProjectScope(project: Project, descriptors: List<ScopeDescriptor>, everywhere: ScopeDescriptor?): ScopeDescriptor? =
      descriptors.firstOrNull { it.scope !== testScope }
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
