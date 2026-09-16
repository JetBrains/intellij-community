// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.ListPluginModel
import com.intellij.ide.plugins.PluginInfoProvider
import com.intellij.ide.plugins.PluginsGroupType
import com.intellij.ide.plugins.TagPanel
import com.intellij.ide.plugins.newui.BaselinePanel
import com.intellij.ide.plugins.newui.InstallButton
import com.intellij.ide.plugins.newui.EventHandler
import com.intellij.ide.plugins.newui.LegacyPluginUiHost
import com.intellij.ide.plugins.newui.LinkComponent
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.ide.plugins.newui.PluginDetailsPageComponent
import com.intellij.ide.plugins.newui.PluginDetailsPageLayout
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.ide.plugins.newui.PluginPreparedUpdateState
import com.intellij.ide.plugins.newui.PluginProgressState
import com.intellij.ide.plugins.newui.PluginRowInput
import com.intellij.ide.plugins.newui.PluginStatus
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.ide.plugins.newui.TagComponent
import com.intellij.ide.plugins.newui.Tags
import com.intellij.ide.plugins.newui.UpdateButton
import com.intellij.ide.plugins.newui.buttons.InstallOptionButton
import com.intellij.ide.plugins.newui.buttons.OptionButton
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.Badge
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.plaf.basic.BasicTabbedPaneUI
import kotlin.math.abs

@TestApplication
@Timeout(30)
internal class LegacyPluginRowFactoryTest {
  @TestDisposable
  lateinit var disposable: Disposable

