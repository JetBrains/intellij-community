// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageHandler
import com.intellij.find.usages.api.UsageOptions
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.find.usages.impl.AllSearchOptions
import com.intellij.find.usages.impl.buildQuery
import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.ide.hierarchy.LanguageCallHierarchy
import com.intellij.ide.hierarchy.actions.BrowseCallHierarchyAction
import com.intellij.ide.hierarchy.actions.BrowseHierarchyActionBase
import com.intellij.ide.hierarchy.actions.BrowseTypeHierarchyAction
import com.intellij.lang.Language
import com.intellij.model.Pointer
import com.intellij.model.search.SearchRequest
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.productMode.ProductMode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.impl.search.registerSearcherForTesting
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.util.CollectionQuery
import com.intellij.util.Query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import java.time.Duration
import javax.swing.Icon

@TestApplication
class DumbModeFindUsagesAndHierarchyTest {
  private val project = projectFixture()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `the actions are dumb-aware`() {
    for (action in listOf(FindUsagesAction(), ShowUsagesAction(), BrowseCallHierarchyAction(), BrowseTypeHierarchyAction())) {
      assertTrue(action.isDumbAware, action.javaClass.simpleName)
    }
  }

  @Test
  fun `in the dumb mode, find usages is enabled for a dumb-aware usage target`() {
    val presentation = inDumbMode { updateFindUsages(DumbAwareUsageTarget()) }
    assertTrue(presentation.isEnabledAndVisible)
  }

  @Test
  fun `in the dumb mode, find usages in a context menu is disabled for a usage target that is not dumb-aware`() {
    val presentation = inDumbMode { updateFindUsages(TestUsageTarget()) }
    assertFalse(presentation.isEnabled)
  }

  @Test
  fun `in the dumb mode, find usages in the main menu waits for the smart mode for a usage target that is not dumb-aware`() {
    val presentation = inDumbMode { updateInMainMenu(FindUsagesAction(), UsageView.USAGE_TARGETS_KEY, arrayOf(TestUsageTarget())) }
    assertTrue(presentation.isEnabledAndVisible)
  }

  @Test
  fun `in the dumb mode, find usages and show usages in the main menu wait for the smart mode for a search target that is not dumb-aware`() {
    for (action in listOf(FindUsagesAction(), ShowUsagesAction())) {
      val presentation = inDumbMode { updateInMainMenu(action, FindUsagesAction.SEARCH_TARGETS, listOf(TestSearchTarget())) }
      assertTrue(presentation.isEnabledAndVisible, action.javaClass.simpleName)
    }
  }

  @Test
  fun `in Light, find usages in the main menu does not wait for the smart mode, which never comes`() {
    ApplicationManager.getApplication().replaceService(IdeProductMode::class.java, object : IdeProductMode {
      override val currentMode: ProductMode = ProductMode.LIGHT_MONOLITH
    }, disposable)
    val presentation = inDumbMode { updateInMainMenu(FindUsagesAction(), UsageView.USAGE_TARGETS_KEY, arrayOf(TestUsageTarget())) }
    assertFalse(presentation.isEnabled)
  }

  @Test
  fun `in the smart mode, find usages is enabled for a usage target that is not dumb-aware`() {
    waitForSmartMode()
    val presentation = updateFindUsages(TestUsageTarget())
    assertTrue(presentation.isEnabledAndVisible)
  }

  @Test
  fun `in the dumb mode, find usages and show usages are enabled for a dumb-aware search target`() {
    for (action in listOf(FindUsagesAction(), ShowUsagesAction())) {
      val presentation = inDumbMode { update(action, FindUsagesAction.SEARCH_TARGETS, listOf(DumbAwareSearchTarget())) }
      assertTrue(presentation.isEnabledAndVisible, action.javaClass.simpleName)
    }
  }

  @Test
  fun `in the dumb mode, find usages and show usages are disabled for a search target that is not dumb-aware`() {
    for (action in listOf(FindUsagesAction(), ShowUsagesAction())) {
      val presentation = inDumbMode { update(action, FindUsagesAction.SEARCH_TARGETS, listOf(TestSearchTarget())) }
      assertFalse(presentation.isEnabled, action.javaClass.simpleName)
    }
  }

  @Test
  fun `in the dumb mode, the usage search of a dumb-aware target runs the dumb-aware searchers only`() {
    registerSearchers()
    val usages = assertTimeoutPreemptively(Duration.ofSeconds(60), ThrowingSupplier {
      inDumbMode { searchUsages(DumbAwareSearchTarget()) }
    })
    assertEquals(listOf(DUMB_AWARE_USAGE), usages)
  }

  @Test
  fun `in the smart mode, the usage search of a dumb-aware target runs every searcher`() {
    registerSearchers()
    waitForSmartMode()
    assertEquals(setOf(DUMB_AWARE_USAGE, SMART_USAGE), searchUsages(DumbAwareSearchTarget()).toSet())
  }

  @Test
  fun `in the dumb mode, a hierarchy action skips the providers that are not dumb-aware`() {
    val dumbAware = registerHierarchyProviders()
    assertSame(dumbAware, inDumbMode { findHierarchyProvider() })
  }

