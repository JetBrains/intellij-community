// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.unified.UnifiedPluginInventory
import com.intellij.ide.plugins.unified.UnifiedPluginInventoryItem
import com.intellij.ide.plugins.unified.UnifiedPluginInternalGroup
import com.intellij.ide.plugins.unified.UnifiedPluginLocalDataProvider
import com.intellij.ide.plugins.unified.UnifiedPluginLocalSnapshot
import com.intellij.ide.plugins.unified.UnifiedPluginMarketplaceDataProvider
import com.intellij.ide.plugins.unified.UnifiedPluginMarketplaceFetchResult
import com.intellij.ide.plugins.unified.UnifiedPluginMarketplaceSnapshot
import com.intellij.ide.plugins.unified.UnifiedPluginRepositoryCatalogResult
import com.intellij.ide.plugins.unified.UnifiedPluginRepositoryDataProvider
import com.intellij.ide.plugins.unified.UnifiedPluginUpdateAllCallback
import com.intellij.ide.plugins.unified.UnifiedPluginUpdateAllExecutor
import com.intellij.ide.plugins.unified.UnifiedPluginUpdateAllRequest
import com.intellij.ide.plugins.unified.buildLocalSnapshot
import com.intellij.ide.plugins.unified.buildMarketplaceSnapshot
import com.intellij.ide.plugins.marketplace.statistics.UnifiedPluginSearchStatistics
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerOpenSourceEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchFilterKind
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchQueryShape
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchSection
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchSourceKind
import com.intellij.ide.plugins.newui.CustomPluginRepository
import com.intellij.ide.plugins.newui.CustomPluginRepositoryLoadResult
import com.intellij.ide.plugins.newui.EventHandler.SelectionType
import com.intellij.ide.plugins.newui.LegacyPluginUiHost
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.ide.ui.LafManager
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ComponentUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.SearchFieldWithExtension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.withForcedRespectIsShowingClientProperty
import com.intellij.util.ui.withShowingChanged
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.FocusEvent
import java.awt.image.BufferedImage
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JButton
import javax.swing.JPanel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(30)
internal class UnifiedPluginsPageSessionTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      LafManager.getInstance()
    }
  }

  @Test
  fun `session renders the unified shell and supports compatibility search`(): Unit =
    uiTest {
      val session = createSession("initial query")
      try {
        val content = session.getComponent()
        val center = session.getCenterComponent(Configurable.TopComponentController.EMPTY)
        val searchField = componentsOfType(center, SearchTextField::class.java).single()

        assertThat(session.getComponent()).isSameAs(content)
        assertThat(session.getCenterComponent(Configurable.TopComponentController.EMPTY)).isSameAs(center)
        assertThat(componentsOfType(content, SearchTextField::class.java)).isEmpty()
        assertThat(searchField.text).isEqualTo("initial query")
        assertThat(searchField.textEditor.actionListeners).isNotEmpty()
        assertThat(session.isMarketplaceTabShowing()).isTrue()
        assertThat(session.isInstalledTabShowing()).isTrue()

        session.enableSearch("enabled query")!!.run()
        assertThat(searchField.text).isEqualTo("enabled query")
        session.openMarketplaceTab("marketplace query")
        assertThat(searchField.text).isEqualTo("marketplace query")
        session.openInstalledTab("installed query")
        assertThat(searchField.text).isEqualTo("installed query")

        val centerData = snapshot(center)
        val contentData = snapshot(content)
        assertThat(centerData[PluginManagerConfigurable.PLUGIN_INSTALL_CALLBACK_DATA_KEY]).isNotNull()
        assertThat(contentData[PlatformDataKeys.COPY_PROVIDER]).isNotNull()
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `stale Settings clear does not replace Marketplace navigation`(): Unit =
    uiTest {
      val session = createSession(null)
      try {
        session.enableSearch("Settings query")!!.run()
        val staleClear = session.enableSearch("")!!

        session.openMarketplaceTab("Marketplace query")
        staleClear.run()

        assertThat(searchField(session).text).isEqualTo("Marketplace query")
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `explicit empty Settings request clears Marketplace navigation`(): Unit =
    uiTest {
      val session = createSession(null)
      try {
        session.openMarketplaceTab("Marketplace query")

        session.enableSearch("")!!.run()

        assertThat(searchField(session).text).isEmpty()
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `page input invalidates a pending Settings action`(): Unit =
    uiTest {
      val session = createSession(null)
      try {
        session.enableSearch("Settings query")!!.run()
        val staleClear = session.enableSearch("")!!

        val searchField = searchField(session)
        searchField.text = "Page query"
        staleClear.run()

        assertThat(searchField.text).isEqualTo("Page query")
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `search filter invalidates a pending Settings action`(): Unit =
    uiTest {
      val session = createSession(null)
      try {
        session.enableSearch("Settings query")!!.run()
        val staleClear = session.enableSearch("")!!
        val searchComponent = componentsOfType(
          session.getCenterComponent(Configurable.TopComponentController.EMPTY),
          SearchFieldWithExtension::class.java,
        ).single()
        val filterButton = componentsOfType(searchComponent, ActionButton::class.java).last()
        val filterGroup = filterButton.action as ActionGroup
        val disabledAction = filterGroup.getChildren(TestActionEvent.createTestEvent()).takeLast(5)[2]

        disabledAction.actionPerformed(TestActionEvent.createTestEvent(disabledAction))
        staleClear.run()

        waitForSearchQuery(searchField(session), "Settings query /disabled")
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `Settings can clear its current query`(): Unit =
    uiTest {
      val session = createSession(null)
      try {
        session.enableSearch("Settings query")!!.run()

        session.enableSearch("")!!.run()

        assertThat(searchField(session).text).isEmpty()
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `only the latest pending Settings action applies`(): Unit =
    uiTest {
      val session = createSession(null)
      try {
        val first = session.enableSearch("First query")!!
        val second = session.enableSearch("Second query")!!

        first.run()
        assertThat(searchField(session).text).isEmpty()

        second.run()
        assertThat(searchField(session).text).isEqualTo("Second query")
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `empty Settings request invalidates a pending action`(): Unit =
    uiTest {
      val session = createSession(null)
      try {
        val pending = session.enableSearch("Pending query")!!

        assertThat(session.enableSearch("")).isNull()
        pending.run()

        assertThat(searchField(session).text).isEmpty()
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `disposed session ignores a pending Settings action`(): Unit =
    uiTest {
      val session = createSession(null)
      val searchField = searchField(session)
      val pending = session.enableSearch("Pending query")!!

      Disposer.dispose(session)
      pending.run()

      assertThat(searchField.text).isEmpty()
    }

  @Test
  fun `session reports each non-empty search after all sources settle`(): Unit =
    uiTest {
      val searches = mutableListOf<Pair<UnifiedPluginSearchStatistics, Int>>()
      val provider = FixedLocalDataProvider(
        UnifiedPluginInventory(listOf(inventoryItem("local.plugin", "Local Plugin")), emptyList())
      )
      val session = createSession(
        searchQuery = "Plugin /vendor:Private",
        provider = provider,
        unifiedSearchLogger = { statistics, searchIndex -> searches.add(statistics to searchIndex) },
      )
      try {
        waitForSearchCount(searches, 1)

        val first = searches.single()
        assertThat(first.second).isEqualTo(0)
        assertThat(first.first.queryShape).isEqualTo(UnifiedPluginSearchQueryShape.TEXT_AND_CONTROLS)
        assertThat(first.first.filterKinds).containsExactly(UnifiedPluginSearchFilterKind.VENDOR)
        assertThat(first.first.sourceKinds).containsExactly(
          UnifiedPluginSearchSourceKind.LOCAL,
          UnifiedPluginSearchSourceKind.INTERNAL,
          UnifiedPluginSearchSourceKind.MARKETPLACE,
          UnifiedPluginSearchSourceKind.CUSTOM_REPOSITORY,
        )
        assertThat(first.first.resultCounts[UnifiedPluginSearchSection.INSTALLED]).isEqualTo(0)

        session.enableSearch("Plugin /tag:Private")!!.run()
        waitForSearchCount(searches, 2)

        assertThat(searches.map { it.second }).containsExactly(0, 1)
        assertThat(searches.last().first.filterKinds).containsExactly(UnifiedPluginSearchFilterKind.TAG)
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `session started identifies the unified page`(@TestDisposable disposable: Disposable): Unit =
    uiTest {
      lateinit var session: UnifiedPluginsPageSession
      val events = FUCollectorTestCase.collectLogEvents(disposable) {
        session = createSession(null)
      }
      try {
        assertThat(events.single { it.event.id == "session.started" }.event.data["isUnifiedPage"]).isEqualTo(true)
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `search and actions stay vertically centered`(): Unit =
    uiTest {
      val session = createSession(null)
      try {
        val header = session.getCenterComponent(Configurable.TopComponentController.EMPTY)
        val searchComponent = componentsOfType(header, SearchFieldWithExtension::class.java).single()
        val updateAllButton = updateAllButton(header)
        updateAllButton.isVisible = true
        updateAllButton.text = "Update All"
        val settingsToolbar = header.components.single { it !== searchComponent && it !is JButton }
        (settingsToolbar as ActionToolbar).updateActionsAsync()
        val settingsButton = componentsOfType(settingsToolbar, ActionButton::class.java).single()
        val layout = header.layout as GridBagLayout
        val searchConstraints = layout.getConstraints(searchComponent)
        val updateAllConstraints = layout.getConstraints(updateAllButton)
        val settingsConstraints = layout.getConstraints(settingsToolbar)
        val searchContent = searchComponent.components.single() as JComponent
        header.setSize(header.preferredSize.width, header.preferredSize.height + 12)

        header.doLayout()

        assertThat(searchContent.border.getBorderInsets(searchContent).top).isZero()
        assertThat(searchContent.border.getBorderInsets(searchContent).bottom).isZero()
        assertThat(searchComponent.height).isEqualTo(searchComponent.preferredSize.height)
        assertThat(updateAllButton.height).isEqualTo(updateAllButton.preferredSize.height)
        assertThat(searchComponent.y).isEqualTo((header.height - searchComponent.height) / 2)
        assertThat(updateAllButton.y).isEqualTo((header.height - updateAllButton.height) / 2)
        assertThat(searchConstraints.gridy).isEqualTo(settingsConstraints.gridy)
        assertThat(updateAllConstraints.gridy).isEqualTo(settingsConstraints.gridy)
        assertThat(searchConstraints.anchor).isEqualTo(GridBagConstraints.CENTER)
        assertThat(updateAllConstraints.anchor).isEqualTo(GridBagConstraints.CENTER)
        assertThat(settingsConstraints.anchor).isEqualTo(GridBagConstraints.WEST)
        assertThat(settingsButton.isFocusable).isTrue()
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `settings action shows keyboard focus outline`(): Unit =
    uiTest {
      val session = createSession(null)
      try {
        val header = session.getCenterComponent(Configurable.TopComponentController.EMPTY)
        val settingsToolbar = header.components.single { it !is SearchFieldWithExtension && it !is JButton }
        (settingsToolbar as ActionToolbar).updateActionsAsync()
        val settingsButton = componentsOfType(settingsToolbar, ActionButton::class.java).single()
        settingsButton.size = settingsButton.preferredSize
        val contentBorder = settingsButton.border
        val contentInsets = contentBorder.getBorderInsets(settingsButton)

        val traversalFocusEvent = FocusEvent(
          settingsButton,
          FocusEvent.FOCUS_GAINED,
          false,
          null,
          FocusEvent.Cause.TRAVERSAL_FORWARD,
        )
        settingsButton.focusListeners.forEach { it.focusGained(traversalFocusEvent) }

        val focusBorder = settingsButton.border
        assertThat(focusBorder).isNotSameAs(contentBorder)
        assertThat(focusBorder.getBorderInsets(settingsButton)).isEqualTo(contentInsets)
        val image = paintedBorder(settingsButton)
        val focusColor = JBUI.CurrentTheme.ActionButton.focusedBorder()
        val focusGap = JBUI.scale(1)
        val focusWidth = JBUI.scale(2)
        val centerX = settingsButton.width / 2
        assertThat(Color(image.getRGB(centerX, 0), true).rgb).isNotEqualTo(focusColor.rgb)
        assertThat(Color(image.getRGB(centerX, focusGap), true).rgb).isEqualTo(focusColor.rgb)
        assertThat(Color(image.getRGB(centerX, focusGap + focusWidth - 1), true).rgb).isEqualTo(focusColor.rgb)
        assertThat(Color(image.getRGB(centerX, focusGap + focusWidth), true).rgb).isNotEqualTo(focusColor.rgb)

        settingsButton.focusListeners.forEach { it.focusLost(FocusEvent(settingsButton, FocusEvent.FOCUS_LOST)) }
        assertThat(settingsButton.border).isSameAs(contentBorder)

        val mouseFocusEvent = FocusEvent(settingsButton, FocusEvent.FOCUS_GAINED, false, null, FocusEvent.Cause.MOUSE_EVENT)
        settingsButton.focusListeners.forEach { it.focusGained(mouseFocusEvent) }
        assertThat(settingsButton.border).isSameAs(contentBorder)
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `search keeps its maximum with internal controls and shrinks for header actions`(): Unit =
    uiTest {
      val session = createSession(null)
      try {
        val header = session.getCenterComponent(Configurable.TopComponentController.EMPTY)
        val searchComponent = componentsOfType(header, SearchFieldWithExtension::class.java).single()
        updateAllButton(header).apply {
          isVisible = true
          text = "Updating 13/35..."
        }

        waitForSearchControlCount(searchComponent, 1)
        assertAdaptiveSearchWidth(header, searchComponent)

        session.enableSearch("kotlin")!!.run()
        waitForSearchControlCount(searchComponent, 2)
        assertAdaptiveSearchWidth(header, searchComponent)

        session.enableSearch("")!!.run()
        waitForSearchControlCount(searchComponent, 1)
        assertAdaptiveSearchWidth(header, searchComponent)
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `Update All button counts prepared executor targets`(): Unit =
    uiTest {
      val updates = MutableSharedFlow<PluginUpdatesEvent>(replay = 1)
      val executor = RecordingUpdateAllExecutor()
      val first = PluginDto("First", PluginId.getId("first"))
      val second = PluginDto("Second", PluginId.getId("second"))
      val disabled = PluginDto("Disabled", PluginId.getId("disabled"))
      val pluginNod = PluginDto("Plugin NOD", PluginId.getId("plugin.nod"))
      val provider = FixedLocalDataProvider(
        UnifiedPluginInventory(
          installedPlugins = listOf(
            inventoryItem("first", "First"),
            inventoryItem("second", "Second"),
            inventoryItem("disabled", "Disabled"),
          ),
          bundledPlugins = emptyList(),
        )
      )
      val session = createSession(
        searchQuery = null,
        provider = provider,
        pluginUpdates = updates,
        updateAllExecutor = executor,
      )
      try {
        val header = session.getCenterComponent(Configurable.TopComponentController.EMPTY)
        val button = updateAllButton(header)
        assertThat(button.isVisible).isFalse()

        updates.emit(PluginUpdatesEvent(listOf(first, second), listOf(disabled), listOf(pluginNod)))
        waitForButton(button, "Update All", enabled = true)
        assertThat(button.icon).isNull()
        button.doClick()
        waitForButton(button, "Updating 0/2...", enabled = false)
        assertThat(button.icon).isSameAs(AnimatedIcon.Default.INSTANCE)
        assertThat(executor.requests.single().updates).containsExactly(first, second)

        updates.emit(PluginUpdatesEvent(listOf(second), emptyList(), emptyList()))
        delay(50.milliseconds)
        assertThat(button.text).isEqualTo("Updating 0/2...")
        executor.callbacks.single().prepared(first.pluginId, restartRequired = false)
        waitForButton(button, "Updating 1/2...", enabled = false)
        executor.callbacks.single().prepared(second.pluginId, restartRequired = false)
        waitForButton(button, "Updated", enabled = false)
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `Update All waits for the local plugin inventory`(): Unit =
    uiTest {
      val inventoryReady = CompletableDeferred<Unit>()
      val update = PluginDto("Plugin update", PluginId.getId("plugin.id"))
      val updates = MutableSharedFlow<PluginUpdatesEvent>(replay = 1)
      val provider = FixedLocalDataProvider(
        inventory = UnifiedPluginInventory(
          installedPlugins = listOf(inventoryItem("plugin.id", "Installed plugin")),
          bundledPlugins = emptyList(),
        ),
        enrichmentReadiness = inventoryReady::await,
      )
      val session = createSession(null, provider, pluginUpdates = updates)
      try {
        val button = updateAllButton(session.getCenterComponent(Configurable.TopComponentController.EMPTY))

        updates.emit(PluginUpdatesEvent(listOf(update), emptyList(), emptyList()))
        delay(50.milliseconds)

        assertThat(button.isVisible).isFalse()

        inventoryReady.complete(Unit)
        waitForButton(button, "Update All", enabled = true)
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `unmodified lifecycle calls and repeated disposal are safe`(): Unit =
    uiTest {
      val session = createSession(null)

      assertThat(session.isModified()).isFalse()
      session.setInstallSource(null)
      session.select(emptyList())
      session.reset()
      session.cancel()
      session.scheduleApply()
      Disposer.dispose(session)
      Disposer.dispose(session)

      assertThat(session.isModified()).isFalse()
    }

  @Test
  fun `session renders and filters installed and bundled rows`(): Unit =
    uiTest {
      val installed = inventoryItem("custom.plugin", "Custom Plugin")
      val bundled = inventoryItem("bundled.plugin", "Bundled Plugin", bundled = true)
      val provider = FixedLocalDataProvider(UnifiedPluginInventory(listOf(installed), listOf(bundled)))
      val session = createSession(null, provider)
      try {
        val content = session.getComponent()
        waitForPluginIds(content, setOf("custom.plugin", "bundled.plugin"))

        session.enableSearch("Bundled")!!.run()

        waitForPluginIds(content, setOf("bundled.plugin"))
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `page replays source state when it first becomes visible`(): Unit =
    uiTest {
      val inventoryEnriched = CompletableDeferred<Unit>()
      val provider = FixedLocalDataProvider(
        UnifiedPluginInventory(
          installedPlugins = listOf(inventoryItem("installed.plugin", "Installed Plugin")),
          bundledPlugins = emptyList(),
        ),
        onEnrich = { inventoryEnriched.complete(Unit) },
      )
      val session = createSession(null, provider, showPage = false)
      try {
        val content = session.getComponent()
        inventoryEnriched.await()
        delay(100.milliseconds)
        assertThat(componentsOfType(content, ListPluginComponent::class.java)).isEmpty()

        showComponent(content)

        waitForPluginIds(content, setOf("installed.plugin"))
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `Installed navigation does not search Marketplace`(): Unit =
    uiTest {
      val provider = FixedLocalDataProvider(
        UnifiedPluginInventory(listOf(inventoryItem("local.plugin", "Local Plugin")), emptyList())
      )
      val marketplaceProvider = FixedMarketplaceDataProvider()
      val session = createSession(null, provider, marketplaceProvider)
      try {
        session.openInstalledTabWithSearch("Local Plugin")

        waitForPluginIds(session.getComponent(), setOf("local.plugin"))
        assertThat(marketplaceProvider.searchQueries).isEmpty()
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `Installed Settings search does not search Marketplace`(): Unit =
    uiTest {
      val provider = FixedLocalDataProvider(
        UnifiedPluginInventory(listOf(inventoryItem("local.plugin", "Local Plugin")), emptyList())
      )
      val marketplaceProvider = FixedMarketplaceDataProvider()
      val session = createSession(null, provider, marketplaceProvider)
      try {
        session.enableSearch("Local Plugin", ignoreTagMarketplaceTab = true)!!.run()

        waitForPluginIds(session.getComponent(), setOf("local.plugin"))
        assertThat(marketplaceProvider.searchQueries).isEmpty()
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `Installed invalid search includes a plugin after local enrichment`(): Unit =
    uiTest {
      val enrichmentRequested = CompletableDeferred<Unit>()
      val enrichmentReady = CompletableDeferred<Unit>()
      val invalidPlugin = inventoryItem("invalid.plugin", "Invalid Plugin")
      val provider = FixedLocalDataProvider(
        inventory = UnifiedPluginInventory(listOf(invalidPlugin), emptyList()),
        enrichmentReadiness = enrichmentReady::await,
        onEnrichmentRequested = { enrichmentRequested.complete(Unit) },
        errors = mapOf(invalidPlugin.model.pluginId to listOf(HtmlChunk.text("Invalid plugin"))),
      )
      val session = createSession(
        searchQuery = null,
        provider = provider,
        initialNavigation = PluginsPageInitialNavigation("/invalid", PluginsPageInitialNavigationTarget.Installed),
      )
      try {
        enrichmentRequested.await()

        assertThat(searchField(session).text).isEqualTo("/invalid")
        assertThat(componentsOfType(session.getComponent(), ListPluginComponent::class.java)).isEmpty()

        enrichmentReady.complete(Unit)

        waitForPluginIds(session.getComponent(), setOf("invalid.plugin"))
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `plugin source enrichment waits for readiness without blocking the page`(): Unit =
    uiTest {
      val pluginStatesReady = CompletableDeferred<Unit>()
      val sessionInitialized = CompletableDeferred<Unit>()
      val allSourcesAwaitSessionInitialization = CompletableDeferred<Unit>()
      val sessionInitializationRequests = AtomicInteger()
      val inventoryLoaded = CompletableDeferred<Unit>()
      val marketplaceSearchStarted = CompletableDeferred<Unit>()
      val localEnrichmentStarted = CompletableDeferred<Unit>()
      val remoteEnrichmentStarted = CompletableDeferred<Unit>()
      val localPlugin = inventoryItem("local.plugin", "Shared Local Plugin")
      val remotePlugin = PluginDto("Shared Remote Plugin", PluginId.getId("remote.plugin"))
      val localProvider = FixedLocalDataProvider(
        inventory = UnifiedPluginInventory(listOf(localPlugin), emptyList()),
        onInventoryLoaded = { inventoryLoaded.complete(Unit) },
        onEnrich = { localEnrichmentStarted.complete(Unit) },
      )
      val marketplaceProvider = FixedMarketplaceDataProvider(
        searches = mapOf("shared" to listOf(remotePlugin)),
        onSearch = { marketplaceSearchStarted.complete(Unit) },
        onEnrich = { remoteEnrichmentStarted.complete(Unit) },
      )
      val session = createSession(
        searchQuery = null,
        provider = localProvider,
        marketplaceProvider = marketplaceProvider,
        pluginStatesReadiness = pluginStatesReady::await,
        sessionInitializationReadiness = {
          if (sessionInitializationRequests.incrementAndGet() == 2) {
            allSourcesAwaitSessionInitialization.complete(Unit)
          }
          sessionInitialized.await()
        },
      )
      try {
        val content = session.getComponent()
        val header = session.getCenterComponent(Configurable.TopComponentController.EMPTY)
        val searchField = componentsOfType(header, SearchTextField::class.java).single()

        inventoryLoaded.await()
        session.enableSearch("shared")!!.run()
        marketplaceSearchStarted.await()

        assertThat(searchField.text).isEqualTo("shared")
        assertThat(localEnrichmentStarted.isCompleted).isFalse()
        assertThat(remoteEnrichmentStarted.isCompleted).isFalse()

        pluginStatesReady.complete(Unit)
        allSourcesAwaitSessionInitialization.await()

        assertThat(localEnrichmentStarted.isCompleted).isFalse()
        assertThat(remoteEnrichmentStarted.isCompleted).isFalse()

        sessionInitialized.complete(Unit)

        waitForPluginIds(content, setOf("local.plugin", "remote.plugin"))
        assertThat(localEnrichmentStarted.isCompleted).isTrue()
        assertThat(remoteEnrichmentStarted.isCompleted).isTrue()
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `disposing the page cancels enrichment that waits for plugin states`(): Unit =
    uiTest {
      val pluginStatesReady = CompletableDeferred<Unit>()
      val inventoryLoaded = CompletableDeferred<Unit>()
      val enrichmentStarted = CompletableDeferred<Unit>()
      val provider = FixedLocalDataProvider(
        inventory = UnifiedPluginInventory(listOf(inventoryItem("local.plugin", "Local Plugin")), emptyList()),
        onInventoryLoaded = { inventoryLoaded.complete(Unit) },
        onEnrich = { enrichmentStarted.complete(Unit) },
      )
      val session = createSession(null, provider, pluginStatesReadiness = pluginStatesReady::await)

      inventoryLoaded.await()
      Disposer.dispose(session)
      pluginStatesReady.complete(Unit)
      yield()

      assertThat(enrichmentStarted.isCompleted).isFalse()
    }

  @Test
  fun `session replaces Suggested rows with Marketplace search results`(): Unit =
    uiTest {
      val suggested = PluginDto("Suggested Plugin", PluginId.getId("suggested.plugin"))
      val marketplace = PluginDto("Marketplace Plugin", PluginId.getId("marketplace.plugin"))
      val remoteProvider = FixedMarketplaceDataProvider(
        suggested = listOf(suggested),
        searches = mapOf("kotlin" to listOf(marketplace)),
      )
      val session = createSession(null, marketplaceProvider = remoteProvider)
      try {
        val content = session.getComponent()
        waitForPluginIds(content, setOf("suggested.plugin"))

        session.enableSearch("kotlin")!!.run()

        waitForPluginIds(content, setOf("marketplace.plugin"))
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `suggested compatibility query restores Suggested without Marketplace search`(): Unit =
    uiTest {
      val suggested = PluginDto("Suggested Plugin", PluginId.getId("suggested.plugin"))
      val remoteProvider = FixedMarketplaceDataProvider(suggested = listOf(suggested))
      val session = createSession(null, marketplaceProvider = remoteProvider)
      try {
        val center = session.getCenterComponent(Configurable.TopComponentController.EMPTY)
        val searchField = componentsOfType(center, SearchTextField::class.java).single()
        waitForPluginIds(session.getComponent(), setOf("suggested.plugin"))

        session.openMarketplaceTab("/suggested")
        waitForPluginIds(session.getComponent(), setOf("suggested.plugin"))

        assertThat(searchField.text).isEmpty()
        assertThat(remoteProvider.searchQueries).isEmpty()
        assertThat(remoteProvider.suggestedLoadCount).isGreaterThanOrEqualTo(1)
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `initial Marketplace navigation skips Suggested loading`(): Unit =
    uiTest {
      val searchStarted = CompletableDeferred<Unit>()
      val marketplaceProvider = FixedMarketplaceDataProvider(
        onSearch = { searchStarted.complete(Unit) },
      )
      val session = createSession(
        searchQuery = null,
        marketplaceProvider = marketplaceProvider,
        initialNavigation = PluginsPageInitialNavigation(
          "Marketplace query",
          PluginsPageInitialNavigationTarget.Marketplace,
        ),
      )
      try {
        searchStarted.await()

        assertThat(searchField(session).text).isEqualTo("Marketplace query")
        assertThat(marketplaceProvider.searchQueries).containsExactly("Marketplace query")
        assertThat(marketplaceProvider.suggestedLoadCount).isZero()
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `session renders and filters ordered repository rows`(): Unit =
    uiTest {
      val first = CustomPluginRepository("first", PluginSource.LOCAL)
      val second = CustomPluginRepository("second", PluginSource.LOCAL)
      val repositoryProvider = FixedRepositoryDataProvider(
        repositories = listOf(first, second),
        plugins = mapOf(
          first.id to listOf(PluginDto("Alpha Plugin", PluginId.getId("alpha.plugin"))),
          second.id to listOf(PluginDto("Beta Plugin", PluginId.getId("beta.plugin"))),
        ),
      )
      val session = createSession(null, repositoryProvider = repositoryProvider)
      try {
        val content = session.getComponent()
        session.enableSearch("Plugin")!!.run()
        waitForPluginIds(content, setOf("alpha.plugin", "beta.plugin"))

        session.enableSearch("Beta")!!.run()

        waitForPluginIds(content, setOf("beta.plugin"))
        assertThat(repositoryProvider.repositoryLoadCount).isEqualTo(2)
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `programmatic selection accumulates plugins from separately published sources`(): Unit =
    uiTest {
      val first = CustomPluginRepository("first", PluginSource.LOCAL)
      val second = CustomPluginRepository("second", PluginSource.LOCAL)
      val firstResult = CompletableDeferred<List<PluginUiModel>>()
      val secondResult = CompletableDeferred<List<PluginUiModel>>()
      val firstPlugin = PluginDto("First Plugin", PluginId.getId("first.plugin"))
      val secondPlugin = PluginDto("Second Plugin", PluginId.getId("second.plugin"))
      val repositoryProvider = DeferredRepositoryDataProvider(
        repositories = listOf(first, second),
        results = mapOf(first.id to firstResult, second.id to secondResult),
      )
      val session = createSession(null, repositoryProvider = repositoryProvider)
      try {
        val content = session.getComponent()
        session.enableSearch("Plugin")!!.run()
        session.select(listOf(firstPlugin.pluginId, secondPlugin.pluginId))

        firstResult.complete(listOf(firstPlugin))
        waitForPluginIds(content, setOf("first.plugin"))
        waitForSelectedPluginIds(content, setOf("first.plugin"))

        secondResult.complete(listOf(secondPlugin))
        waitForPluginIds(content, setOf("first.plugin", "second.plugin"))
        waitForSelectedPluginIds(content, setOf("first.plugin", "second.plugin"))
      }
      finally {
        Disposer.dispose(session)
      }
    }

  @Test
  fun `programmatic selection waits for the Internal descriptor`() =
    uiTest {
      val internalGroup = CompletableDeferred<UnifiedPluginInternalGroup?>()
      val localPlugin = inventoryItem("local.plugin", "Local Plugin")
      val internalPlugin = PluginDto("Internal Plugin", PluginId.getId("internal.plugin"))
      val session = createSession(
        searchQuery = null,
        provider = FixedLocalDataProvider(UnifiedPluginInventory(listOf(localPlugin), emptyList())),
        internalGroupLoader = { internalGroup.await() },
        internalLoadingDelay = 10.seconds,
      )
      try {
        val content = session.getComponent()
        waitForPluginIds(content, setOf("local.plugin"))

        session.select(listOf(internalPlugin.pluginId))
        internalGroup.complete(UnifiedPluginInternalGroup("Internal plugins", listOf(internalPlugin)))

        waitForPluginIds(content, setOf("local.plugin", "internal.plugin"))
        waitForSelectedPluginIds(content, setOf("internal.plugin"))
      }
      finally {
        Disposer.dispose(session)
      }
    }

  private fun uiTest(action: suspend CoroutineScope.() -> Unit) {
    withForcedRespectIsShowingClientProperty {
      timeoutRunBlocking(context = Dispatchers.UiWithModelAccess, action = action)
    }
  }

  private fun createSession(
    searchQuery: String?,
    provider: UnifiedPluginLocalDataProvider = FixedLocalDataProvider(UnifiedPluginInventory(emptyList(), emptyList())),
    marketplaceProvider: UnifiedPluginMarketplaceDataProvider = FixedMarketplaceDataProvider(),
    repositoryProvider: UnifiedPluginRepositoryDataProvider = FixedRepositoryDataProvider(),
    pluginStatesReadiness: suspend () -> Unit = {},
    sessionInitializationReadiness: suspend (LegacyPluginUiHost) -> Unit = { it.awaitSessionInitialization() },
    pluginUpdates: Flow<PluginUpdatesEvent> = emptyFlow(),
    internalGroupLoader: suspend () -> UnifiedPluginInternalGroup? = { null },
    internalLoadingDelay: Duration = 100.milliseconds,
    updateAllExecutor: UnifiedPluginUpdateAllExecutor = UnifiedPluginUpdateAllExecutor { _, _ -> },
    unifiedSearchLogger: (UnifiedPluginSearchStatistics, Int) -> Unit = { _, _ -> },
    showPage: Boolean = true,
    initialNavigation: PluginsPageInitialNavigation? = null,
  ): UnifiedPluginsPageSession {
    val session = UnifiedPluginsPageSession(
      initialNavigation,
      PluginManagerOpenSourceEnum.OTHER,
      localDataProviderFactory = { provider },
      repositoryDataProviderFactory = { repositoryProvider },
      marketplaceDataProviderFactory = { _, _ -> marketplaceProvider },
      pluginUpdates = pluginUpdates,
      customizer = null,
      pluginStatesReadiness = pluginStatesReadiness,
      sessionInitializationReadiness = sessionInitializationReadiness,
      internalGroupLoader = internalGroupLoader,
      internalLoadingDelay = internalLoadingDelay,
      updateAllExecutorFactory = { _, _ -> updateAllExecutor },
      unifiedSearchLogger = unifiedSearchLogger,
    )
    if (showPage) showComponent(session.getComponent())
    if (searchQuery != null) session.enableSearch(searchQuery)?.run()
    return session
  }

  private fun showComponent(component: JComponent) {
    val container = JPanel()
    ComponentUtil.forceMarkAsShowing(container, true)
    withShowingChanged { container.add(component) }
  }

  private suspend fun waitForPluginIds(content: Component, expected: Set<String>) {
    withTimeout(5.seconds) {
      while (true) {
        val actual = componentsOfType(content, ListPluginComponent::class.java)
          .mapTo(HashSet()) { it.getPluginModel().pluginId.idString }
        if (actual == expected) return@withTimeout
        delay(10.milliseconds)
      }
    }
  }

  private suspend fun waitForSearchControlCount(searchComponent: JComponent, expected: Int) {
    withTimeout(5.seconds) {
      while (visibleSearchControlCount(searchComponent) != expected) {
        delay(10.milliseconds)
      }
    }
  }

  private suspend fun waitForSearchCount(searches: List<*>, expected: Int) {
    withTimeout(5.seconds) {
      while (searches.size != expected) {
        yield()
      }
    }
  }

  private suspend fun waitForSearchQuery(searchField: SearchTextField, query: String) {
    withTimeout(5.seconds) {
      while (searchField.text != query) {
        yield()
      }
    }
  }

  private fun searchField(session: UnifiedPluginsPageSession): SearchTextField {
    return componentsOfType(
      session.getCenterComponent(Configurable.TopComponentController.EMPTY),
      SearchTextField::class.java,
    ).single()
  }

  private fun visibleSearchControlCount(searchComponent: JComponent): Int {
    return componentsOfType(searchComponent, ActionButton::class.java).count(ActionButton::isVisible)
  }

  private fun assertAdaptiveSearchWidth(header: JComponent, searchComponent: JComponent) {
    val updateAllButton = updateAllButton(header).apply { isVisible = true }
    val settingsToolbar = header.components.single { it !== searchComponent && it !is JButton }
    val layout = header.layout as GridBagLayout
    val searchConstraints = layout.getConstraints(searchComponent)
    val updateAllConstraints = layout.getConstraints(updateAllButton)
    val settingsConstraints = layout.getConstraints(settingsToolbar)
    val maximumWidth = JBUI.scale(340)
    assertThat(updateAllConstraints.gridx).isEqualTo(searchConstraints.gridx + 1)
    assertThat(settingsConstraints.gridx).isEqualTo(updateAllConstraints.gridx + 1)
    assertThat(searchConstraints.fill).isEqualTo(GridBagConstraints.HORIZONTAL)
    assertThat(searchComponent.minimumSize.width).isLessThan(maximumWidth)
    assertThat(searchComponent.preferredSize.width).isEqualTo(maximumWidth)
    assertThat(searchComponent.maximumSize.width).isEqualTo(maximumWidth)
    for (extraWidth in listOf(0, 200)) {
      header.setSize(header.preferredSize.width + extraWidth, header.preferredSize.height)
      header.doLayout()

      assertThat(searchComponent.width).isEqualTo(maximumWidth)
    }

    val narrowWidth = (header.minimumSize.width + header.preferredSize.width) / 2
    header.setSize(narrowWidth, header.preferredSize.height)
    header.doLayout()

    assertThat(searchComponent.width).isLessThan(maximumWidth)
    assertThat(settingsToolbar.bounds.x + settingsToolbar.width).isLessThanOrEqualTo(header.width)

    val historyToolbar = JPanel().apply {
      preferredSize = Dimension(JBUI.scale(88), header.preferredSize.height)
      minimumSize = preferredSize
    }
    val settingsHeader = JPanel(GridBagLayout()).apply {
      add(header, GridBagConstraints().apply {
        gridx = 0
        fill = GridBagConstraints.HORIZONTAL
        weightx = 1.0
      })
      add(historyToolbar, GridBagConstraints().apply {
        gridx = 1
      })
    }
    val expectedNarrowSearchWidth = JBUI.scale(80)
    settingsHeader.setSize(
      header.minimumSize.width + expectedNarrowSearchWidth + historyToolbar.preferredSize.width,
      header.preferredSize.height,
    )
    settingsHeader.doLayout()
    header.doLayout()

    assertThat(searchComponent.width).isEqualTo(expectedNarrowSearchWidth)
    assertThat(updateAllButton.x - searchComponent.run { x + width }).isEqualTo(JBUI.scale(4))
    assertThat(settingsToolbar.x - updateAllButton.run { x + width }).isEqualTo(JBUI.scale(4))
    assertThat(settingsToolbar.bounds.x + settingsToolbar.width).isLessThanOrEqualTo(header.width)
  }

  private fun updateAllButton(header: JComponent): JButton {
    return header.components.single { it is JButton } as JButton
  }

  private suspend fun waitForButton(button: JButton, text: String, enabled: Boolean) {
    withTimeout(5.seconds) {
      while (!button.isVisible || button.text != text || button.isEnabled != enabled) {
        delay(10.milliseconds)
      }
    }
  }

  private class RecordingUpdateAllExecutor : UnifiedPluginUpdateAllExecutor {
    val requests = mutableListOf<UnifiedPluginUpdateAllRequest>()
    val callbacks = mutableListOf<UnifiedPluginUpdateAllCallback>()

    override fun execute(request: UnifiedPluginUpdateAllRequest, callback: UnifiedPluginUpdateAllCallback) {
      requests.add(request)
      callbacks.add(callback)
    }
  }

  private suspend fun waitForSelectedPluginIds(content: Component, expected: Set<String>) {
    withTimeout(5.seconds) {
      while (true) {
        val actual = componentsOfType(content, ListPluginComponent::class.java)
          .filter { it.getSelection() == SelectionType.SELECTION }
          .mapTo(HashSet()) { it.getPluginModel().pluginId.idString }
        if (actual == expected) return@withTimeout
        delay(10.milliseconds)
      }
    }
  }

  private class FixedLocalDataProvider(
    private val inventory: UnifiedPluginInventory,
    private val onInventoryLoaded: () -> Unit = {},
    private val onEnrich: () -> Unit = {},
    private val enrichmentReadiness: suspend () -> Unit = {},
    private val onEnrichmentRequested: () -> Unit = {},
    private val errors: Map<PluginId, List<HtmlChunk>> = emptyMap(),
  ) : UnifiedPluginLocalDataProvider {
    override suspend fun loadInventory(): UnifiedPluginInventory {
      onInventoryLoaded()
      return inventory
    }

    override suspend fun enrich(
      inventory: UnifiedPluginInventory,
      updates: PluginUpdatesEvent?,
      contentRevision: Long,
    ): UnifiedPluginLocalSnapshot {
      onEnrichmentRequested()
      enrichmentReadiness()
      onEnrich()
      val plugins = inventory.installedPlugins + inventory.bundledPlugins
      return buildLocalSnapshot(
        inventory = inventory,
        updates = updates,
        contentRevision = contentRevision,
        enabledStates = plugins.associate { it.model.pluginId to true },
        errors = errors,
        installationStates = emptyMap(),
        restrictions = emptyMap(),
      )
    }
  }

  private class FixedMarketplaceDataProvider(
    private val suggested: List<PluginUiModel> = emptyList(),
    private val searches: Map<String, List<PluginUiModel>> = emptyMap(),
    private val onSearch: (String) -> Unit = {},
    private val onEnrich: () -> Unit = {},
  ) : UnifiedPluginMarketplaceDataProvider {
    val searchQueries = mutableListOf<String>()
    var suggestedLoadCount = 0

    override fun loadSuggested(): Flow<UnifiedPluginMarketplaceFetchResult> = flow {
      suggestedLoadCount++
      emit(UnifiedPluginMarketplaceFetchResult(suggested))
    }

    override suspend fun searchMarketplace(query: String): UnifiedPluginMarketplaceFetchResult {
      searchQueries.add(query)
      onSearch(query)
      return UnifiedPluginMarketplaceFetchResult(searches[query].orEmpty())
    }

    override suspend fun enrich(
      models: List<PluginUiModel>,
      updates: PluginUpdatesEvent?,
      contentRevision: Long,
    ): UnifiedPluginMarketplaceSnapshot {
      onEnrich()
      return buildMarketplaceSnapshot(
        models = models,
        updates = updates,
        contentRevision = contentRevision,
        installedModels = emptyMap(),
        enabledStates = emptyMap(),
        errors = emptyMap(),
        installationStates = emptyMap(),
        restrictions = emptyMap(),
      )
    }
  }

  private class FixedRepositoryDataProvider(
    private val repositories: List<CustomPluginRepository> = emptyList(),
    private val plugins: Map<String, List<PluginUiModel>> = emptyMap(),
  ) : UnifiedPluginRepositoryDataProvider {
    private val repositoryLoadCounter = AtomicInteger()
    val repositoryLoadCount: Int
      get() = repositoryLoadCounter.get()

    override suspend fun loadCatalog(): UnifiedPluginRepositoryCatalogResult {
      return UnifiedPluginRepositoryCatalogResult(repositories)
    }

    override suspend fun loadRepository(repository: CustomPluginRepository): CustomPluginRepositoryLoadResult {
      repositoryLoadCounter.incrementAndGet()
      return CustomPluginRepositoryLoadResult(plugins[repository.id].orEmpty())
    }
  }

  private class DeferredRepositoryDataProvider(
    private val repositories: List<CustomPluginRepository>,
    private val results: Map<String, CompletableDeferred<List<PluginUiModel>>>,
  ) : UnifiedPluginRepositoryDataProvider {
    override suspend fun loadCatalog(): UnifiedPluginRepositoryCatalogResult {
      return UnifiedPluginRepositoryCatalogResult(repositories)
    }

    override suspend fun loadRepository(repository: CustomPluginRepository): CustomPluginRepositoryLoadResult {
      return CustomPluginRepositoryLoadResult(results.getValue(repository.id).await())
    }
  }

  private fun paintedBorder(component: JComponent): BufferedImage {
    val image = BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      component.border.paintBorder(component, graphics, 0, 0, component.width, component.height)
    }
    finally {
      graphics.dispose()
    }
    return image
  }

  private fun inventoryItem(id: String, name: String, bundled: Boolean = false): UnifiedPluginInventoryItem {
    val model = PluginDto(name, PluginId.getId(id)).apply { source = PluginSource.LOCAL }
    return UnifiedPluginInventoryItem(
      model = model,
      runtimeOn = PluginSource.LOCAL,
      stagedOn = null,
      bundledOn = PluginSource.LOCAL.takeIf { bundled },
    )
  }

  private fun <T : Component> componentsOfType(root: Component, type: Class<T>): List<T> {
    val result = ArrayList<T>()
    fun visit(component: Component) {
      if (type.isInstance(component)) result.add(type.cast(component))
      if (component is Container) component.components.forEach(::visit)
    }
    visit(root)
    return result
  }

  private fun snapshot(component: Component): CollectingDataSink {
    val sink = CollectingDataSink()
    (component as UiDataProvider).uiDataSnapshot(sink)
    return sink
  }

  private class CollectingDataSink : DataSink {
    private val data = HashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: DataKey<T>): T? = data[key.name] as? T

    override fun dataSnapshot(provider: DataSnapshotProvider) = provider.dataSnapshot(this)

    override fun uiDataSnapshot(provider: DataProvider): Unit = error("Not used in this test")

    override fun uiDataSnapshot(provider: UiDataProvider) = provider.uiDataSnapshot(this)

    override fun <T : Any> setNull(key: DataKey<T>) {
      data.remove(key.name)
    }

    override fun <T : Any> set(key: DataKey<T>, data: T?) {
      if (data == null) this.data.remove(key.name) else this.data[key.name] = data
    }

    override fun <T : Any> lazyValue(key: DataKey<T>, data: (DataMap) -> T?): Unit = error("Not used in this test")

    override fun <T : Any> lazyNull(key: DataKey<T>): Unit = error("Not used in this test")
  }
}