  companion object {
    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      LafManager.getInstance()
    }
  }

  @Test
  fun `factory and empty reconciler share host lifecycle`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val host = LegacyPluginUiHost(parentScope = this, operationScope = this)
    try {
      val factory = LegacyPluginRowFactory(host, ListPluginModel(), LinkListener { _, _ -> }, onSelectionChanged = {})
      val reconciler = factory.createReconciler { _, _ -> }

      reconciler.close()
      reconciler.close()
    }
    finally {
      host.dispose(closeSession = false)
    }
  }

  @Test
  fun `row group keeps the full section order`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val host = LegacyPluginUiHost(parentScope = this, operationScope = this)
    try {
      val items = (0 until 5).map { index ->
        val pluginId = PluginId.getId("plugin.$index")
        val plugin = PluginNodeModelBuilderFactory.createBuilder(pluginId).setName("Plugin $index").build()
        PluginItemState(pluginId, plugin.name, modelHandle = PluginItemModelHandle(plugin))
      }
      val section = PluginSectionState(PluginSectionId.Installed, items = items)
      val factory = LegacyPluginRowFactory(host, ListPluginModel(), { _, _ -> }, onSelectionChanged = {})
      val specification = factory.specification(section, items.first())
      (factory.createRow(specification.occurrenceId, specification.item, specification.renderKey) as LegacyPluginRow).use { row ->
        val group = row.component.getGroup()
        items.forEachIndexed { index, item ->
          assertThat(group.getPluginIndex(item.pluginId)).isEqualTo(index)
        }

        val reorderedItems = items.reversed()
        factory.specification(section.copy(items = reorderedItems), reorderedItems.first())
        reorderedItems.forEachIndexed { index, item ->
          assertThat(group.getPluginIndex(item.pluginId)).isEqualTo(index)
        }
      }
    }
    finally {
      host.dispose(closeSession = false)
    }
  }

  @Test
  fun `unified host uses secondary plugin action buttons`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val host = LegacyPluginUiHost(parentScope = this, operationScope = this)
    try {
      val searchListener = LinkListener<Any> { _, _ -> }
      val listModel = ListPluginModel()
      val factory = LegacyPluginRowFactory(host, listModel, searchListener, onSelectionChanged = {})
      val reconciler = factory.createReconciler { _, _ -> }

      val marketplacePluginId = PluginId.getId("marketplace.plugin")
      val marketplacePlugin = PluginNodeModelBuilderFactory.createBuilder(marketplacePluginId).setName("Marketplace Plugin").build()
      val marketplaceItem = PluginItemState(
        marketplacePluginId,
        marketplacePlugin.name,
        modelHandle = PluginItemModelHandle(marketplacePlugin),
        rowInput = PluginRowInput(
          installedPlugin = null,
          installationState = PluginInstallationState(false),
          errors = emptyList(),
          updateDescriptor = null,
          enabled = true,
          restrictedByProduct = false,
        ),
      )

      val updatePluginId = PluginId.getId("update.plugin")
      val updatePlugin = PluginNodeModelBuilderFactory.createBuilder(updatePluginId).setName("Update Plugin").build()
      val update = PluginNodeModelBuilderFactory.createBuilder(updatePluginId).setName("Plugin Update").build()
      val updateItem = PluginItemState(
        updatePluginId,
        updatePlugin.name,
        modelHandle = PluginItemModelHandle(updatePlugin),
        rowInput = PluginRowInput(
          installedPlugin = updatePlugin,
          installationState = PluginInstallationState(true),
          errors = emptyList(),
          updateDescriptor = update,
          enabled = true,
          restrictedByProduct = false,
        ),
      )

      val restartPluginId = PluginId.getId("restart.plugin")
      val restartPlugin = PluginNodeModelBuilderFactory.createBuilder(restartPluginId).setName("Restart Plugin").build()
      val restartItem = PluginItemState(
        restartPluginId,
        restartPlugin.name,
        modelHandle = PluginItemModelHandle(restartPlugin),
        rowInput = PluginRowInput(
          installedPlugin = restartPlugin,
          installationState = PluginInstallationState(true, PluginStatus.UPDATED_WITH_RESTART),
          errors = emptyList(),
          updateDescriptor = null,
          enabled = true,
          restrictedByProduct = false,
        ),
      )

      val installedSection = PluginSectionState(PluginSectionId.Installed, items = listOf(updateItem, restartItem))
      val marketplaceSection = PluginSectionState(PluginSectionId.Suggested, items = listOf(marketplaceItem))
      val bindings = reconciler.reconcile(
        listOf(
          factory.specification(installedSection, updateItem),
          factory.specification(installedSection, restartItem),
          factory.specification(marketplaceSection, marketplaceItem),
        )
      )
      val rowsPanel = JPanel()
      bindings.forEach { rowsPanel.add((it.row as LegacyPluginRow).component) }
      factory.rowsRendered(bindings)

      val rows = bindings.associate { it.occurrenceId.pluginId to (it.row as LegacyPluginRow) }
      assertSecondaryStyle(checkNotNull(rows.getValue(marketplacePluginId).component.myInstallButton))
      assertSecondaryStyle(checkNotNull(rows.getValue(updatePluginId).component.myUpdateButton))
      assertSecondaryStyle(checkNotNull(rows.getValue(restartPluginId).component.myRestartButton))

      val details = host.createDetails(searchListener, marketplace = true)
      assertThat(details.useSecondaryButtons).isTrue()

      reconciler.close()
    }
    finally {
      host.dispose(closeSession = false)
    }
  }

  @Test
  fun `unified rows use toggle for plugin enablement`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val host = LegacyPluginUiHost(parentScope = this, operationScope = this)
    try {
      val pluginId = PluginId.getId("toggle.plugin")
      val plugin = PluginNodeModelBuilderFactory.createBuilder(pluginId).setName("Toggle Plugin").build()
      val listModel = ListPluginModel().apply {
        setPluginInstallationState(pluginId, PluginInstallationState(true))
      }
      val item = PluginItemState(
        pluginId,
        plugin.name,
        modelHandle = PluginItemModelHandle(plugin),
        rowInput = PluginRowInput(
          installedPlugin = plugin,
          installationState = PluginInstallationState(true),
          errors = emptyList(),
          updateDescriptor = null,
          enabled = true,
          restrictedByProduct = false,
        ),
      )
      val section = PluginSectionState(PluginSectionId.Installed, items = listOf(item))
      val factory = LegacyPluginRowFactory(host, listModel, { _, _ -> }, onSelectionChanged = {})
      factory.createReconciler { _, _ -> }.use { reconciler ->
        val binding = reconciler.reconcile(listOf(factory.specification(section, item))).single()
        factory.rowsRendered(listOf(binding))
        val row = (binding.row as LegacyPluginRow).component
        row.size = row.preferredSize
        row.doLayout()
        val toggle = componentsOfType(row, OnOffButton::class.java).single()
        val title = componentsOfType(row, JBLabel::class.java).single { it.text == plugin.name }

        assertThat(toggle.accessibleContext.accessibleName).isNotBlank()
        assertThat(toggle.actionListeners).isNotEmpty()
        assertThat(toggle.isFocusable).isTrue()
        assertThat(toggle.isSelected).isTrue()
        assertThat(verticalCenterTwice(toggle)).isEqualTo(verticalCenterTwice(title))
      }
    }
    finally {
      host.dispose(closeSession = false)
    }
  }

  @Test
  fun `details page spacing is enabled only for the unified page`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val listener = LinkListener<Any> { _, _ -> }
      val legacyHost = LegacyPluginUiHost(parentScope = this, operationScope = this)
      val unifiedHost = LegacyPluginUiHost(
        parentScope = this,
        operationScope = this,
        unifiedDetailsPageLayout = true,
      )
      try {
        val legacyDetails = legacyHost.createDetails(listener, marketplace = true)
        val unifiedDetails = unifiedHost.createDetails(listener, marketplace = true)
        assertThat(legacyDetails.layout).isEqualTo(PluginDetailsPageLayout.Legacy)
        assertThat(unifiedDetails.layout).isEqualTo(PluginDetailsPageLayout.Unified)

        val legacyRoot = legacyDetails.getValue(0, true)
        val unifiedRoot = unifiedDetails.getValue(0, true)
        val legacyHeader = detailsHeader(legacyRoot)
        val unifiedHeader = detailsHeader(unifiedRoot)
        assertHorizontalInsets(legacyHeader, left = 20, right = 20)
        assertHorizontalInsets(unifiedHeader, left = 16, right = 16)

        val legacyTabs = componentsOfType(legacyRoot, JBTabbedPane::class.java).single()
        val unifiedTabs = componentsOfType(unifiedRoot, JBTabbedPane::class.java).single()
        legacyTabs.setUI(BasicTabbedPaneUI())
        unifiedTabs.setUI(BasicTabbedPaneUI())
        legacyTabs.setBounds(0, 0, JBUI.scale(800), JBUI.scale(600))
        unifiedTabs.setBounds(0, 0, JBUI.scale(800), JBUI.scale(600))
        legacyTabs.doLayout()
        unifiedTabs.doLayout()
        assertThat(unifiedTabs.getBoundsAt(0).x - legacyTabs.getBoundsAt(0).x).isEqualTo(JBUI.scale(12))

        val legacyOverview = scrollTabContent(legacyTabs, 0)
        val unifiedOverview = scrollTabContent(unifiedTabs, 0)
        assertHorizontalInsets(legacyOverview, left = 16, right = 0)
        assertHorizontalInsets(unifiedOverview, left = 16, right = 16)
        assertHorizontalInsets(borderLayoutChild(legacyOverview, BorderLayout.NORTH), left = 0, right = 16)
        assertHorizontalInsets(borderLayoutChild(unifiedOverview, BorderLayout.NORTH), left = 0, right = 0)

        assertHorizontalInsets(scrollTabContent(legacyTabs, 1), left = 12, right = 0)
        assertHorizontalInsets(scrollTabContent(unifiedTabs, 1), left = 16, right = 16)
        assertHorizontalInsets(
          borderLayoutChild(scrollTabContent(legacyTabs, 2), BorderLayout.NORTH),
          left = 16,
          right = 16,
        )
        assertHorizontalInsets(
          borderLayoutChild(scrollTabContent(unifiedTabs, 2), BorderLayout.NORTH),
          left = 16,
          right = 16,
        )
        assertHorizontalInsets(scrollTabContent(legacyTabs, 3), left = 12, right = 0)
        assertHorizontalInsets(scrollTabContent(unifiedTabs, 3), left = 16, right = 16)
      }
      finally {
        legacyHost.dispose(closeSession = false)
        unifiedHost.dispose(closeSession = false)
      }
    }

  @Test
  fun `unified host keeps the selected plugin icon scale after state changes`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      fun renderIconWidths(pluginIdValue: String, pluginIconScale: Float): Pair<Int, Int> {
        val host = LegacyPluginUiHost(
          parentScope = this,
          operationScope = this,
          pluginIconScale = pluginIconScale,
        )
        try {
          val pluginId = PluginId.getId(pluginIdValue)
          val model = PluginNodeModelBuilderFactory.createBuilder(pluginId).setName("Plugin Icon").build()
          val item = PluginItemState(pluginId, model.name, modelHandle = PluginItemModelHandle(model))
          val section = PluginSectionState(PluginSectionId.Installed, items = listOf(item))
          val factory = LegacyPluginRowFactory(host, ListPluginModel(), { _, _ -> }, onSelectionChanged = {})
          factory.createReconciler { _, _ -> }.use { reconciler ->
            val binding = reconciler.reconcile(listOf(factory.specification(section, item))).single()
            factory.rowsRendered(listOf(binding))
            val row = (binding.row as LegacyPluginRow).component
            val icon = componentsOfType(row, JLabel::class.java).single { it.icon != null }
            val normalWidth = icon.icon.iconWidth

            row.updateErrors(listOf(HtmlChunk.text("Broken plugin")))
            return normalWidth to icon.icon.iconWidth
          }
        }
        finally {
          host.dispose(closeSession = false)
        }
      }

      val baselineWidths = renderIconWidths("default.icon", 1.0f)
      val compactWidths = renderIconWidths("compact.icon", 0.8f)
      assertThat(baselineWidths.first).isEqualTo(baselineWidths.second)
      assertThat(compactWidths.first).isEqualTo(compactWidths.second)
      assertThat(compactWidths.first.toDouble() / baselineWidths.first).isCloseTo(0.8, within(0.05))
    }

  @Test
  fun `unified details actions align their visible leading edge`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val listener = LinkListener<Any> { _, _ -> }
      val legacyHost = LegacyPluginUiHost(parentScope = this, operationScope = this)
      val unifiedHost = LegacyPluginUiHost(parentScope = this, operationScope = this, unifiedDetailsPageLayout = true)
      try {
        val legacyHeader = detailsHeader(legacyHost.createDetails(listener, marketplace = true).getValue(0, true))
        val unifiedHeader = detailsHeader(unifiedHost.createDetails(listener, marketplace = true).getValue(0, true))
        val legacyActions = componentsOfType(legacyHeader, BaselinePanel::class.java).single()
        val unifiedActions = componentsOfType(unifiedHeader, BaselinePanel::class.java).single()
        val legacyInstall = legacyActions.buttonComponents.filterIsInstance<InstallOptionButton>().single()
        val unifiedInstall = unifiedActions.buttonComponents.filterIsInstance<InstallOptionButton>().single()

        layoutHeaderAction(legacyHeader, legacyActions, legacyInstall)
        layoutHeaderAction(unifiedHeader, unifiedActions, unifiedInstall)
        assertThat(legacyInstall.x).isZero()
        assertThat(unifiedInstall.x).isEqualTo(-JBUI.scale(3))
        assertThat(unifiedActions.x + unifiedInstall.x + JBUI.scale(3)).isEqualTo(JBUI.scale(16))

        unifiedActions.setProgressComponent(null, JBLabel("progress"))
        unifiedActions.doLayout()
        assertThat(unifiedInstall.x).isEqualTo(-JBUI.scale(3))
        unifiedActions.removeProgressComponent()

        val plainAction = unifiedActions.buttonComponents.filterIsInstance<JButton>()
          .first { it !is InstallOptionButton }
        layoutHeaderAction(unifiedHeader, unifiedActions, plainAction)
        assertThat(plainAction.x).isEqualTo(-JBUI.scale(3))
      }
      finally {
        legacyHost.dispose(closeSession = false)
        unifiedHost.dispose(closeSession = false)
      }
    }

  @Test
  fun `unified details use natural Install width without changing legacy width`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val listener = LinkListener<Any> { _, _ -> }
      val legacyHost = LegacyPluginUiHost(parentScope = this, operationScope = this)
      val unifiedHost = LegacyPluginUiHost(parentScope = this, operationScope = this, unifiedDetailsPageLayout = true)
      try {
        val legacyHeader = detailsHeader(legacyHost.createDetails(listener, marketplace = true).getValue(0, true))
        val unifiedHeader = detailsHeader(unifiedHost.createDetails(listener, marketplace = true).getValue(0, true))
        val legacyInstall = componentsOfType(legacyHeader, InstallOptionButton::class.java).single()
        val unifiedInstall = componentsOfType(unifiedHeader, InstallOptionButton::class.java).single()

        assertThat(legacyInstall.isPreferredSizeSet).isTrue()
        assertThat(unifiedInstall.isPreferredSizeSet).isFalse()
      }
      finally {
        legacyHost.dispose(closeSession = false)
        unifiedHost.dispose(closeSession = false)
      }
    }

  @Test
  fun `legacy plugin action buttons keep custom colors`() {
    val installButton = InstallButton(false)
    assertThat(installButton.getClientProperty("JButton.textColor")).isNotNull()
    assertThat(installButton.getClientProperty("JButton.backgroundColor")).isNotNull()
    assertThat(installButton.getClientProperty("JButton.borderColor")).isNotNull()

    val updateButton = UpdateButton()
    assertThat(updateButton.getClientProperty("JButton.textColor")).isNotNull()
    assertThat(updateButton.getClientProperty("JButton.backgroundColor")).isNotNull()
    assertThat(updateButton.getClientProperty("JButton.borderColor")).isNotNull()
  }

  @Test
  fun `unified tag panels use badge colors and legacy panels keep tag components`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val selectedQueries = ArrayList<String?>()
      val listener = LinkListener<Any> { _, data -> selectedQueries.add(data as? String) }
      val unifiedPanel = TagPanel(listener, true)
      unifiedPanel.setTags(
        listOf(Tags.Paid.name, Tags.Ultimate.name, Tags.Pro.name, Tags.Freemium.name, Tags.Purchased.name, Tags.EAP.name)
      )

      val badges = unifiedPanel.components.map { component ->
        assertThat(component).isInstanceOf(LinkComponent::class.java)
        val link = component as LinkComponent
        assertThat(link.accessibleContext.accessibleName).isNotBlank()
        link.icon as Badge
      }
      assertThat(badges.map { it.colorType }).containsExactly(
        Badge.ColorType.BLUE_SECONDARY,
        Badge.ColorType.BLUE_SECONDARY,
        Badge.ColorType.BLUE_SECONDARY,
        Badge.ColorType.GREEN_SECONDARY,
        Badge.ColorType.GREEN_SECONDARY,
        Badge.ColorType.GRAY_SECONDARY,
      )
      assertThat((unifiedPanel.components[0] as LinkComponent).toolTipText).isNotBlank()
      assertThat((unifiedPanel.components[5] as LinkComponent).toolTipText).isNotBlank()
      (unifiedPanel.components.first() as LinkComponent).doClick()
      assertThat(selectedQueries).containsExactly(SearchQueryParser.getTagQuery(Tags.Paid.name))

      val legacyPanel = TagPanel(listener)
      legacyPanel.setTags(listOf(Tags.Paid.name))
      assertThat(legacyPanel.components.single()).isInstanceOf(TagComponent::class.java)
    }

  @Test
  fun `unified host uses badge tags in rows and details`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val host = LegacyPluginUiHost(parentScope = this, operationScope = this)
    try {
      val listener = LinkListener<Any> { _, _ -> }
      val pluginId = PluginId.getId("paid.plugin")
      val model = PluginNodeModelBuilderFactory.createBuilder(pluginId)
        .setName("Paid Plugin")
        .setTags(listOf(Tags.Paid.name))
        .build()
      val item = PluginItemState(pluginId, model.name, modelHandle = PluginItemModelHandle(model))
      val section = PluginSectionState(PluginSectionId.Suggested, items = listOf(item))
      val factory = LegacyPluginRowFactory(host, ListPluginModel(), listener, onSelectionChanged = {})
      factory.createReconciler { _, _ -> }.use { reconciler ->
        val binding = reconciler.reconcile(listOf(factory.specification(section, item))).single()
        factory.rowsRendered(listOf(binding))
        val row = (binding.row as LegacyPluginRow).component
        row.size = row.preferredSize
        row.doLayout()
        val title = componentsOfType(row, JBLabel::class.java).single { it.text == model.name }
        val badgeLink = componentsOfType(row, LinkComponent::class.java).single { it.icon is Badge }
        val badge = badgeLink.icon as Badge

        assertThat(badge.text).isEqualTo(Tags.Paid.name)
        assertThat(badge.colorType).isEqualTo(Badge.ColorType.BLUE_SECONDARY)
        assertThat(abs(verticalCenterTwice(title) - verticalCenterTwice(badgeLink))).isLessThanOrEqualTo(1)

        val details = host.createDetails(listener, marketplace = true)
        assertThat(details.useBadgeTags).isTrue()
      }
    }
    finally {
      host.dispose(closeSession = false)
    }
  }

  @Test
  fun `unified rows use stable island selection geometry`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val host = LegacyPluginUiHost(parentScope = this, operationScope = this)
      try {
        val listener = LinkListener<Any> { _, _ -> }
        val pluginId = PluginId.getId("island.selection.plugin")
        val model = PluginNodeModelBuilderFactory.createBuilder(pluginId).setName("Island Selection Plugin").build()
        val item = PluginItemState(pluginId, model.name, modelHandle = PluginItemModelHandle(model))
        val section = PluginSectionState(PluginSectionId.Installed, items = listOf(item))
        val factory = LegacyPluginRowFactory(host, ListPluginModel(), listener, onSelectionChanged = {})
        factory.createReconciler { _, _ -> }.use { reconciler ->
          val binding = reconciler.reconcile(listOf(factory.specification(section, item))).single()
          factory.rowsRendered(listOf(binding))
          val row = (binding.row as LegacyPluginRow).component
          val initialPreferredSize = row.preferredSize
          val insets = row.border.getBorderInsets(row)

          assertThat(row.selectionArc).isEqualTo(JBUI.scale(8))
          assertThat(row.selectionInsets).isEqualTo(JBUI.insets(0, 8))
          assertThat(insets.top).isEqualTo(JBUI.scale(12))
          assertThat(insets.bottom).isEqualTo(JBUI.scale(12))
          assertThat(insets.left).isEqualTo(JBUI.scale(16))
          assertThat(insets.right).isEqualTo(JBUI.scale(16))
          assertThat(row.selectionColor).isNull()

          row.setSelection(EventHandler.SelectionType.HOVER, false)
          assertThat(row.selectionColor).isNotNull()
          assertThat(row.preferredSize).isEqualTo(initialPreferredSize)

          row.setSelection(EventHandler.SelectionType.SELECTION, false)
          assertThat(row.selectionColor).isNotNull()
          assertThat(row.preferredSize).isEqualTo(initialPreferredSize)

          row.setSelection(EventHandler.SelectionType.NONE, false)
          assertThat(row.selectionColor).isNull()
          assertThat(row.preferredSize).isEqualTo(initialPreferredSize)
        }
      }
      finally {
        host.dispose(closeSession = false)
      }
    }

  @Test
  fun `selected legacy details detach before their row is released`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val host = LegacyPluginUiHost(parentScope = this, operationScope = this)
    try {
      val listener = LinkListener<Any> { _, _ -> }
      val listModel = ListPluginModel()
      val factory = LegacyPluginRowFactory(host, listModel, listener, onSelectionChanged = {})
      val presenter = LegacyPluginDetailsPresenter(host, listener)
      val reconciler = factory.createReconciler(presenter::beforeRowRelease)
      try {
        val pluginId = PluginId.getId("details.plugin")
        val model = PluginNodeModelBuilderFactory.createBuilder(pluginId).setName("Details Plugin").build()
        listModel.setPluginInstallationState(pluginId, PluginInstallationState(false))
        val item = PluginItemState(pluginId, model.name, modelHandle = PluginItemModelHandle(model))
        val section = PluginSectionState(PluginSectionId.Installed, items = listOf(item))
        val binding = reconciler.reconcile(listOf(factory.specification(section, item))).single()
        factory.rowsRendered(listOf(binding))
        presenter.render(PluginDetailsMode.LOCAL, listOf(PluginDetailsSelection(binding.occurrenceId, binding.row)))
        val initialDetails = presenter.component.components.toSet()

        reconciler.reconcile(emptyList())
        factory.rowsRendered(emptyList())

        assertThat(presenter.component.components).hasSize(2)
        assertThat(presenter.component.components.toSet().intersect(initialDetails)).hasSize(1)
      }
      finally {
        presenter.close()
        reconciler.close()
      }
    }
    finally {
      host.dispose(closeSession = false)
    }
  }

  @Test
  fun `view attaches a new legacy row before rendering its update descriptor`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val host = LegacyPluginUiHost(parentScope = this, operationScope = this)
      try {
        val pluginId = PluginId.getId("updated.plugin")
        val installed = PluginNodeModelBuilderFactory.createBuilder(pluginId).setName("Installed Plugin").build()
        val update = PluginNodeModelBuilderFactory.createBuilder(pluginId).setName("Plugin Update").build()
        val listModel = ListPluginModel().apply {
          setPluginInstallationState(pluginId, PluginInstallationState(true))
        }
        val input = PluginRowInput(
          installedPlugin = installed,
          installationState = PluginInstallationState(true),
          errors = emptyList(),
          updateDescriptor = update,
          enabled = true,
          restrictedByProduct = false,
        )
        val item = PluginItemState(
          pluginId,
          installed.name,
          modelHandle = PluginItemModelHandle(installed),
          rowInput = input,
        )
        val factory = LegacyPluginRowFactory(host, listModel, LinkListener { _, _ -> }, onSelectionChanged = {})
        UnifiedPluginsPageView({}, {}, { _, _ -> }, rowFactory = factory).use { view ->
          view.render(UnifiedPluginsPageController(listOf(PluginSectionState(PluginSectionId.Installed, items = listOf(item)))).state.value)
          val row = componentsOfType(view.component, ListPluginComponent::class.java).single()
          assertThat(row.parent).isNotNull()
          assertThat(row.getUpdatePluginDescriptor()?.pluginId).isEqualTo(pluginId)
        }
      }
      finally {
        host.dispose(closeSession = false)
      }
    }

  @Test
  fun `active update renders row and details progress without action overlap`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    ApplicationManager.getApplication().replaceService(PluginInfoProvider::class.java, object : PluginInfoProvider {
      override fun loadCachedPlugins(): Set<PluginId> = emptySet()

      override fun loadPlugins(indicator: ProgressIndicator?) = CompletableFuture.completedFuture(emptySet<PluginId>())
    }, disposable)
    val host = LegacyPluginUiHost(parentScope = this, operationScope = this, unifiedDetailsPageLayout = true)
    try {
      val pluginId = PluginId.getId("update.progress.plugin")
      val installed = PluginNodeModelBuilderFactory.createBuilder(pluginId)
        .setName("Update Progress Plugin")
        .setIsConverted(true)
        .build()
      val update = PluginNodeModelBuilderFactory.createBuilder(pluginId)
        .setName("Update Progress Plugin")
        .setIsConverted(true)
        .build()
      val listModel = ListPluginModel().apply {
        setPluginInstallationState(pluginId, PluginInstallationState(true))
      }
      val input = PluginRowInput(
        installedPlugin = installed,
        installationState = PluginInstallationState(true),
        errors = emptyList(),
        updateDescriptor = update,
        enabled = true,
        restrictedByProduct = false,
        operationInProgress = true,
        detailsProgress = PluginProgressState.Determinate(0.375),
      )
      val item = PluginItemState(
        pluginId, update.name, modelHandle = PluginItemModelHandle(update), rowInput = input,
      )
      val listener = LinkListener<Any> { _, _ -> }
      val factory = LegacyPluginRowFactory(host, listModel, listener, onSelectionChanged = {})
      val presenter = LegacyPluginDetailsPresenter(host, listener)
      val details = componentsOfType(presenter.component, PluginDetailsPageComponent::class.java).single { it.isVisible }
      val initialHeader = detailsHeader(details.getValue(0, true))
      val initialActions = componentsOfType(initialHeader, BaselinePanel::class.java).single()
      val staleDisableAction = initialActions.buttonComponents.filterIsInstance<OptionButton>().single { it !is InstallOptionButton }
      initialActions.setProgressDisabledButton(staleDisableAction)
      UnifiedPluginsPageView({}, {}, { _, _ -> }, rowFactory = factory, detailsPresenter = presenter).use { view ->
        val controller = UnifiedPluginsPageController(
          listOf(PluginSectionState(PluginSectionId.Installing, items = listOf(item)))
        )
        view.render(controller.state.value)
        yield()

        val row = componentsOfType(view.component, ListPluginComponent::class.java).single()
        assertThat(row.underProgress()).isTrue()
        val progress = componentsOfType(view.component, JProgressBar::class.java).single()
        assertThat(progress.isIndeterminate).isFalse()
        assertThat(progress.percentComplete).isCloseTo(0.375, within(0.01))
        val header = detailsHeader(details.getValue(0, true))
        val actions = componentsOfType(header, BaselinePanel::class.java).single()
        val updateAction = actions.buttonComponents.filterIsInstance<UpdateButton>().single()
        header.setBounds(0, 0, JBUI.scale(800), header.preferredSize.height)
        header.doLayout()
        actions.doLayout()

        assertThat(actions.buttonComponents.filter(Component::isVisible)).containsExactly(updateAction)
        assertThat(updateAction.isEnabled).isFalse()
        assertThat(updateAction.x).isEqualTo(-JBUI.scale(3))
        val progressContainer = generateSequence<Component>(progress) { it.parent }.first { it.parent === actions }
        assertThat(updateAction.bounds.intersects(progressContainer.bounds)).isFalse()

        row.showProgress()
        details.showInstallProgress(this)

        assertThat(componentsOfType(view.component, JProgressBar::class.java)).hasSize(1)

        controller.updateSection(PluginSectionState(
          PluginSectionId.Installing,
          items = listOf(item.copy(contentRevision = 1, rowInput = input.copy(detailsProgress = PluginProgressState.Indeterminate))),
        ))
        view.render(controller.state.value)
        yield()

        assertThat(componentsOfType(view.component, JProgressBar::class.java).single().isIndeterminate).isTrue()

        details.hideProgress()

        controller.updateSection(PluginSectionState(
          PluginSectionId.Installing,
          items = listOf(item.copy(contentRevision = 2, rowInput = input.copy(operationInProgress = false, detailsProgress = null))),
        ))
        view.render(controller.state.value)
        yield()

        assertThat(componentsOfType(view.component, ListPluginComponent::class.java).single().underProgress()).isFalse()
        assertThat(componentsOfType(view.component, JProgressBar::class.java)).isEmpty()
      }
    }
    finally {
      host.dispose(closeSession = false)
    }
  }

  @Test
  fun `prepared dynamic update shows Installed and Reset restores Update`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val host = LegacyPluginUiHost(parentScope = this, operationScope = this, unifiedDetailsPageLayout = true)
      try {
        val pluginId = PluginId.getId("prepared.dynamic.update.plugin")
        val installed = PluginNodeModelBuilderFactory.createBuilder(pluginId)
          .setName("Prepared Dynamic Update Plugin")
          .setIsConverted(true)
          .build()
        val update = PluginNodeModelBuilderFactory.createBuilder(pluginId)
          .setName("Prepared Dynamic Update Plugin")
          .setIsConverted(true)
          .build()
        val input = PluginRowInput(
          installedPlugin = installed,
          installationState = PluginInstallationState(true),
          errors = emptyList(),
          updateDescriptor = update,
          enabled = true,
          restrictedByProduct = false,
          preparedUpdate = PluginPreparedUpdateState(restartRequired = false),
        )
        val item = PluginItemState(
          pluginId,
          installed.name,
          modelHandle = PluginItemModelHandle(installed),
          rowInput = input,
        )
        val listener = LinkListener<Any> { _, _ -> }
        val factory = LegacyPluginRowFactory(host, ListPluginModel(), listener, onSelectionChanged = {})
        val presenter = LegacyPluginDetailsPresenter(host, listener)
        UnifiedPluginsPageView({}, {}, { _, _ -> }, rowFactory = factory, detailsPresenter = presenter).use { view ->
          val controller = UnifiedPluginsPageController(
            listOf(PluginSectionState(PluginSectionId.Installed, items = listOf(item)))
          )

          view.render(controller.state.value)
          yield()

          val firstRow = componentsOfType(view.component, ListPluginComponent::class.java).single()
          val installedAction = checkNotNull(firstRow.myUpdateButton)
          assertThat(installedAction.text).isEqualTo(IdeBundle.message("plugin.status.installed"))
          assertThat(installedAction.isEnabled).isFalse()
          assertPreparedDetailsAction(presenter, IdeBundle.message("plugin.status.installed"), enabled = false)

          val availableItem = item.copy(
            contentRevision = 1,
            rowInput = input.copy(preparedUpdate = null),
          )
          controller.updateSection(PluginSectionState(PluginSectionId.Installed, items = listOf(availableItem)))
          view.render(controller.state.value)
          yield()

          val resetRow = componentsOfType(view.component, ListPluginComponent::class.java).single()
          assertThat(resetRow).isNotSameAs(firstRow)
          assertThat(resetRow.myUpdateButton?.text).isEqualTo(IdeBundle.message("plugins.configurable.update.button"))
          assertThat(resetRow.myUpdateButton?.isEnabled).isTrue()
          assertPreparedDetailsAction(
            presenter,
            IdeBundle.message("plugins.configurable.update.button"),
            enabled = true,
          )
        }
      }
      finally {
        host.dispose(closeSession = false)
      }
    }

  @Test
  fun `prepared update that needs restart shows Restart IDE in the row and details`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val host = LegacyPluginUiHost(parentScope = this, operationScope = this, unifiedDetailsPageLayout = true)
      try {
        val pluginId = PluginId.getId("prepared.restart.update.plugin")
        val installed = PluginNodeModelBuilderFactory.createBuilder(pluginId)
          .setName("Prepared Restart Update Plugin")
          .setIsConverted(true)
          .build()
        val update = PluginNodeModelBuilderFactory.createBuilder(pluginId)
          .setName("Prepared Restart Update Plugin")
          .setIsConverted(true)
          .build()
        val input = PluginRowInput(
          installedPlugin = installed,
          installationState = PluginInstallationState(true),
          errors = emptyList(),
          updateDescriptor = update,
          enabled = true,
          restrictedByProduct = false,
          preparedUpdate = PluginPreparedUpdateState(restartRequired = true),
        )
        val item = PluginItemState(
          pluginId,
          installed.name,
          modelHandle = PluginItemModelHandle(installed),
          rowInput = input,
        )
        val listener = LinkListener<Any> { _, _ -> }
        val factory = LegacyPluginRowFactory(host, ListPluginModel(), listener, onSelectionChanged = {})
        val presenter = LegacyPluginDetailsPresenter(host, listener)
        UnifiedPluginsPageView({}, {}, { _, _ -> }, rowFactory = factory, detailsPresenter = presenter).use { view ->
          view.render(UnifiedPluginsPageController(
            listOf(PluginSectionState(PluginSectionId.Installed, items = listOf(item)))
          ).state.value)
          yield()

          val row = componentsOfType(view.component, ListPluginComponent::class.java).single()
          assertThat(row.myRestartButton?.text).isEqualTo(IdeBundle.message("plugins.configurable.restart.ide.button"))
          assertThat(row.myRestartButton?.isVisible).isTrue()
          assertPreparedDetailsAction(
            presenter,
            IdeBundle.message("plugins.configurable.restart.ide.button"),
            enabled = true,
          )
        }
      }
      finally {
        host.dispose(closeSession = false)
      }
    }

  @Test
  fun `a completed dynamic update does not request restart in recreated unified details`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val host = LegacyPluginUiHost(parentScope = this, operationScope = this, unifiedDetailsPageLayout = true)
      try {
        val pluginId = PluginId.getId("completed.dynamic.update.plugin")
        val plugin = PluginNodeModelBuilderFactory.createBuilder(pluginId)
          .setName("Completed Dynamic Update Plugin")
          .setIsConverted(true)
          .build()
        InstalledPluginsState.getInstance().onPluginInstall(plugin.getDescriptor(), true, false)
        val input = PluginRowInput(
          installedPlugin = plugin,
          installationState = PluginInstallationState(true, PluginStatus.UPDATED),
          errors = emptyList(),
          updateDescriptor = null,
          enabled = true,
          restrictedByProduct = false,
        )
        val item = PluginItemState(pluginId, plugin.name, modelHandle = PluginItemModelHandle(plugin), rowInput = input)
        val listener = LinkListener<Any> { _, _ -> }
        val factory = LegacyPluginRowFactory(host, ListPluginModel(), listener, onSelectionChanged = {})
        val presenter = LegacyPluginDetailsPresenter(host, listener)
        UnifiedPluginsPageView({}, {}, { _, _ -> }, rowFactory = factory, detailsPresenter = presenter).use { view ->
          val controller = UnifiedPluginsPageController(
            listOf(PluginSectionState(PluginSectionId.Installing, items = listOf(item)))
          )

          view.render(controller.state.value)
          yield()

          val details = componentsOfType(presenter.component, PluginDetailsPageComponent::class.java).single { it.isVisible }
          val restartButton = componentsOfType(detailsHeader(details.getValue(0, true)), JButton::class.java).single {
            it.text == IdeBundle.message("plugins.configurable.restart.ide.button")
          }
          assertThat(restartButton.isVisible).isFalse()
        }
      }
      finally {
        host.dispose(closeSession = false)
      }
    }

  @Test
  fun `a terminal ledger row clears inherited progress on its first render`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val host = LegacyPluginUiHost(parentScope = this, operationScope = this)
      try {
        val pluginId = PluginId.getId("completed.update.progress.plugin")
        val plugin = PluginNodeModelBuilderFactory.createBuilder(pluginId).setName("Completed Update Progress Plugin").build()
        val input = PluginRowInput(
          installedPlugin = plugin,
          installationState = PluginInstallationState(true, PluginStatus.UPDATED),
          errors = emptyList(),
          updateDescriptor = null,
          enabled = true,
          restrictedByProduct = false,
          operationInProgress = false,
        )
        val item = PluginItemState(pluginId, plugin.name, modelHandle = PluginItemModelHandle(plugin), rowInput = input)
        val section = PluginSectionState(PluginSectionId.Installing, items = listOf(item))
        val factory = LegacyPluginRowFactory(host, ListPluginModel(), LinkListener { _, _ -> }, onSelectionChanged = {})
        factory.createReconciler { _, _ -> }.use { reconciler ->
          val binding = reconciler.reconcile(listOf(factory.specification(section, item))).single()
          val component = (binding.row as LegacyPluginRow).component
          JPanel().add(component)
          component.showProgress()

          factory.rowsRendered(listOf(binding))

          assertThat(component.underProgress()).isFalse()
        }
      }
      finally {
        host.dispose(closeSession = false)
      }
    }

  @Test
  fun `released row removes listeners from replaced controls`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val host = LegacyPluginUiHost(parentScope = this, operationScope = this)
    try {
      val factory = LegacyPluginRowFactory(host, ListPluginModel(), LinkListener { _, _ -> }, onSelectionChanged = {})
      factory.createReconciler { _, _ -> }.use { reconciler ->
        val pluginId = PluginId.getId("replaced.controls.plugin")
        val model = PluginNodeModelBuilderFactory.createBuilder(pluginId).setName("Replaced Controls Plugin").build()
        val rowInput = PluginRowInput(
          installedPlugin = null,
          installationState = PluginInstallationState(false),
          errors = emptyList(),
          updateDescriptor = null,
          enabled = true,
          restrictedByProduct = false,
        )
        val item = PluginItemState(pluginId, model.name, modelHandle = PluginItemModelHandle(model), rowInput = rowInput)
        val section = PluginSectionState(PluginSectionId.Suggested, items = listOf(item))
        val binding = reconciler.reconcile(listOf(factory.specification(section, item))).single()
        val component = (binding.row as LegacyPluginRow).component
        val replacedButton = checkNotNull(component.myInstallButton)
        val rowMouseListeners = component.mouseListeners.toSet()
        val delegatingMouseListener = replacedButton.mouseListeners.single(rowMouseListeners::contains)

        factory.rowsRendered(listOf(binding))
        assertThat(component.myInstallButton).isNotSameAs(replacedButton)

        reconciler.reconcile(emptyList())
        factory.rowsRendered(emptyList())

        assertThat(replacedButton.mouseListeners).doesNotContain(delegatingMouseListener)
      }
    }
    finally {
      host.dispose(closeSession = false)
    }
  }

  @Test
  fun `local sections use installed row behavior`() {
    assertThat(legacyRowPresentation(PluginSectionId.Installing))
      .isEqualTo(LegacyPluginRowPresentation(PluginsGroupType.INSTALLING, marketplace = false))
    assertThat(legacyRowPresentation(PluginSectionId.Installed))
      .isEqualTo(LegacyPluginRowPresentation(PluginsGroupType.INSTALLED, marketplace = false))
    assertThat(legacyRowPresentation(PluginSectionId.Bundled))
      .isEqualTo(LegacyPluginRowPresentation(PluginsGroupType.INSTALLED, marketplace = false))
  }

  @Test
  fun `remote sections use marketplace row behavior`() {
    assertThat(legacyRowPresentation(PluginSectionId.Internal))
      .isEqualTo(LegacyPluginRowPresentation(PluginsGroupType.INTERNAL, marketplace = true))
    assertThat(legacyRowPresentation(PluginSectionId.Suggested))
      .isEqualTo(LegacyPluginRowPresentation(PluginsGroupType.SUGGESTED, marketplace = true))
    assertThat(legacyRowPresentation(PluginSectionId.Marketplace))
      .isEqualTo(LegacyPluginRowPresentation(PluginsGroupType.SEARCH, marketplace = true))
    assertThat(legacyRowPresentation(PluginSectionId.CustomRepository("repository")))
      .isEqualTo(LegacyPluginRowPresentation(PluginsGroupType.CUSTOM_REPOSITORY, marketplace = true))
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

  private fun assertSecondaryStyle(button: JButton) {
    assertThat(button.getClientProperty("JButton.textColor")).isNull()
    assertThat(button.getClientProperty("JButton.focusedTextColor")).isNull()
    assertThat(button.getClientProperty("JButton.backgroundColor")).isNull()
    assertThat(button.getClientProperty("JButton.focusedBackgroundColor")).isNull()
    assertThat(button.getClientProperty("JButton.borderColor")).isNull()
    assertThat(button.getClientProperty("JButton.focusedBorderColor")).isNull()
  }

  private fun assertPreparedDetailsAction(
    presenter: LegacyPluginDetailsPresenter,
    expectedText: String,
    enabled: Boolean,
  ) {
    val details = componentsOfType(presenter.component, PluginDetailsPageComponent::class.java).single { it.isVisible }
    val actions = componentsOfType(detailsHeader(details.getValue(0, true)), BaselinePanel::class.java).single()
    val action = actions.buttonComponents.filterIsInstance<JButton>().single { button ->
      button.isVisible && button.text == expectedText
    }
    assertThat(action.isEnabled).isEqualTo(enabled)
  }

  private fun verticalCenterTwice(component: Component): Int = component.y * 2 + component.height

  private fun detailsHeader(root: JComponent): JComponent {
    val content = (root.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER) as JComponent
    return (content.layout as BorderLayout).getLayoutComponent(BorderLayout.NORTH) as JComponent
  }

  private fun scrollTabContent(pane: JBTabbedPane, index: Int): JComponent =
    (pane.getComponentAt(index) as JBScrollPane).viewport.view as JComponent

  private fun layoutHeaderAction(header: JComponent, actions: BaselinePanel, target: Component) {
    actions.buttonComponents.forEach { it.isVisible = it === target }
    header.setBounds(0, 0, JBUI.scale(800), header.preferredSize.height)
    header.doLayout()
    actions.doLayout()
  }

  private fun borderLayoutChild(parent: JComponent, position: String): JComponent =
    (parent.layout as BorderLayout).getLayoutComponent(position) as JComponent

  private fun assertHorizontalInsets(component: JComponent, left: Int, right: Int) {
    val insets = component.border.getBorderInsets(component)
    assertThat(insets.left).isEqualTo(JBUI.scale(left))
    assertThat(insets.right).isEqualTo(JBUI.scale(right))
  }
}