  @Test
  fun `in the smart mode, a hierarchy action uses every provider`() {
    registerHierarchyProviders()
    waitForSmartMode()
    assertFalse(findHierarchyProvider() is DumbAware)
  }

  private fun <T> inDumbMode(computable: () -> T): T = DumbModeTestUtils.computeInDumbModeSynchronously(project.get(), computable)

  private fun waitForSmartMode() {
    IndexingTestUtil.waitUntilIndexesAreReady(project.get())
  }

  private fun updateFindUsages(target: UsageTarget): Presentation = update(FindUsagesAction(), UsageView.USAGE_TARGETS_KEY, arrayOf(target))

  private fun <T : Any> updateInMainMenu(action: AnAction, key: DataKey<T>, value: T): Presentation {
    return update(action, key, value, ActionPlaces.MAIN_MENU)
  }

  private fun <T : Any> update(action: AnAction, key: DataKey<T>, value: T, place: String = ActionPlaces.EDITOR_POPUP): Presentation {
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project.get())
      .add(key, value)
      .build()
    val uiKind = if (place == ActionPlaces.MAIN_MENU) ActionUiKind.MAIN_MENU else ActionUiKind.POPUP
    val event = AnActionEvent.createEvent(dataContext, null, place, uiKind, null)
    ActionUtil.updateAction(action, event)
    return event.presentation
  }

  private fun registerSearchers() {
    registerSearcherForTesting(UsageSearchParameters::class.java, DumbAwareUsageSearcher(), disposable)
    registerSearcherForTesting(UsageSearchParameters::class.java, SmartUsageSearcher(), disposable)
  }

  /** The usages of [target], also its text occurrences, which need the word index and are skipped in the dumb mode. */
  private fun searchUsages(target: SearchTarget): List<Usage> {
    val options = AllSearchOptions(UsageOptions.createOptions(GlobalSearchScope.allScope(project.get())), textSearch = true)
    return buildQuery(project.get(), target, options).findAll().toList()
  }

  /** Registers a provider that is not dumb-aware before a dumb-aware one, and returns the dumb-aware one. */
  private fun registerHierarchyProviders(): HierarchyProvider {
    val dumbAware = DumbAwareHierarchyProvider()
    LanguageCallHierarchy.INSTANCE.addExplicitExtension(TestLanguage, TestHierarchyProvider(), disposable)
    LanguageCallHierarchy.INSTANCE.addExplicitExtension(TestLanguage, dumbAware, disposable)
    return dumbAware
  }

  private fun findHierarchyProvider(): HierarchyProvider? {
    val dataContext = SimpleDataContext.getProjectContext(project.get())
    return BrowseHierarchyActionBase.findBestHierarchyProvider(LanguageCallHierarchy.INSTANCE, TestElement(), dataContext)
  }

  private open class TestUsageTarget : UsageTarget {
    override fun getName(): String = "word"
    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
      override fun getPresentableText(): String = "word"
      override fun getIcon(unused: Boolean): Icon? = null
    }
    override fun isValid(): Boolean = true
    override fun findUsages() {}
  }

  private class DumbAwareUsageTarget : TestUsageTarget(), DumbAware

  private open class TestSearchTarget : SearchTarget {
    override fun createPointer(): Pointer<out SearchTarget> = Pointer.hardPointer(this)
    override fun presentation(): TargetPresentation = TargetPresentation.builder("word").presentation()
    override val usageHandler: UsageHandler = UsageHandler.createEmptyUsageHandler("word")
    override val textSearchRequests: Collection<SearchRequest> = listOf(SearchRequest.of("word"))
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
  }

  private class DumbAwareSearchTarget : TestSearchTarget(), DumbAware

  private class TestUsage(val name: String) : Usage {
    override fun createPointer(): Pointer<out Usage> = Pointer.hardPointer(this)
    override val declaration: Boolean = false
    override fun toString(): String = name
  }

  private open class SmartUsageSearcher(private val usage: Usage = SMART_USAGE) : UsageSearcher {
    override fun collectSearchRequest(parameters: UsageSearchParameters): Query<out Usage> = CollectionQuery(listOf(usage))
  }

  private class DumbAwareUsageSearcher : SmartUsageSearcher(DUMB_AWARE_USAGE), DumbAware

  private companion object {
    val DUMB_AWARE_USAGE: Usage = TestUsage("dumb-aware")
    val SMART_USAGE: Usage = TestUsage("smart")
  }

  private object TestLanguage : Language("DumbModeFindUsagesAndHierarchyTest")

  private class TestElement : FakePsiElement() {
    override fun getParent(): PsiElement? = null
    override fun getLanguage(): Language = TestLanguage
  }

  private open class TestHierarchyProvider : HierarchyProvider {
    override fun getTarget(dataContext: DataContext): PsiElement? = null
    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser = throw UnsupportedOperationException()
    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {}
  }

  private class DumbAwareHierarchyProvider : TestHierarchyProvider(), DumbAware
}
