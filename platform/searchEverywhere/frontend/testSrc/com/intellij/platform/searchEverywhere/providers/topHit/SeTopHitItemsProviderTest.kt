// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.topHit

import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.OptionsTopHitProvider
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AbbreviationManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.presentations.SeBasicItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeOptionActionItemPresentation
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import com.intellij.platform.searchEverywhere.providers.commands.SeCommandItem
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer

private const val ABBREVIATION = "sethtest"

/**
 * [SeTopHitItemsProvider] finds the actions of an abbreviation and the objects of the [SearchTopHitProvider]s.
 *
 * Each test sets the top hit providers of the application, so the result does not depend on the installed plugins.
 */
@TestApplication
class SeTopHitItemsProviderTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project: Project get() = projectFixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun anAbbreviationFindsOnlyTheAvailableActions() = timeoutRunBlocking {
    setTopHitProviders()
    val availableAction = TestAction("Available top hit")
    val actionWithoutText = TestAction(text = null)
    registerAbbreviation("SeTopHitTest.Available", availableAction)
    registerAbbreviation("SeTopHitTest.WithoutText", actionWithoutText)

    withProvider(isHost = true) { provider ->
      assertEquals(listOf<Any>(availableAction), provider.search(ABBREVIATION).map { it.rawObject })
    }
  }

  @Test
  fun aTopHitProviderGivesItsObjectsAfterTheActionsOfTheAbbreviation() = timeoutRunBlocking {
    val action = TestAction("Abbreviated top hit")
    registerAbbreviation("SeTopHitTest.Abbreviated", action)
    val option = TestBooleanOption("Top hit option:", isEnabled = true)
    setTopHitProviders(TestTopHitProvider(ABBREVIATION, option))

    withProvider(isHost = true) { provider ->
      assertEquals(listOf(action, option), provider.search(ABBREVIATION).map { it.rawObject })
    }
  }

  @Test
  fun anItemOfTheClientProviderHasAHigherWeight() = timeoutRunBlocking {
    setTopHitProviders(TestTopHitProvider("needle", TestBooleanOption("Needle", isEnabled = true)))

    val hostItem = withProvider(isHost = true) { provider ->
      assertEquals(SeProviderIdUtils.TOP_HIT_HOST_ID, provider.id)
      provider.search("needle").single()
    }
    val clientItem = withProvider(isHost = false) { provider ->
      assertEquals(SeProviderIdUtils.TOP_HIT_ID, provider.id)
      provider.search("needle").single()
    }

    assertEquals(SeTopHitItemsProvider.TOP_HIT_WEIGHT, hostItem.weight())
    assertEquals(SeTopHitItemsProvider.TOP_HIT_WEIGHT + 1, clientItem.weight())
  }

  @Test
  fun aCommandWithoutAnArgumentFindsNothing() = timeoutRunBlocking {
    val topHitProvider = TestTopHitProvider("/editor", TestBooleanOption("Needle", isEnabled = true))
    setTopHitProviders(topHitProvider)

    withProvider(isHost = true) { provider ->
      assertEquals(emptyList<SeItem>(), provider.search("/editor"))
      assertEquals(emptyList<String>(), topHitProvider.patterns, "the provider must not ask the top hit providers")

      assertEquals(emptyList<SeItem>(), provider.search("/editor needle"))
      assertEquals(listOf("/editor needle"), topHitProvider.patterns)
    }
  }

  @Test
  fun anEmptyQueryFindsNothing() = timeoutRunBlocking {
    val topHitProvider = TestTopHitProvider("", TestBooleanOption("Needle", isEnabled = true))
    setTopHitProviders(topHitProvider)

    withProvider(isHost = true) { provider ->
      assertEquals(emptyList<SeItem>(), provider.search(""))
      assertEquals(emptyList<String>(), topHitProvider.patterns)
    }
  }

  @Test
  fun aSelectedBooleanOptionChangesItsStateAndKeepsThePopupOpen() = timeoutRunBlocking {
    val option = TestBooleanOption("Show needles:", isEnabled = false)
    setTopHitProviders(TestTopHitProvider("needle", option))

    withProvider(isHost = true) { provider ->
      val item = provider.search("needle").single()
      val presentation = item.presentation() as SeOptionActionItemPresentation
      assertEquals("Show needles", presentation.text, "the text has no trailing colon")
      assertEquals(false, presentation.commonData.switcherState)

      assertFalse(provider.itemSelected(item, 0, "needle"), "a boolean option keeps the popup open")

      assertTrue(option.isOptionEnabled)
      val updatedPresentation = item.presentation() as SeOptionActionItemPresentation
      assertEquals(true, updatedPresentation.commonData.switcherState, "the popup asks the item again for the new state")
    }
  }

  @Test
  fun aLongOptionTextEndsWithAnEllipsis() = timeoutRunBlocking {
    val text = "Show needles " + "x".repeat(60)
    setTopHitProviders(TestTopHitProvider("needle", TestBooleanOption(text, isEnabled = false)))

    withProvider(isHost = true) { provider ->
      val presentation = provider.search("needle").single().presentation() as SeOptionActionItemPresentation
      assertEquals(text.take(60) + "...", presentation.text)
    }
  }

  @Test
  fun anActionHasTheTextOfItsTemplatePresentation() = timeoutRunBlocking {
    val action = TestAction("Needle action")
    setTopHitProviders(TestTopHitProvider("needle", action))

    withProvider(isHost = true) { provider ->
      val presentation = provider.search("needle").single().presentation() as SeBasicItemPresentation
      assertEquals("Needle action", presentation.text)
    }
  }

  @Test
  fun anOptionsProviderTopHitIsACommandItem() = timeoutRunBlocking {
    setTopHitProviders(TestTopHitProvider("needle", TestOptionsTopHitProvider("gamma")))

    withProvider(isHost = true) { provider ->
      val item = provider.search("needle").single() as SeCommandItem
      assertEquals("gamma", item.commandInfo.command)
      assertEquals(SeProviderIdUtils.TOP_HIT_HOST_ID, item.commandInfo.providerId)
      assertEquals("/gamma", item.presentation().text, "the selection of a command item sets the search text to this text")
    }
  }

  @Test
  fun theCommandsAreTheIdsOfTheOptionsProviders() = timeoutRunBlocking {
    setTopHitProviders(TestOptionsProvider("alpha"), TestTopHitProvider("needle"), TestOptionsProvider("beta"), TestOptionsProvider("alpha"))

    withProvider(isHost = true) { provider ->
      val commands = provider.getSupportedCommands()

      assertEquals(listOf("alpha", "beta"), commands.map { it.command }, "each id gives one command, in the order of the providers")
      assertTrue(commands.all { it.providerId == SeProviderIdUtils.TOP_HIT_HOST_ID }, commands.toString())
    }
  }

  private fun setTopHitProviders(vararg providers: SearchTopHitProvider) {
    ExtensionTestUtil.maskExtensions(SearchTopHitProvider.EP_NAME, providers.toList(), disposable)
  }

  /**
   * Registers [action] with [actionId] and gives it the [ABBREVIATION] until the end of the test.
   */
  private fun registerAbbreviation(actionId: String, action: AnAction) {
    val actionManager = ActionManager.getInstance()
    val abbreviationManager = AbbreviationManager.getInstance()
    actionManager.registerAction(actionId, action)
    abbreviationManager.register(ABBREVIATION, actionId)
    Disposer.register(disposable) {
      abbreviationManager.remove(ABBREVIATION, actionId)
      actionManager.unregisterAction(actionId)
    }
  }

  private suspend fun <T> withProvider(isHost: Boolean, block: suspend (SeTopHitItemsProvider) -> T): T {
    val provider = SeTopHitItemsProvider(isHost, project, contextComponent = null, displayName = "Top Hit")
    try {
      return block(provider)
    }
    finally {
      Disposer.dispose(provider)
    }
  }

  private suspend fun SeTopHitItemsProvider.search(query: String): List<SeItem> {
    val filter = SeEverywhereFilterImpl(isAllTab = true, isEverywhere = false, disabledProviderIds = emptyList())
    val items = mutableListOf<SeItem>()
    collectItems(SeParams(query, filter.toState())) { item ->
      items.add(item)
      true
    }
    return items
  }
}

private class TestAction(text: String?) : AnAction(text), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {}
}

private class TestBooleanOption(option: String, isEnabled: Boolean) : BooleanOptionDescription(option, null) {
  private var isEnabled = isEnabled

  override fun isOptionEnabled(): Boolean = isEnabled

  override fun setOptionState(enabled: Boolean) {
    isEnabled = enabled
  }
}

/**
 * Gives [topHits] for the pattern [pattern] and keeps each pattern that it gets.
 */
private class TestTopHitProvider(private val pattern: String, private vararg val topHits: Any) : SearchTopHitProvider {
  val patterns: MutableList<String> = mutableListOf()

  override fun consumeTopHits(pattern: String, collector: Consumer<Any>, project: Project?) {
    patterns.add(pattern)
    if (pattern == this.pattern) topHits.forEach(collector::accept)
  }
}

private class TestOptionsProvider(private val id: String) : OptionsSearchTopHitProvider.ApplicationLevelProvider {
  override fun getId(): String = id
  override fun getOptions(): Collection<OptionDescription> = emptyList()
}

private class TestOptionsTopHitProvider(private val id: String) : OptionsTopHitProvider() {
  override fun getId(): String = id
}
