// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.text

import com.intellij.find.FindManager
import com.intellij.find.FindSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import com.intellij.platform.searchEverywhere.providers.SeTextFilter
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.intellij.usages.UsageInfo2UsageAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Font

/**
 * [SeTextItemsProvider] finds the lines of the text search.
 *
 * The Text tab keeps its options and its scope. The All tab searches with the default options and keeps the shared state.
 */
@TestApplication
class SeTextItemsProviderTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val moduleFixture = projectFixture.moduleFixture()
  private val sourceRootFixture = moduleFixture.sourceRootFixture()
  private val fileFixture = sourceRootFixture.virtualFileFixture("haystack.txt", "Needle one\nneedle two\n")
  private val uuidFileFixture = sourceRootFixture.virtualFileFixture("uuids.txt", """
    14516b6c-f7e7-444d-b011-3fcbdc71f7bd
    ffc9fc9e-b527-44f6-85ea-eedafea0f30d
    c8a1f836-5198-41b5-a906-793bd9d0d241
  """.trimIndent())

  private val project: Project get() = projectFixture.get()

  private var savedFileMask: String? = null
  private var savedCustomScope: String? = null

  @BeforeEach
  fun saveSettings() {
    savedFileMask = FindSettings.getInstance().fileMask
    savedCustomScope = FindSettings.getInstance().customScope
  }

  @AfterEach
  fun restoreSettings() {
    FindSettings.getInstance().fileMask = savedFileMask
    FindSettings.getInstance().customScope = savedCustomScope
  }

  @Test
  fun theTextTabSearchesWithItsOptionsAndKeepsThemInTheSharedModel() = timeoutRunBlocking {
    fileFixture.get()
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    withProvider { provider ->
      val projectScopeId = provider.getSearchScopesInfo()?.projectScopeId
      val filter = SeTextFilter(projectScopeId, selectedType = null, isCaseSensitive = true, isWholeWordsOnly = false, isRegex = false)

      val lines = provider.search("Needle", filter.toState())

      assertEquals(1, lines.size, "a case-sensitive search must skip the lower-case line: $lines")
      assertTrue(lines.single().contains("Needle one"), lines.single())
      assertTrue(FindManager.getInstance(project).findInProjectModel.isCaseSensitive, "the Text tab keeps its options for Find in Files")
    }
  }

  @Test
  fun theAllTabSearchesWithTheDefaultOptionsAndKeepsTheSharedModel() = timeoutRunBlocking {
    fileFixture.get()
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    val sharedModel = FindManager.getInstance(project).findInProjectModel
    sharedModel.isCaseSensitive = true
    sharedModel.fileFilter = "*.xml"

    withProvider { provider ->
      val filter = SeEverywhereFilterImpl(isAllTab = true, isEverywhere = false, disabledProviderIds = emptyList())

      val lines = provider.search("Needle", filter.toState())

      assertEquals(2, lines.size, "the All tab must ignore the case option and the file mask of the Text tab: $lines")
      assertTrue(sharedModel.isCaseSensitive, "the All tab must not change the options of the Text tab")
      assertEquals("*.xml", sharedModel.fileFilter, "the All tab must not change the file mask of the Text tab")
    }
  }

  @Test
  fun aNewSessionStartsOnTheScopeThatTheUserSelected() = timeoutRunBlocking {
    fileFixture.get()
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    val selectedScopeName = withProvider { provider ->
      val info = checkNotNull(provider.getSearchScopesInfo()) { "the Text tab needs the scope list" }
      val scope = info.scopes.first { !it.isSeparator && it.scopeId != info.projectScopeId && it.scopeId != info.everywhereScopeId }
      val filter = SeTextFilter(scope.scopeId, selectedType = null, isCaseSensitive = false, isWholeWordsOnly = false, isRegex = false)

      provider.search("Needle", filter.toState())
      scope.name
    }

    withProvider { provider ->
      val info = provider.getSearchScopesInfo()!!
      val selectedScope = info.scopes.firstOrNull { it.scopeId == info.selectedScopeId }

      assertEquals(selectedScopeName, selectedScope?.name)
      assertNotEquals(info.projectScopeId, info.selectedScopeId)
    }

    withProvider { provider ->
      val info = provider.getSearchScopesInfo()!!
      val filter = SeTextFilter(info.projectScopeId, selectedType = null, isCaseSensitive = false, isWholeWordsOnly = false, isRegex = false)
      provider.search("Needle", filter.toState())
    }

    withProvider { provider ->
      val info = provider.getSearchScopesInfo()!!
      assertEquals(info.projectScopeId, info.selectedScopeId, "the project scope is not kept, so a new session starts on it")
    }
  }

  @Test
  fun aTextTabSearchClearsTheDirectoryOfTheSharedModel() = timeoutRunBlocking {
    fileFixture.get()
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    val sharedModel = FindManager.getInstance(project).findInProjectModel
    sharedModel.directoryName = "previous-directory"

    withProvider { provider ->
      val projectScopeId = provider.getSearchScopesInfo()?.projectScopeId
      val filter = SeTextFilter(projectScopeId, selectedType = null, isCaseSensitive = false, isWholeWordsOnly = false, isRegex = false)

      provider.search("Needle", filter.toState())

      assertNull(sharedModel.directoryName, "the scope of the Text tab must not intersect with the old directory")
    }
  }

  @Test
  fun aFoundLineShowsTheQueryInBold() = timeoutRunBlocking {
    uuidFileFixture.get()
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    withProvider { provider ->
      val filter = SeEverywhereFilterImpl(isAllTab = true, isEverywhere = false, disabledProviderIds = emptyList())

      // The provider must compute the presentation itself, because the usage does not compute it in tests.
      val items = withContext(Dispatchers.Default) {
        var items = emptyList<SeTextSearchItem>()
        UsageInfo2UsageAdapter.disableAutomaticPresentationCalculationInTests {
          items = runBlocking { provider.searchItems("eedafea0f30d", filter.toState()) }
        }
        items
      }

      val chunk = items.single().item.presentation.text.single { it.text == "eedafea0f30d" }
      assertEquals(Font.BOLD, chunk.attributes.fontType)
    }
  }

  @Test
  fun anEmptyQueryFindsNothing() = timeoutRunBlocking {
    fileFixture.get()

    withProvider { provider ->
      val filter = SeEverywhereFilterImpl(isAllTab = true, isEverywhere = false, disabledProviderIds = emptyList())
      assertEquals(emptyList<String>(), provider.search("", filter.toState()))
    }
  }

  private suspend fun <T> withProvider(block: suspend (SeTextItemsProvider) -> T): T {
    val provider = SeTextItemsProvider(project, psiContext = null)
    try {
      return block(provider)
    }
    finally {
      Disposer.dispose(provider)
    }
  }

  private suspend fun SeTextItemsProvider.search(query: String, filter: SeFilterState): List<String> =
    searchItems(query, filter).map { it.item.presentableText }

  private suspend fun SeTextItemsProvider.searchItems(query: String, filter: SeFilterState): List<SeTextSearchItem> {
    val items = mutableListOf<SeTextSearchItem>()
    collectItems(SeParams(query, filter)) { item ->
      items.add(item as SeTextSearchItem)
      true
    }
    return items
  }
}
