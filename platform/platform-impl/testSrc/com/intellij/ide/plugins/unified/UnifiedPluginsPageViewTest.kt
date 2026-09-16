// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.ui.LafManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.UI
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.Divider
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.SearchFieldWithExtension
import com.intellij.ui.border.CustomLineBorder
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.annotations.Nls
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

@TestApplication
@Timeout(30)
internal class UnifiedPluginsPageViewTest {
  companion object {
    private const val EXPECTED_ANCHOR_OFFSET: Int = -7
    private const val SEARCH_HISTORY_PROPERTY: String = "UnifiedPluginsSearchHistory"

    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      LafManager.getInstance()
    }
  }

  @Test
  fun `updated Update All button has no icon`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val updateAll = UnifiedPluginUpdateAllButton {}

    updateAll.render(UnifiedPluginUpdateAllPresentation.Updated)

    val button = updateAll.component as JButton
    assertThat(button.text).isEqualTo(IdeBundle.message("plugins.configurable.updated.button"))
    assertThat(button.icon).isNull()
    assertThat(button.isEnabled).isFalse()
    assertThat(button.isVisible).isTrue()
  }

  @Test
  fun `no visible sections show the standard empty status`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val view = createView()

    view.render(UnifiedPluginsPageController().state.value)

    assertThat(sectionLists(view)).isEmpty()
    val resultsPanel = componentsOfType(view.component, JBPanelWithEmptyText::class.java).single()
    val emptyText = IdeBundle.message("plugins.configurable.nothing.found")
    assertThat(resultsPanel.emptyText.text).isEqualTo(emptyText)
    assertThat(resultsPanel.accessibleContext.accessibleName).isEqualTo(emptyText)
  }

  @Test
  fun `section dividers follow the visible section order`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val controller = UnifiedPluginsPageController(
      listOf(
        section(PluginSectionId.Installed, itemCount = 1),
        section(PluginSectionId.Bundled, itemCount = 1),
        section(PluginSectionId.Suggested, itemCount = 1),
      )
    )
    val view = createView()

    view.render(controller.state.value)

    assertThat(sectionDividerVisibility(view)).containsExactly(false, true, true)
    prepareForScrolling(view)
    assertThat(sectionGaps(view)).containsOnly(JBUI.scale(8))

    controller.updateSection(section(PluginSectionId.Installing, itemCount = 1))
    view.render(controller.state.value)

    assertThat(sectionDividerVisibility(view)).containsExactly(false, true, true, true)

    controller.removeSection(PluginSectionId.Installing)
    view.render(controller.state.value)

    assertThat(sectionDividerVisibility(view)).containsExactly(false, true, true)
  }

  @Test
  fun `result announcements describe loading completion and errors`() {
    val ready = UnifiedPluginsPageController(
      listOf(section(PluginSectionId.Installed, "first.plugin", "second.plugin"))
    ).state.value
    val loading = ready.copy(
      sections = ready.sections + PluginSectionState(
        PluginSectionId.Suggested,
        status = PluginSectionStatus.Loading(showingStaleContent = false),
      )
    )
    val failed = ready.copy(
      sections = ready.sections + PluginSectionState(
        PluginSectionId.Suggested,
        status = PluginSectionStatus.Failed(PluginSectionError("Marketplace unavailable", retryable = true)),
      )
    )

    assertThat(pluginResultsAnnouncement(loading)).isEqualTo(IdeBundle.message("plugins.configurable.results.loading"))
    assertThat(pluginResultsAnnouncement(ready)).isEqualTo(IdeBundle.message("plugins.configurable.results.updated", 2))
    assertThat(pluginResultsAnnouncement(failed)).isEqualTo("Marketplace unavailable")
  }

  @Test
  fun `loading icon replaces count while retaining items`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 5))
    val view = createView()
    view.render(controller.state.value)

    val installedList = sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))
    val installedComponent = installedList.parent
    val countLabel = componentsOfType(installedComponent, JBLabel::class.java).single { it.text == "5" }
    assertThat(countLabel.icon).isNull()
    assertThat(installedList.model.size).isEqualTo(PluginSectionState.COLLAPSED_ITEM_LIMIT)

    val installed = controller.state.value.section(PluginSectionId.Installed)
    controller.updateSection(installed.copy(status = PluginSectionStatus.Loading(showingStaleContent = true)))
    view.render(controller.state.value)

    assertThat(sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))).isSameAs(installedList)
    val loadingLabel = componentsOfType(installedComponent, JBLabel::class.java).single { it.icon is AnimatedIcon }
    assertThat(loadingLabel.text).isEmpty()
    assertThat(loadingLabel.accessibleContext.accessibleName)
      .isEqualTo(IdeBundle.message("plugins.configurable.section.loading", IdeBundle.message("plugin.manager.tab.installed")))
  }

  @Test
  fun `large section count shows the display limit`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 1_001))
    val view = createView()

    view.render(controller.state.value)

    val installedList = sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))
    val expectedCount = IdeBundle.message(
      "plugins.configurable.count.more",
      PluginSectionState.MAX_DISPLAYED_ITEM_COUNT,
    )
    val countLabel = componentsOfType(installedList.parent, JBLabel::class.java).single { it.text == expectedCount }
    assertThat(countLabel.accessibleContext.accessibleName).isEqualTo(expectedCount)
  }

  @Test
  fun `section failure renders a scoped retry action`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val retries = ArrayList<PluginSectionId>()
    val error = PluginSectionError("Marketplace unavailable", retryable = true)
    val controller = UnifiedPluginsPageController()
    controller.updateSection(PluginSectionState(PluginSectionId.Suggested, status = PluginSectionStatus.Failed(error)))
    val view = createView(onRetryRequested = retries::add)

    view.render(controller.state.value)

    val errorLabel = componentsOfType(view.component, JBLabel::class.java).single { it.text == "Marketplace unavailable" }
    val errorPanel = errorLabel.parent as JComponent
    assertThat(errorPanel.border.getBorderInsets(errorPanel)).isEqualTo(JBUI.insets(10))
    val retry = componentsOfType(view.component, ActionLink::class.java)
      .single { it.text == IdeBundle.message("plugin.manager.refresh") }
    retry.doClick()
    assertThat(retries).containsExactly(PluginSectionId.Suggested)
  }

  @Test
  fun `expansion control projects all items and emits semantic intent`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val expansionChanges = ArrayList<Pair<PluginSectionId, Boolean>>()
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 5))
    val view = createView(onExpansionChanged = { id, expanded -> expansionChanges.add(id to expanded) })
    view.render(controller.state.value)

    val installedList = sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))
    val expansionLink = componentsOfType(installedList.parent, ActionLink::class.java).single()
    assertThat(installedList.model.size).isEqualTo(PluginSectionState.COLLAPSED_ITEM_LIMIT)
    assertThat(expansionLink.text).isEqualTo(IdeBundle.message("plugins.configurable.show.more"))
    assertThat(expansionLink.icon).isSameAs(AllIcons.General.ChevronDown)
    assertThat(expansionLink.horizontalTextPosition).isEqualTo(SwingConstants.LEADING)

    expansionLink.doClick()
    assertThat(expansionChanges).containsExactly(PluginSectionId.Installed to true)

    controller.setSectionExpanded(PluginSectionId.Installed, true)
    view.render(controller.state.value)
    assertThat(installedList.model.size).isEqualTo(5)
    assertThat(expansionLink.text).isEqualTo(IdeBundle.message("plugins.configurable.show.less"))
    assertThat(expansionLink.icon).isSameAs(AllIcons.General.ChevronUp)

    expansionLink.doClick()
    assertThat(expansionChanges).containsExactly(
      PluginSectionId.Installed to true,
      PluginSectionId.Installed to false,
    )
  }

  @Test
  fun `long repository title truncates without hiding controls`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val repositoryId = PluginSectionId.CustomRepository(
      "https://plugins.example.com/" + "a-very-long-repository-segment/".repeat(10) + "plugins.xml"
    )
    val fullTitle = IdeBundle.message("plugins.configurable.repository.0", repositoryId.repositoryId)
    val controller = UnifiedPluginsPageController(
      initialSections = listOf(section(repositoryId, itemCount = 5, title = fullTitle)),
      initialQuery = PluginsQueryState("query", "query"),
    )
    val view = createView()
    view.component.setSize(700, 320)
    layoutRecursively(view.component)

    view.render(controller.state.value)
    layoutRecursively(view.component)
    view.render(controller.state.value)

    val titleLabel = componentsOfType(view.component, JBLabel::class.java)
      .single { it.accessibleContext.accessibleName == fullTitle }
    val header = checkNotNull(titleLabel.parent?.parent)
    val expansionLink = componentsOfType(header, ActionLink::class.java).single()
    val statusLabel = componentsOfType(header, JBLabel::class.java).single { it.text == "5" }
    val availableTitleWidth = titleLabel.parent.width - statusLabel.preferredSize.width
    val fullTitleWidth = titleLabel.getFontMetrics(titleLabel.font).stringWidth(fullTitle)
    assertThat(fullTitleWidth)
      .describedAs("full title width with %s available in a %s-wide header", availableTitleWidth, header.width)
      .isGreaterThan(availableTitleWidth)
    assertThat(titleLabel.text)
      .startsWith(fullTitle.take(5))
      .endsWith("...")
      .isNotEqualTo(fullTitle)
    assertThat(titleLabel.toolTipText).isEqualTo(fullTitle)
    assertThat(expansionLink.x + expansionLink.width).isLessThanOrEqualTo(header.width)
    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    val actionRight = SwingUtilities.convertPoint(expansionLink, Point(expansionLink.width, 0), scrollPane.viewport).x
    assertThat(actionRight).isLessThanOrEqualTo(scrollPane.viewport.width)

    view.component.setSize(fullTitleWidth * 4, 320)
    layoutRecursively(view.component)
    view.render(controller.state.value)

    assertThat(titleLabel.text).isEqualTo(fullTitle)
    assertThat(titleLabel.toolTipText).isNull()
  }

  @Test
  fun `long non-repository section title uses trailing ellipsis`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val fullTitle = "Internal plugins from " + "a very long organization name ".repeat(10)
    val controller = UnifiedPluginsPageController(
      listOf(section(PluginSectionId.Internal, itemCount = 5, title = fullTitle))
    )
    val view = createView()
    view.component.setSize(700, 320)
    layoutRecursively(view.component)

    view.render(controller.state.value)
    layoutRecursively(view.component)
    view.render(controller.state.value)

    val titleLabel = componentsOfType(view.component, JBLabel::class.java)
      .single { it.accessibleContext.accessibleName == fullTitle }
    assertThat(titleLabel.text)
      .startsWith(fullTitle.take(5))
      .endsWith("...")
      .isNotEqualTo(fullTitle)
    assertThat(titleLabel.toolTipText).isEqualTo(fullTitle)
  }

  @Test
  fun `section instances are reused and removed by semantic id`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val repositoryId = PluginSectionId.CustomRepository("repository")
    val repositoryTitle = IdeBundle.message("plugins.configurable.repository.0", repositoryId.repositoryId)
    val controller = UnifiedPluginsPageController(
      initialSections = listOf(
        section(PluginSectionId.Installed, itemCount = 1),
        section(repositoryId, itemCount = 2, title = repositoryTitle),
      ),
      initialQuery = PluginsQueryState("query", "query"),
    )
    val view = createView()
    view.render(controller.state.value)

    val installedList = sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))
    val installedComponent = installedList.parent
    val repositoryList = sectionList(view, repositoryTitle)
    val repositoryComponent = repositoryList.parent
    val sectionsPanel = checkNotNull(installedComponent.parent)
    val removedComponents = ArrayList<Component>()
    sectionsPanel.addContainerListener(object : ContainerAdapter() {
      override fun componentRemoved(event: ContainerEvent) {
        removedComponents.add(event.child)
      }
    })

    view.render(controller.state.value)
    assertThat(sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))).isSameAs(installedList)
    assertThat(sectionList(view, repositoryTitle)).isSameAs(repositoryList)

    controller.updateSections(
      listOf(
        section(PluginSectionId.Installing, itemCount = 1),
        section(PluginSectionId.Installed, itemCount = 1),
      )
    )
    controller.removeSection(repositoryId)
    view.render(controller.state.value)

    assertThat(sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))).isSameAs(installedList)
    assertThat(installedComponent.parent).isSameAs(sectionsPanel)
    assertThat(removedComponents).doesNotContain(installedComponent)
    assertThat(sectionLists(view).map { it.accessibleContext.accessibleName }).doesNotContain(repositoryTitle)
    assertThat(repositoryComponent.parent).isNull()
  }

  @Test
  fun `inserting and removing a section preserves visible occurrence offset`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 20))
    controller.setSectionExpanded(PluginSectionId.Installed, true)
    val view = createView()
    view.render(controller.state.value)
    prepareForScrolling(view)

    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    val installedList = sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))
    val anchorIndex = 9
    scrollToRow(scrollPane, installedList, anchorIndex)
    val initialViewY = scrollPane.viewport.viewPosition.y
    assertThat(rowOffset(scrollPane, installedList, anchorIndex)).isEqualTo(EXPECTED_ANCHOR_OFFSET)

    controller.updateSection(section(PluginSectionId.Installing, itemCount = 4))
    view.render(controller.state.value)

    assertThat(sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))).isSameAs(installedList)
    assertThat(rowOffset(scrollPane, installedList, anchorIndex)).isEqualTo(EXPECTED_ANCHOR_OFFSET)
    assertThat(scrollPane.viewport.viewPosition.y).isGreaterThan(initialViewY)

    controller.removeSection(PluginSectionId.Installing)
    view.render(controller.state.value)

    assertThat(rowOffset(scrollPane, installedList, anchorIndex)).isEqualTo(EXPECTED_ANCHOR_OFFSET)
    assertThat(scrollPane.viewport.viewPosition.y).isEqualTo(initialViewY)
  }

  @Test
  fun `expanding a preceding section preserves visible occurrence offset`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val controller = UnifiedPluginsPageController()
    controller.updateSections(
      listOf(
        section(PluginSectionId.Installed, itemCount = 5),
        section(PluginSectionId.Bundled, itemCount = 20),
      )
    )
    controller.setSectionExpanded(PluginSectionId.Bundled, true)
    val view = createView()
    view.render(controller.state.value)
    prepareForScrolling(view)

    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    val bundledList = sectionList(view, IdeBundle.message("plugins.configurable.bundled"))
    val anchorIndex = 7
    scrollToRow(scrollPane, bundledList, anchorIndex)
    val initialViewY = scrollPane.viewport.viewPosition.y

    controller.setSectionExpanded(PluginSectionId.Installed, true)
    view.render(controller.state.value)

    assertThat(rowOffset(scrollPane, bundledList, anchorIndex)).isEqualTo(EXPECTED_ANCHOR_OFFSET)
    assertThat(scrollPane.viewport.viewPosition.y).isGreaterThan(initialViewY)
  }

  @Test
  fun `large expanded section realizes viewport chunks without changing scroll geometry`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UI) {
      val controller = UnifiedPluginsPageController()
      controller.updateSection(section(PluginSectionId.Installed, itemCount = 350))
      controller.setSectionExpanded(PluginSectionId.Installed, true)
      val view = createView()
      view.render(controller.state.value)
      prepareForScrolling(view)

      val installedList = sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))
      val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
      val initialViewHeight = scrollPane.viewport.view.height
      assertThat(installedList.model.size).isEqualTo(100)

      val listTop = componentY(installedList, scrollPane.viewport.view)
      val targetViewY = listTop + 120 * installedList.fixedCellHeight
      scrollPane.viewport.viewPosition = Point(0, targetViewY)

      assertThat(installedList.model.size).isEqualTo(200)
      assertThat(scrollPane.viewport.viewPosition.y).isEqualTo(targetViewY)
      assertThat(scrollPane.viewport.view.height).isEqualTo(initialViewHeight)
      assertThat(installedList.model.getElementAt(120)).isEqualTo(item("plugin.121"))
    }

  @Test
  fun `query replacement resets the realized row prefix`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 350))
    controller.setSectionExpanded(PluginSectionId.Installed, true)
    val view = createView()
    view.render(controller.state.value)
    prepareForScrolling(view)

    val installedList = sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))
    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    val listTop = componentY(installedList, scrollPane.viewport.view)
    scrollPane.viewport.viewPosition = Point(0, listTop + 120 * installedList.fixedCellHeight)
    assertThat(installedList.model.size).isEqualTo(200)

    controller.setQuery(PluginsQueryState("new", "new", revision = 1))
    controller.updateSection(
      PluginSectionState(
        PluginSectionId.Installed,
        items = (1..350).map { item("replacement.$it") },
      )
    )
    view.render(controller.state.value)

    assertThat(installedList.model.size).isEqualTo(100)
    assertThat(installedList.model.getElementAt(0)).isEqualTo(item("replacement.1"))
  }

  @Test
  fun `stable realized plugin prefix survives presentation updates`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 350))
    controller.setSectionExpanded(PluginSectionId.Installed, true)
    val view = createView()
    view.render(controller.state.value)
    prepareForScrolling(view)

    val installedList = sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))
    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    val listTop = componentY(installedList, scrollPane.viewport.view)
    scrollPane.viewport.viewPosition = Point(0, listTop + 120 * installedList.fixedCellHeight)
    assertThat(installedList.model.size).isEqualTo(200)

    val updatedSection = controller.state.value.section(PluginSectionId.Installed).copy(
      items = (1..350).map { index -> item("plugin.$index").copy(name = "Updated $index") },
    )
    controller.updateSection(updatedSection)
    view.render(controller.state.value)

    assertThat(installedList.model.size).isEqualTo(200)
    assertThat(installedList.model.getElementAt(0)).isEqualTo(item("plugin.1").copy(name = "Updated 1"))
  }

  @Test
  fun `selected real rows are realized in every expanded section`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val installedSelection = PluginOccurrenceId(PluginSectionId.Installed, PluginId.getId("installed.105"))
    val bundledSelection = PluginOccurrenceId(PluginSectionId.Bundled, PluginId.getId("bundled.106"))
    val controller = UnifiedPluginsPageController()
    controller.updateSections(
      listOf(
        PluginSectionState(PluginSectionId.Installed, items = (1..110).map { realItem("installed.$it") }),
        PluginSectionState(PluginSectionId.Bundled, items = (1..110).map { realItem("bundled.$it") }),
      )
    )
    assertThat(controller.selectAndRevealOccurrences(listOf(installedSelection, bundledSelection))).isTrue()
    val presenter = RecordingPluginDetailsPresenter()
    val rowFactory = RecordingPluginRowFactory()
    val view = createView(rowFactory = rowFactory, detailsPresenter = presenter)

    view.render(controller.state.value)

    assertThat(rowFactory.selectedOccurrences()).containsExactlyInAnyOrder(installedSelection, bundledSelection)
    assertThat(presenter.selectedOccurrences).containsExactly(installedSelection, bundledSelection)
  }

  @Test
  fun `selection at realized boundary exposes the next keyboard chunk`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 250))
    controller.setSectionExpanded(PluginSectionId.Installed, true)
    val view = createView()
    view.render(controller.state.value)
    prepareForScrolling(view)

    val installedList = sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))
    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    val initialViewHeight = scrollPane.viewport.view.height
    assertThat(installedList.model.size).isEqualTo(100)

    installedList.selectedIndex = 99

    assertThat(installedList.model.size).isEqualTo(200)
    assertThat(installedList.selectedValue).isEqualTo(item("plugin.100"))
    assertThat(scrollPane.viewport.view.height).isEqualTo(initialViewHeight)
  }

  @Test
  fun `section pane and header keep fixed geometry when sticky`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 10))
    controller.setSectionExpanded(PluginSectionId.Installed, true)
    val view = createView()
    view.render(controller.state.value)
    prepareForScrolling(view)

    val installedTitle = IdeBundle.message("plugin.manager.tab.installed")
    val installedHeader = sectionHeader(view, installedTitle) as JComponent
    val installedList = sectionList(view, installedTitle)
    val sectionsPanel = installedList.parent.parent as JComponent
    val expectedInsets = Insets(JBUI.scale(8), JBUI.scale(16), JBUI.scale(4), JBUI.scale(12))
    assertThat(sectionsPanel.insets).isEqualTo(Insets(0, 0, 0, 0))
    assertThat(installedHeader.preferredSize.height).isEqualTo(JBUI.scale(40))
    assertThat(installedHeader.minimumSize.height).isEqualTo(JBUI.scale(40))
    assertThat(installedHeader.maximumSize.height).isEqualTo(JBUI.scale(40))
    assertThat(installedHeader.insets).isEqualTo(expectedInsets)

    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    scrollToRow(scrollPane, installedList, 2)

    assertThat(componentY(installedHeader, scrollPane.parent)).isEqualTo(0)
    assertThat(sectionHeader(view, installedTitle)).isSameAs(installedHeader)
    assertThat(installedHeader.height).isEqualTo(JBUI.scale(40))
    assertThat(installedHeader.insets).isEqualTo(expectedInsets)
  }

  @Test
  fun `overlapping scrollbar keeps the viewport width`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 1))
    val view = createView()
    view.render(controller.state.value)
    prepareForScrolling(view)

    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    assertThat(scrollPane.isOverlappingScrollBar).isTrue()
    assertThat(scrollPane.verticalScrollBar.isVisible).isFalse()
    val viewportWidth = scrollPane.viewport.width

    controller.updateSection(section(PluginSectionId.Installed, itemCount = 20))
    controller.setSectionExpanded(PluginSectionId.Installed, true)
    view.render(controller.state.value)
    layoutRecursively(view.component)

    assertThat(scrollPane.verticalScrollBar.isVisible).isTrue()
    assertThat(scrollPane.verticalScrollBar.isOpaque).isFalse()
    assertThat(scrollPane.viewport.width).isEqualTo(viewportWidth)
    val initialScrollBarBounds = scrollPane.verticalScrollBar.bounds
    assertThat(initialScrollBarBounds.y).isEqualTo(JBUI.scale(40))

    val installedTitle = IdeBundle.message("plugin.manager.tab.installed")
    val installedList = sectionList(view, installedTitle)
    scrollToRow(scrollPane, installedList, 2)

    val stickyHeader = sectionHeader(view, installedTitle)
    val stickyBounds = SwingUtilities.convertRectangle(
      stickyHeader,
      Rectangle(0, 0, stickyHeader.width, stickyHeader.height),
      scrollPane.parent,
    )
    val scrollBar = scrollPane.verticalScrollBar
    val scrollBarBounds = SwingUtilities.convertRectangle(scrollBar.parent, scrollBar.bounds, scrollPane.parent)
    assertThat(scrollBar.bounds).isEqualTo(initialScrollBarBounds)
    assertThat(stickyBounds.width).isEqualTo(scrollPane.viewport.width)
    assertThat(stickyBounds.x + stickyBounds.width).isGreaterThan(scrollBarBounds.x)
    assertThat(scrollBarBounds.y).isEqualTo(stickyBounds.y + stickyBounds.height)
    assertThat(stickyBounds.intersects(scrollBarBounds)).isFalse()
  }

  @Test
  fun `section pane top border continues across the splitter`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val view = createView()
    view.render(UnifiedPluginsPageController().state.value)
    prepareForScrolling(view)

    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    val listPanel = scrollPane.parent.parent as JComponent
    val border = listPanel.border
    assertThat(border).isInstanceOf(CustomLineBorder::class.java)
    assertThat(border.getBorderInsets(listPanel)).isEqualTo(Insets(JBUI.scale(1), 0, 0, 0))
    assertThat(listPanel.minimumSize.width).isEqualTo(JBUI.scale(280))

    val splitter = view.component as OnePixelSplitter
    splitter.setProportion(0.1f)
    layoutRecursively(splitter)
    assertThat(listPanel.width).isEqualTo(JBUI.scale(280))

    val image = BufferedImage(2, JBUI.scale(1) + 1, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      border.paintBorder(listPanel, graphics, 0, 0, image.width, image.height)
    }
    finally {
      graphics.dispose()
    }
    assertThat(image.getRGB(0, 0)).isEqualTo(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR.rgb)

    val divider = componentsOfType(view.component, Divider::class.java).single()
    assertThat(divider.background).isSameAs(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR)
  }

  @Test
  fun `sticky header is pushed off by the next section without duplicating controls`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UI) {
      val controller = UnifiedPluginsPageController()
      controller.updateSections(
        listOf(
          section(PluginSectionId.Installed, itemCount = 10),
          section(PluginSectionId.Bundled, itemCount = 10),
        )
      )
      controller.setSectionExpanded(PluginSectionId.Installed, true)
      controller.setSectionExpanded(PluginSectionId.Bundled, true)
      val view = createView()
      view.render(controller.state.value)
      prepareForScrolling(view)

      val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
      val installedTitle = IdeBundle.message("plugin.manager.tab.installed")
      val bundledTitle = IdeBundle.message("plugins.configurable.bundled")
      val installedHeader = sectionHeader(view, installedTitle)
      val bundledHeader = sectionHeader(view, bundledTitle)
      val stickyHeaderHost = installedHeader.parent as JComponent
      val installedExpansionLink = componentsOfType(installedHeader, ActionLink::class.java).single()
      val installedList = sectionList(view, installedTitle)
      val initialScrollBarBounds = scrollPane.verticalScrollBar.bounds
      assertThat(initialScrollBarBounds.y).isEqualTo(JBUI.scale(40))
      scrollToRow(scrollPane, installedList, 2)

      assertThat(componentY(installedHeader, scrollPane.parent)).isEqualTo(0)
      assertThat(scrollPane.verticalScrollBar.bounds).isEqualTo(initialScrollBarBounds)
      assertThat(sectionHeader(view, installedTitle)).isSameAs(installedHeader)
      assertThat(componentsOfType(installedHeader, ActionLink::class.java).single()).isSameAs(installedExpansionLink)
      assertThat(componentsOfType(view.component, JBLabel::class.java).count { it.text == installedTitle }).isEqualTo(1)
      assertThat(paintedPixel(stickyHeaderHost, stickyHeaderHost.width / 2, installedHeader.height - 1))
        .isEqualTo(PluginManagerConfigurable.MAIN_BG_COLOR.rgb)
      assertThat(paintedAlpha(stickyHeaderHost, stickyHeaderHost.width / 2, installedHeader.height)).isGreaterThan(0)

      val bundledHeaderY = componentY(bundledHeader, scrollPane.viewport.view)
      val overlap = installedHeader.preferredSize.height / 2
      scrollPane.viewport.viewPosition = Point(0, bundledHeaderY - overlap)

      assertThat(componentY(installedHeader, scrollPane.parent)).isEqualTo(overlap - installedHeader.preferredSize.height)
      assertThat(componentY(bundledHeader, scrollPane.parent)).isEqualTo(overlap)
      assertThat(scrollPane.verticalScrollBar.bounds).isEqualTo(initialScrollBarBounds)
      assertThat(paintedPixel(stickyHeaderHost, stickyHeaderHost.width / 2, installedHeader.height - 1))
        .isEqualTo(JBColor.border().rgb)
      assertThat(paintedAlpha(stickyHeaderHost, stickyHeaderHost.width / 2, installedHeader.height)).isZero()

      scrollPane.viewport.viewPosition = Point(0, bundledHeaderY)

      assertThat(componentY(bundledHeader, scrollPane.parent)).isEqualTo(0)
      assertThat(sectionHeader(view, bundledTitle)).isSameAs(bundledHeader)
      assertThat(scrollPane.verticalScrollBar.bounds).isEqualTo(initialScrollBarBounds)
    }

  @Test
  fun `sticky header paints an eight pixel gradient without moving the scrollbar`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UI) {
      val controller = UnifiedPluginsPageController()
      controller.updateSection(section(PluginSectionId.Installed, itemCount = 20))
      controller.setSectionExpanded(PluginSectionId.Installed, true)
      val view = createView()
      view.render(controller.state.value)
      prepareForScrolling(view)

      val installedTitle = IdeBundle.message("plugin.manager.tab.installed")
      val installedHeader = sectionHeader(view, installedTitle) as JComponent
      val installedList = sectionList(view, installedTitle)
      val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
      val initialScrollBarBounds = scrollPane.verticalScrollBar.bounds
      scrollToRow(scrollPane, installedList, 2)

      val stickyHeaderHost = installedHeader.parent as JComponent
      scrollPane.viewport.viewPosition = Point()
      assertThat(paintedAlpha(stickyHeaderHost, stickyHeaderHost.width / 2, installedHeader.height)).isZero()
      scrollToRow(scrollPane, installedList, 2)

      assertThat(installedHeader.height).isEqualTo(JBUI.scale(40))
      assertThat(stickyHeaderHost.height - installedHeader.height).isEqualTo(JBUI.scale(8))
      assertThat(scrollPane.verticalScrollBar.bounds).isEqualTo(initialScrollBarBounds)
      assertThat(stickyHeaderHost.contains(stickyHeaderHost.width / 2, installedHeader.height - 1)).isTrue()
      assertThat(stickyHeaderHost.contains(stickyHeaderHost.width / 2, installedHeader.height)).isFalse()

      val image = paintedImage(stickyHeaderHost)
      val x = stickyHeaderHost.width / 2
      val gradientTop = Color(image.getRGB(x, installedHeader.height), true)
      val gradientBottom = Color(image.getRGB(x, stickyHeaderHost.height - 1), true)
      assertThat(gradientTop.rgb).isEqualTo(PluginManagerConfigurable.MAIN_BG_COLOR.rgb)
      assertThat(gradientTop.alpha).isEqualTo(255)
      assertThat(gradientTop.alpha).isGreaterThan(gradientBottom.alpha)
      assertThat(Color(image.getRGB(stickyHeaderHost.width - 1, installedHeader.height), true).alpha).isEqualTo(gradientTop.alpha)
    }

  @Test
  fun `sticky expansion control collapses its section`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val controller = UnifiedPluginsPageController()
    controller.updateSections(
      listOf(
        section(PluginSectionId.Installed, itemCount = 20),
        section(PluginSectionId.Bundled, itemCount = 20),
      )
    )
    controller.setSectionExpanded(PluginSectionId.Installed, true)
    controller.setSectionExpanded(PluginSectionId.Bundled, true)
    lateinit var view: UnifiedPluginsPageView
    view = createView(onExpansionChanged = { id, expanded ->
      controller.setSectionExpanded(id, expanded)
      view.render(controller.state.value)
    })
    view.render(controller.state.value)
    prepareForScrolling(view)

    val installedTitle = IdeBundle.message("plugin.manager.tab.installed")
    val installedList = sectionList(view, installedTitle)
    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    scrollToRow(scrollPane, installedList, 5)
    val stickyHeader = sectionHeader(view, installedTitle)
    val expansionLink = componentsOfType(stickyHeader, ActionLink::class.java).single()

    assertThat(componentY(stickyHeader, scrollPane.parent)).isEqualTo(0)
    assertThat(expansionLink.text).isEqualTo(IdeBundle.message("plugins.configurable.show.less"))
    assertThat(expansionLink.icon).isSameAs(AllIcons.General.ChevronUp)

    expansionLink.doClick()

    assertThat(installedList.model.size).isEqualTo(PluginSectionState.COLLAPSED_ITEM_LIMIT)
    assertThat(expansionLink.text).isEqualTo(IdeBundle.message("plugins.configurable.show.more"))
    assertThat(expansionLink.icon).isSameAs(AllIcons.General.ChevronDown)
  }

  @Test
  fun `marketplace replaces suggested when query changes`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val controller = UnifiedPluginsPageController(
      listOf(
        section(PluginSectionId.Suggested, "suggested.plugin"),
        section(PluginSectionId.Marketplace, "marketplace.plugin"),
      )
    )
    val view = createView()
    view.render(controller.state.value)

    assertThat(sectionLists(view).map { it.accessibleContext.accessibleName })
      .contains(IdeBundle.message("plugins.configurable.suggested"))
      .doesNotContain(IdeBundle.message("plugin.manager.tab.marketplace"))

    controller.setQuery(PluginsQueryState("kotlin", "kotlin", 1))
    view.render(controller.state.value)

    assertThat(sectionLists(view).map { it.accessibleContext.accessibleName })
      .contains(IdeBundle.message("plugin.manager.tab.marketplace"))
      .doesNotContain(IdeBundle.message("plugins.configurable.suggested"))
    assertThat(sectionList(view, IdeBundle.message("plugin.manager.tab.marketplace")).model.getElementAt(0))
      .isEqualTo(item("marketplace.plugin"))
  }

  @Test
  fun `selection changes emit occurrence without render feedback`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val selections = ArrayList<List<PluginOccurrenceId>>()
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, "first.plugin", "second.plugin"))
    val view = createView(onSelectionChanged = selections::add)

    view.render(controller.state.value)

    val installedList = sectionList(view, IdeBundle.message("plugin.manager.tab.installed"))
    assertThat(installedList.selectedIndex).isEqualTo(0)
    assertThat(selections).isEmpty()
    assertThat(componentsOfType(view.component, JBLabel::class.java).map { it.text })
      .contains(IdeBundle.message("plugins.configurable.details.none.selected"))

    installedList.selectedIndex = 1
    assertThat(selections).containsExactly(listOf(
      PluginOccurrenceId(PluginSectionId.Installed, PluginId.getId("second.plugin"))
    ))

    controller.selectOccurrence(null)
    view.render(controller.state.value)
    assertThat(installedList.selectedIndex).isEqualTo(-1)
    assertThat(selections).hasSize(1)
  }

  @Test
  fun `search emits user edits but not rendered query`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val queries = ArrayList<String>()
    val controller = UnifiedPluginsPageController()
    controller.setQuery(PluginsQueryState("kotlin", "kotlin", 1))
    val view = createView(onSearchChanged = queries::add)

    view.render(controller.state.value)

    assertThat(componentsOfType(view.component, SearchTextField::class.java)).isEmpty()
    val searchField = componentsOfType(view.searchComponent, SearchTextField::class.java).single()
    assertThat(searchField.text).isEqualTo("kotlin")
    assertThat(queries).isEmpty()

    searchField.text = "java"
    assertThat(queries.last()).isEqualTo("java")
  }

  @Test
  fun `search history popup is enabled and history persists across views`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val properties = PropertiesComponent.getInstance()
    val previousHistory = properties.getValue(SEARCH_HISTORY_PROPERTY)
    try {
      properties.unsetValue(SEARCH_HISTORY_PROPERTY)
      val firstView = createView()
      val firstField = componentsOfType(firstView.searchComponent, SearchTextField::class.java).single()

      assertThat(firstField.textEditor.getClientProperty("History.Popup.Enabled")).isEqualTo(true)
      assertThat(ActionUtil.getActions(firstField.textEditor)).isNotEmpty()

      firstField.text = "kotlin"
      firstField.addCurrentTextToHistory()

      assertThat(properties.getValue(SEARCH_HISTORY_PROPERTY)).isEqualTo("kotlin")
      val secondView = createView()
      val secondField = componentsOfType(secondView.searchComponent, SearchTextField::class.java).single()
      assertThat(secondField.history).containsExactly("kotlin")
    }
    finally {
      restoreSearchHistory(properties, previousHistory)
    }
  }

  @Test
  fun `search history shortcuts navigate queries through the normal callback`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val properties = PropertiesComponent.getInstance()
    val previousHistory = properties.getValue(SEARCH_HISTORY_PROPERTY)
    try {
      properties.setValue(SEARCH_HISTORY_PROPERTY, "java\nkotlin")
      val queries = ArrayList<String>()
      val view = createView(onSearchChanged = queries::add)
      val searchField = componentsOfType(view.searchComponent, SearchTextField::class.java).single()
      val previousActionKey = searchField.textEditor.inputMap.get(SearchTextField.ALT_SHOW_HISTORY_KEYSTROKE)
      val nextActionKey = searchField.textEditor.inputMap.get(SearchTextField.SHOW_HISTORY_KEYSTROKE)

      assertThat(previousActionKey).isNotNull()
      assertThat(nextActionKey).isNotNull()

      searchField.textEditor.actionMap.get(previousActionKey)
        .actionPerformed(ActionEvent(searchField.textEditor, ActionEvent.ACTION_PERFORMED, "previous history"))
      assertThat(searchField.text).isEqualTo("kotlin")
      assertThat(queries.last()).isEqualTo("kotlin")

      searchField.textEditor.actionMap.get(nextActionKey)
        .actionPerformed(ActionEvent(searchField.textEditor, ActionEvent.ACTION_PERFORMED, "next history"))
      assertThat(searchField.text).isEqualTo("java")
      assertThat(queries.last()).isEqualTo("java")
    }
    finally {
      restoreSearchHistory(properties, previousHistory)
    }
  }

  @Test
  fun `search renders placeholder and ordered focusable controls`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val view = createView()
    val base = UnifiedPluginsPageController().state.value
    val selectedFilters = UnifiedPluginsSearchControlsState(
      selectedCategories = setOf("Programming Language"),
    )

    view.render(base.copy(searchControls = selectedFilters))

    assertThat(view.searchComponent).isInstanceOf(SearchFieldWithExtension::class.java)
    val searchField = componentsOfType(view.searchComponent, SearchTextField::class.java).single()
    assertThat(searchField.textEditor.emptyText.text).isEqualTo(IdeBundle.message("plugin.manager.search.all.plugins"))
    val filterOnly = searchControlButtons(view)
    assertThat(filterOnly).hasSize(1)
    assertThat(filterOnly.single().isFocusable).isTrue()
    assertThat(filterOnly.single().isSelected).isTrue()
    assertThat(filterOnly.single().accessibleContext.accessibleName)
      .isEqualTo(IdeBundle.message("plugins.configurable.filter.plugins"))

    val withSort = selectedFilters.copy(
      effectiveSort = com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions.DOWNLOADS,
      sortVisible = true,
    )
    view.render(base.copy(searchControls = withSort))

    val controls = searchControlButtons(view)
    assertThat(controls).hasSize(2)
    assertThat(controls).allMatch(ActionButton::isFocusable)
    assertThat(controls.map { it.accessibleContext.accessibleName }).containsExactly(
      IdeBundle.message("plugins.configurable.sort.marketplace.results", "Downloads"),
      IdeBundle.message("plugins.configurable.filter.plugins"),
    )
    assertThat(controls.first().presentation.icon).isSameAs(AllIcons.General.SortBy)
    assertThat(controls.last()).isSameAs(filterOnly.single())
  }

  @Test
  fun `search emits user edit delivered while sections render`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val queries = ArrayList<String>()
    val controller = UnifiedPluginsPageController(listOf(section(PluginSectionId.Installed, itemCount = 1)))
    val view = createView(onSearchChanged = queries::add)
    view.render(controller.state.value)
    val searchField = componentsOfType(view.searchComponent, SearchTextField::class.java).single()
    val sectionsPanel = checkNotNull(sectionLists(view).first().parent.parent)
    sectionsPanel.addContainerListener(object : ContainerAdapter() {
      override fun componentAdded(event: ContainerEvent) {
        searchField.text = "java"
      }
    })

    controller.updateSection(section(PluginSectionId.Installing, itemCount = 1))
    view.render(controller.state.value)

    assertThat(queries).containsExactly("java")
  }

  private fun createView(
    onSearchChanged: (String) -> Unit = {},
    onSelectionChanged: (List<PluginOccurrenceId>) -> Unit = {},
    onExpansionChanged: (PluginSectionId, Boolean) -> Unit = { _, _ -> },
    onRetryRequested: (PluginSectionId) -> Unit = {},
    rowFactory: PluginRowFactory? = null,
    detailsPresenter: PluginDetailsPresenter? = null,
  ): UnifiedPluginsPageView {
    return UnifiedPluginsPageView(
      onSearchChanged,
      onSelectionChanged,
      onExpansionChanged,
      rowFactory,
      detailsPresenter,
      onSectionRetryRequested = onRetryRequested,
    )
  }

  private fun searchControlButtons(view: UnifiedPluginsPageView): List<ActionButton> {
    return componentsOfType(view.searchComponent, ActionButton::class.java).filter(ActionButton::isVisible)
  }

  private fun restoreSearchHistory(properties: PropertiesComponent, value: String?) {
    if (value == null) {
      properties.unsetValue(SEARCH_HISTORY_PROPERTY)
    }
    else {
      properties.setValue(SEARCH_HISTORY_PROPERTY, value)
    }
  }

  private fun section(
    id: PluginSectionId,
    itemCount: Int,
    title: @Nls String? = null,
  ): PluginSectionState {
    return PluginSectionState(id = id, title = title, items = (1..itemCount).map { item("plugin.$it") })
  }

  private fun section(id: PluginSectionId, vararg pluginIds: String): PluginSectionState {
    return PluginSectionState(id = id, items = pluginIds.map(::item))
  }

  private fun item(pluginId: String): PluginItemState {
    return PluginItemState(PluginId.getId(pluginId), pluginId)
  }

  private fun realItem(pluginId: String): PluginItemState {
    val model = PluginDto(pluginId, PluginId.getId(pluginId))
    return PluginItemState(model.pluginId, model.name, modelHandle = PluginItemModelHandle(model))
  }

  private fun UnifiedPluginsPageState.section(id: PluginSectionId): PluginSectionState {
    return sections.single { it.id == id }
  }

  private fun sectionLists(view: UnifiedPluginsPageView): List<JBList<*>> {
    return componentsOfType(view.component, JBList::class.java)
  }

  private fun sectionList(view: UnifiedPluginsPageView, title: String): JBList<*> {
    return sectionLists(view).single { it.accessibleContext.accessibleName == title }
  }

  private fun sectionHeader(view: UnifiedPluginsPageView, title: String): Component {
    val titleLabel = componentsOfType(view.component, JBLabel::class.java).single { it.text == title }
    return checkNotNull(titleLabel.parent?.parent)
  }

  private fun prepareForScrolling(view: UnifiedPluginsPageView) {
    view.component.setSize(900, 320)
    layoutRecursively(view.component)
  }

  private fun layoutRecursively(component: Component) {
    if (component !is Container) return
    component.doLayout()
    component.components.forEach(::layoutRecursively)
  }

  private fun scrollToRow(scrollPane: JBScrollPane, list: JBList<*>, index: Int) {
    val bounds = checkNotNull(list.getCellBounds(index, index))
    val relativeBounds = SwingUtilities.convertRectangle(list, bounds, scrollPane.viewport.view)
    scrollPane.viewport.viewPosition = Point(0, relativeBounds.y - EXPECTED_ANCHOR_OFFSET)
  }

  private fun rowOffset(scrollPane: JBScrollPane, list: JBList<*>, index: Int): Int {
    val bounds = checkNotNull(list.getCellBounds(index, index))
    val relativeBounds = SwingUtilities.convertRectangle(list, bounds, scrollPane.viewport.view)
    return relativeBounds.y - scrollPane.viewport.viewPosition.y
  }

  private fun componentY(component: Component, relativeTo: Component): Int {
    return SwingUtilities.convertPoint(component, Point(), relativeTo).y
  }

  private fun paintedPixel(component: JComponent, x: Int, y: Int): Int {
    return paintedImage(component).getRGB(x, y)
  }

  private fun paintedAlpha(component: JComponent, x: Int, y: Int): Int {
    return Color(paintedPixel(component, x, y), true).alpha
  }

  private fun paintedImage(component: JComponent): BufferedImage {
    val image = BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      component.paint(graphics)
    }
    finally {
      graphics.dispose()
    }
    return image
  }

  private fun <T : Component> componentsOfType(root: Component, type: Class<T>): List<T> {
    val result = ArrayList<T>()

    fun visit(component: Component) {
      if (type.isInstance(component)) {
        result.add(type.cast(component))
      }
      if (component is Container) {
        component.components.forEach(::visit)
      }
    }

    visit(root)
    return result
  }

  private fun sectionDividerVisibility(view: UnifiedPluginsPageView): List<Boolean> {
    return sectionLists(view).map { list ->
      val section = list.parent as JComponent
      (section.border?.getBorderInsets(section)?.top ?: 0) > 0
    }
  }

  private fun sectionGaps(view: UnifiedPluginsPageView): List<Int> {
    val sections = sectionLists(view).map { it.parent as JComponent }
    return sections.zipWithNext { first, second -> second.y - first.y - first.height }
  }

  private class RecordingPluginRowFactory : PluginRowFactory {
    private val rows = LinkedHashMap<PluginOccurrenceId, RecordingPluginRow>()

    override fun specification(section: PluginSectionState, item: PluginItemState): PluginRowSpecification<Any> {
      return PluginRowSpecification(section.occurrenceId(item.pluginId), item, Unit)
    }

    override fun createRow(occurrenceId: PluginOccurrenceId, item: PluginItemState, renderKey: Any): PluginRow {
      return RecordingPluginRow().also { rows[occurrenceId] = it }
    }

    override fun rowsRendered(bindings: List<PluginRowBinding<PluginRow>>) = Unit

    fun selectedOccurrences(): List<PluginOccurrenceId> {
      return rows.filterValues(RecordingPluginRow::selected).keys.toList()
    }
  }

  private class RecordingPluginRow : PluginRow {
    override val component: JComponent = JPanel()
    var selected: Boolean = false

    override fun renderSelection(selected: Boolean) {
      this.selected = selected
    }

    override fun close() = Unit
  }

  private class RecordingPluginDetailsPresenter : PluginDetailsPresenter {
    override val component: JComponent = JPanel()
    var selectedOccurrences: List<PluginOccurrenceId> = emptyList()

    override fun render(mode: PluginDetailsMode, selection: List<PluginDetailsSelection>) {
      selectedOccurrences = selection.map(PluginDetailsSelection::occurrenceId)
    }

    override fun beforeRowRelease(occurrenceId: PluginOccurrenceId, row: PluginRow) = Unit

    override fun close() = Unit
  }

}
