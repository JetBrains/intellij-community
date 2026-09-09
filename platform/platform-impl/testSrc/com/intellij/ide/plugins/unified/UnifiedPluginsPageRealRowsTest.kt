// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.UI
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

@TestApplication
@Timeout(30)
internal class UnifiedPluginsPageRealRowsTest {
  companion object {
    private const val ANCHOR_OFFSET = 8
    private const val ROW_HEIGHT = 36

    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      LafManager.getInstance()
    }
  }

  @Test
  fun `rows are reused by model and render key and closed once`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val factory = RecordingRowFactory()
    val firstItem = item("first.plugin")
    val secondItem = item("second.plugin")
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, firstItem, secondItem))
    val view = createView(factory)

    view.render(controller.state.value)
    val firstRow = factory.row(PluginSectionId.Installed, firstItem)
    val secondRow = factory.row(PluginSectionId.Installed, secondItem)
    val rowsPanel = checkNotNull(firstRow.component.parent)
    assertThat(firstRow.selected).isTrue()
    assertThat(firstRow.component.parent).isNotNull()

    controller.updateSection(
      section(
        PluginSectionId.Installed,
        firstItem.copy(contentRevision = 1),
        secondItem.copy(contentRevision = 1),
      )
    )
    view.render(controller.state.value)
    assertThat(factory.row(PluginSectionId.Installed, firstItem)).isSameAs(firstRow)
    assertThat(factory.row(PluginSectionId.Installed, secondItem)).isSameAs(secondRow)

    controller.updateSection(section(PluginSectionId.Installed, secondItem, firstItem))
    view.render(controller.state.value)

    assertThat(factory.row(PluginSectionId.Installed, firstItem)).isSameAs(firstRow)
    assertThat(factory.row(PluginSectionId.Installed, secondItem)).isSameAs(secondRow)
    assertThat(rowsPanel.components).containsExactly(secondRow.component, firstRow.component)

    val renamedFirstItem = firstItem.copy(name = "Renamed")
    controller.updateSection(section(PluginSectionId.Installed, renamedFirstItem))
    view.render(controller.state.value)
    val replacement = factory.row(PluginSectionId.Installed, renamedFirstItem)

    assertThat(replacement).isNotSameAs(firstRow)
    assertThat(firstRow.closeCount).isEqualTo(1)
    assertThat(secondRow.closeCount).isEqualTo(1)
    assertThat(firstRow.component.parent).isNull()
    assertThat(secondRow.component.parent).isNull()
    assertThat(replacement.component.parent).isNotNull()
    assertThat(rowsPanel.components).containsExactly(replacement.component)

    view.close()
    view.close()
    assertThat(replacement.closeCount).isEqualTo(1)
    assertThat(factory.renderedOccurrences).isEmpty()
  }

  @Test
  fun `expanded sections grow and retain real row prefixes while scrolling`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val factory = RecordingRowFactory()
    val items = (1..250).map { item("plugin.$it") }
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, *items.toTypedArray()))
    controller.setSectionExpanded(PluginSectionId.Installed, true)
    val view = createView(factory)
    view.render(controller.state.value)
    prepareForScrolling(view)

    assertThat(factory.activeRows).hasSize(100)
    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    val firstRow = factory.activeRows.values.first().component
    val rowsTop = SwingUtilities.convertPoint(firstRow.parent, Point(), scrollPane.viewport.view).y
    scrollPane.viewport.viewPosition = Point(0, rowsTop + 120 * ROW_HEIGHT)

    assertThat(factory.activeRows).hasSize(200)
    assertThat(factory.row(PluginSectionId.Installed, item("plugin.121"))).isNotNull()

    scrollPane.viewport.viewPosition = Point(0, rowsTop + 220 * ROW_HEIGHT)
    assertThat(factory.activeRows).hasSize(250)
    scrollPane.viewport.viewPosition = Point()
    assertThat(factory.activeRows).hasSize(250)
    assertThat(factory.activeRows.keys.map(PluginOccurrenceId::pluginId)).contains(items.last().pluginId)
    view.close()
  }

  @Test
  fun `expanded sections retain their realized prefix across item revisions`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val factory = RecordingRowFactory()
    val items = (1..250).map { item("plugin.$it") }
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, *items.toTypedArray()))
    controller.setSectionExpanded(PluginSectionId.Installed, true)
    val view = createView(factory)
    view.render(controller.state.value)
    prepareForScrolling(view)

    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    val firstRow = factory.activeRows.values.first().component
    val rowsTop = SwingUtilities.convertPoint(firstRow.parent, Point(), scrollPane.viewport.view).y
    scrollPane.viewport.viewPosition = Point(0, rowsTop + 120 * ROW_HEIGHT)
    val retainedRow = factory.row(PluginSectionId.Installed, items[120])
    assertThat(factory.activeRows).hasSize(200)

    controller.updateSection(
      section(PluginSectionId.Installed, *items.map { it.copy(contentRevision = 1) }.toTypedArray())
    )
    view.render(controller.state.value)

    assertThat(factory.activeRows).hasSize(200)
    assertThat(factory.row(PluginSectionId.Installed, items[120])).isSameAs(retainedRow)
    view.close()
  }

  @Test
  fun `Bundled category headers follow expansion and query state`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val factory = RecordingRowFactory()
    val items = listOf(
      item("first.language", "Languages"),
      item("second.language", "Languages"),
      item("first.tool", "Tools"),
      item("second.tool", "Tools"),
    )
    val controller = UnifiedPluginsPageController(
      listOf(PluginSectionState(PluginSectionId.Bundled, items = items))
    )
    val view = createView(factory)

    view.render(controller.state.value)
    assertThat(categoryHeaders(view)).isEmpty()

    controller.setSectionExpanded(PluginSectionId.Bundled, true)
    view.render(controller.state.value)

    val headers = categoryHeaders(view)
    assertThat(headers.map { it.accessibleContext.accessibleName }).containsExactly("Languages", "Tools")
    headers.forEach { header ->
      assertThat(header.isOpaque).isFalse()
      assertThat(header.preferredSize.height).isEqualTo(JBUI.scale(40))
      assertThat(header.insets.top).isEqualTo(JBUI.scale(8))
      assertThat(header.insets.right).isEqualTo(JBUI.scale(12))
      assertThat(header.insets.bottom).isEqualTo(JBUI.scale(4))
      assertThat(header.insets.left).isEqualTo(JBUI.scale(16))
      assertThat(componentsOfType(header, JBLabel::class.java).single().font.style).isEqualTo(Font.PLAIN)
    }
    assertThat(headers.map { componentsOfType(it, ActionLink::class.java).single().text })
      .containsOnly(IdeBundle.message("plugins.configurable.enable.all"))
    assertThat(headers).allSatisfy { header ->
      val action = componentsOfType(header, ActionLink::class.java).single()
      assertThat(action.isFocusable).isTrue()
      assertThat(action.accessibleContext.accessibleName).isEqualTo(action.text)
    }

    controller.setQuery(PluginsQueryState("language", "language", 1))
    view.render(controller.state.value)
    assertThat(categoryHeaders(view)).isEmpty()
    view.close()
  }

  @Test
  fun `Bundled promotion appears after its category header only while expanded`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UI) {
      val factory = RecordingRowFactory()
      val tool = item("first.tool", "Tools")
      val language = item("first.language", "Languages")
      val controller = UnifiedPluginsPageController(
        initialSections = listOf(PluginSectionState(PluginSectionId.Bundled, items = listOf(tool, language))),
        priorityBundledCategories = setOf("Tools"),
      )
      var creationCount = 0
      val promotionPanel = JPanel().apply { name = "Tools promotion" }
      val view = createView(
        factory = factory,
        createBundledCategoryPromotion = { category ->
          creationCount++
          promotionPanel.takeIf { category == "Tools" }
        },
      )

      view.render(controller.state.value)
      assertThat(creationCount).isZero()
      assertThat(promotionPanel.parent).isNull()

      controller.setSectionExpanded(PluginSectionId.Bundled, true)
      view.render(controller.state.value)

      val toolsHeader = categoryHeaders(view).single { it.accessibleContext.accessibleName == "Tools" }
      val toolRow = factory.row(PluginSectionId.Bundled, tool).component
      assertThat(promotionPanel.parent).isSameAs(toolsHeader.parent)
      assertThat(toolsHeader.parent.components.toList()).containsSubsequence(toolsHeader, promotionPanel, toolRow)
      assertThat(creationCount).isEqualTo(2)

      view.render(controller.state.value)
      assertThat(creationCount).isEqualTo(2)

      controller.setSectionExpanded(PluginSectionId.Bundled, false)
      view.render(controller.state.value)
      assertThat(promotionPanel.parent).isNull()

      controller.setSectionExpanded(PluginSectionId.Bundled, true)
      controller.setQuery(PluginsQueryState("tool", "tool", 1))
      view.render(controller.state.value)
      assertThat(promotionPanel.parent).isNull()
      assertThat(creationCount).isEqualTo(2)
      view.close()
    }

  @Test
  fun `Bundled category action includes unrealized rows and later categories appear while scrolling`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UI) {
      val factory = RecordingRowFactory()
      val languages = (1..120).map { item("language.$it", "Languages") }
      val tools = (1..30).map { item("tool.$it", "Tools") }
      val controller = UnifiedPluginsPageController(
        listOf(PluginSectionState(PluginSectionId.Bundled, items = languages + tools))
      )
      controller.setSectionExpanded(PluginSectionId.Bundled, true)
      val actions = ArrayList<BundledPluginCategoryGroupState>()
      val view = createView(factory, onBundledCategoryAction = actions::add)
      view.render(controller.state.value)
      prepareForScrolling(view)

      assertThat(factory.activeRows).hasSize(100)
      assertThat(categoryHeaders(view).map { it.accessibleContext.accessibleName }).containsExactly("Languages")
      componentsOfType(categoryHeaders(view).single(), ActionLink::class.java).single().doClick()
      assertThat(actions.single().pluginIds).containsExactlyElementsOf(languages.map(PluginItemState::pluginId))

      val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
      val firstRow = factory.activeRows.values.first().component
      val rowsTop = SwingUtilities.convertPoint(firstRow.parent, Point(), scrollPane.viewport.view).y
      scrollPane.viewport.viewPosition = Point(0, rowsTop + 130 * ROW_HEIGHT)

      assertThat(factory.activeRows).hasSize(150)
      assertThat(categoryHeaders(view).map { it.accessibleContext.accessibleName }).containsExactly("Languages", "Tools")

      val selectedItem = tools.first()
      controller.selectOccurrence(PluginOccurrenceId(PluginSectionId.Bundled, selectedItem.pluginId))
      view.render(controller.state.value)
      assertThat(factory.row(PluginSectionId.Bundled, selectedItem).selected).isTrue()
      view.close()
    }

  @Test
  fun `render realizes the selected occurrence in an expanded section`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val factory = RecordingRowFactory()
    val details = RecordingDetailsPresenter()
    val items = (1..250).map { item("plugin.$it") }
    val selectedItem = items[174]
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, *items.toTypedArray()))
    controller.setSectionExpanded(PluginSectionId.Installed, true)
    val view = createView(factory, details)
    view.render(controller.state.value)
    assertThat(factory.activeRows).hasSize(100)

    val selectedOccurrence = PluginOccurrenceId(PluginSectionId.Installed, selectedItem.pluginId)
    controller.selectOccurrence(selectedOccurrence)
    view.render(controller.state.value)

    assertThat(factory.activeRows).hasSize(200)
    assertThat(factory.row(PluginSectionId.Installed, selectedItem).selected).isTrue()
    assertThat(details.selection.single().occurrenceId).isEqualTo(selectedOccurrence)
    view.close()
  }

  @Test
  fun `selection at a real row chunk boundary realizes the next keyboard target`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UI) {
      val factory = RecordingRowFactory()
      val items = (1..250).map { item("plugin.$it") }
      val controller = UnifiedPluginsPageController()
      controller.updateSection(section(PluginSectionId.Installed, *items.toTypedArray()))
      controller.setSectionExpanded(PluginSectionId.Installed, true)
      val view = createView(factory)
      view.render(controller.state.value)
      assertThat(factory.activeRows).hasSize(100)

      controller.selectOccurrence(PluginOccurrenceId(PluginSectionId.Installed, items[99].pluginId))
      view.render(controller.state.value)

      assertThat(factory.activeRows).hasSize(200)
      assertThat(factory.row(PluginSectionId.Installed, items[100])).isNotNull()
      view.close()
    }

  @Test
  fun `compatible multi selection renders every row and details occurrence`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UI) {
      val factory = RecordingRowFactory()
      val details = RecordingDetailsPresenter()
      val first = item("first.plugin")
      val second = item("second.plugin")
      val section = section(PluginSectionId.Installed, first, second)
      val controller = UnifiedPluginsPageController(listOf(section))
      val occurrences = section.items.map { section.occurrenceId(it.pluginId) }
      controller.selectOccurrences(occurrences)
      val view = createView(factory, details)

      view.render(controller.state.value)

      assertThat(factory.row(PluginSectionId.Installed, first).selected).isTrue()
      assertThat(factory.row(PluginSectionId.Installed, second).selected).isTrue()
      assertThat(details.selection.map(PluginDetailsSelection::occurrenceId)).containsExactlyElementsOf(occurrences)
      view.close()
    }

  @Test
  fun `inserting a real-row section preserves visible occurrence offset`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val factory = RecordingRowFactory()
    val installedItems = (1..20).map { item("installed.$it") }
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, *installedItems.toTypedArray()))
    controller.setSectionExpanded(PluginSectionId.Installed, true)
    val view = createView(factory)
    view.render(controller.state.value)
    prepareForScrolling(view)

    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    val anchorItem = installedItems[9]
    val anchor = factory.row(PluginSectionId.Installed, anchorItem).component
    scrollToComponent(scrollPane, anchor)
    val initialOffset = componentOffset(scrollPane, anchor)

    controller.updateSection(section(PluginSectionId.Installing, *(1..4).map { item("installing.$it") }.toTypedArray()))
    view.render(controller.state.value)

    assertThat(factory.row(PluginSectionId.Installed, anchorItem).component).isSameAs(anchor)
    assertThat(componentOffset(scrollPane, anchor)).isEqualTo(initialOffset)
    view.close()
  }

  @Test
  fun `installing row height change preserves a later visible occurrence`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val factory = RecordingRowFactory()
    val installingItem = item("installing.plugin")
    val installedItems = (1..20).map { item("installed.$it") }
    val controller = UnifiedPluginsPageController()
    controller.updateSections(
      listOf(
        section(PluginSectionId.Installing, installingItem),
        section(PluginSectionId.Installed, *installedItems.toTypedArray()),
      )
    )
    controller.setSectionExpanded(PluginSectionId.Installed, true)
    val view = createView(factory)
    view.render(controller.state.value)
    prepareForScrolling(view)

    val scrollPane = componentsOfType(view.component, JBScrollPane::class.java).single()
    val anchor = factory.row(PluginSectionId.Installed, installedItems[9]).component
    scrollToComponent(scrollPane, anchor)
    val initialOffset = componentOffset(scrollPane, anchor)
    val initialViewY = scrollPane.viewport.viewPosition.y
    val installingRow = factory.row(PluginSectionId.Installing, installingItem)

    installingRow.setHeight(ROW_HEIGHT * 3)
    controller.updateSection(section(PluginSectionId.Installing, installingItem.copy(contentRevision = 1)))
    view.render(controller.state.value)

    assertThat(factory.row(PluginSectionId.Installing, installingItem)).isSameAs(installingRow)
    assertThat(componentOffset(scrollPane, anchor)).isEqualTo(initialOffset)
    assertThat(scrollPane.viewport.viewPosition.y).isGreaterThan(initialViewY)
    view.close()
  }

  @Test
  fun `filtered one-row sections do not shrink real rows below preferred height`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val factory = RecordingRowFactory()
    val firstItem = item("first.plugin")
    val secondItem = item("second.plugin")
    val controller = UnifiedPluginsPageController(
      listOf(
        section(PluginSectionId.Installed, firstItem, item("filtered.installed.plugin")),
        section(PluginSectionId.Bundled, secondItem, item("filtered.bundled.plugin")),
      )
    )
    val view = createView(factory)
    view.render(controller.state.value)
    prepareForScrolling(view)

    controller.updateSections(
      listOf(
        section(PluginSectionId.Installed, firstItem),
        section(PluginSectionId.Bundled, secondItem),
      )
    )
    view.render(controller.state.value)

    val firstRow = factory.row(PluginSectionId.Installed, firstItem).component
    val secondRow = factory.row(PluginSectionId.Bundled, secondItem).component
    val firstSection = firstRow.parent.parent as JComponent
    val secondSection = secondRow.parent.parent as JComponent
    val sectionsPanel = firstSection.parent as JComponent

    sectionsPanel.setSize(sectionsPanel.width, sectionsPanel.preferredSize.height - 1)
    layoutRecursively(sectionsPanel)

    val firstRowBounds = SwingUtilities.convertRectangle(firstRow, Rectangle(firstRow.size), firstSection)
    assertThat(firstSection.height).isEqualTo(firstSection.preferredSize.height)
    assertThat(firstRow.height).isEqualTo(firstRow.preferredSize.height)
    assertThat(firstRowBounds.y + firstRowBounds.height).isLessThanOrEqualTo(firstSection.height)
    assertThat(firstSection.y + firstSection.height).isLessThanOrEqualTo(secondSection.y)
    view.close()
  }

  @Test
  fun `installing rows reconstruct across collapse and expansion`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val factory = RecordingRowFactory()
    val items = (1..4).map { item("installing.$it") }
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installing, *items.toTypedArray()))
    val view = createView(factory)
    view.render(controller.state.value)

    assertThat(factory.activeRows.keys.map { it.pluginId }).containsExactlyElementsOf(items.map { it.pluginId })
    val expandedRow = factory.row(PluginSectionId.Installing, items.last())

    controller.setSectionExpanded(PluginSectionId.Installing, false)
    view.render(controller.state.value)
    assertThat(expandedRow.closeCount).isEqualTo(1)
    assertThat(factory.activeRows.keys.map { it.pluginId }).doesNotContain(items.last().pluginId)

    controller.setSectionExpanded(PluginSectionId.Installing, true)
    view.render(controller.state.value)
    assertThat(factory.row(PluginSectionId.Installing, items.last())).isNotSameAs(expandedRow)
    view.close()
  }

  @Test
  fun `details follow section mode and detach before selected rows close`(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    val factory = RecordingRowFactory()
    val details = RecordingDetailsPresenter()
    val controller = UnifiedPluginsPageController()
    val installedItem = item("installed.plugin")
    controller.updateSection(section(PluginSectionId.Installed, installedItem))
    val view = createView(factory, details)

    view.render(controller.state.value)
    val firstRow = factory.row(PluginSectionId.Installed, installedItem)
    assertThat(details.mode).isEqualTo(PluginDetailsMode.LOCAL)
    assertThat(details.selection.single().row).isSameAs(firstRow)

    val renamedItem = installedItem.copy(name = "Renamed")
    controller.updateSection(section(PluginSectionId.Installed, renamedItem))
    view.render(controller.state.value)
    val replacement = factory.row(PluginSectionId.Installed, renamedItem)

    assertThat(details.releaseCloseCounts).containsExactly(0)
    assertThat(firstRow.closeCount).isEqualTo(1)
    assertThat(details.selection.single().row).isSameAs(replacement)

    controller.updateSection(section(PluginSectionId.Installed))
    view.render(controller.state.value)
    assertThat(details.mode).isEqualTo(PluginDetailsMode.LOCAL)
    assertThat(details.selection).isEmpty()

    val marketplaceItem = item("marketplace.plugin")
    controller.setQuery(PluginsQueryState("marketplace", "marketplace", 1))
    controller.updateSection(section(PluginSectionId.Marketplace, marketplaceItem))
    view.render(controller.state.value)
    val marketplaceRow = factory.row(PluginSectionId.Marketplace, marketplaceItem)
    assertThat(details.mode).isEqualTo(PluginDetailsMode.MARKETPLACE)
    assertThat(details.selection.single().row).isSameAs(marketplaceRow)

    view.close()
    assertThat(details.closeCount).isEqualTo(1)
    assertThat(details.selectedRowCloseCountAtClose).isZero()
    assertThat(marketplaceRow.closeCount).isEqualTo(1)
  }

  private fun createView(
    factory: PluginRowFactory,
    details: PluginDetailsPresenter? = null,
    onBundledCategoryAction: (BundledPluginCategoryGroupState) -> Unit = {},
    createBundledCategoryPromotion: (String) -> JComponent? = { null },
  ): UnifiedPluginsPageView {
    return UnifiedPluginsPageView(
      {},
      {},
      { _, _ -> },
      factory,
      details,
      onBundledCategoryAction = onBundledCategoryAction,
      createBundledCategoryPromotion = createBundledCategoryPromotion,
    )
  }

  private fun section(id: PluginSectionId, vararg items: PluginItemState): PluginSectionState {
    return PluginSectionState(id = id, items = items.toList())
  }

  private fun item(pluginId: String, category: String? = null): PluginItemState {
    val id = PluginId.getId(pluginId)
    val model = PluginNodeModelBuilderFactory.createBuilder(id).setName(pluginId).build()
    return PluginItemState(id, pluginId, modelHandle = PluginItemModelHandle(model), searchCategory = category)
  }

  private fun categoryHeaders(view: UnifiedPluginsPageView): List<JPanel> {
    return componentsOfType(view.component, JPanel::class.java).filter { panel ->
      panel.accessibleContext.accessibleName == "Languages" || panel.accessibleContext.accessibleName == "Tools"
    }
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

  private fun scrollToComponent(scrollPane: JBScrollPane, component: JComponent) {
    val bounds = SwingUtilities.convertRectangle(component, Rectangle(0, 0, component.width, component.height), scrollPane.viewport.view)
    scrollPane.viewport.viewPosition = Point(0, bounds.y - ANCHOR_OFFSET)
  }

  private fun componentOffset(scrollPane: JBScrollPane, component: JComponent): Int {
    val point = SwingUtilities.convertPoint(component, Point(), scrollPane.viewport.view)
    return point.y - scrollPane.viewport.viewPosition.y
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

  private class RecordingRowFactory : PluginRowFactory {
    val activeRows = LinkedHashMap<PluginOccurrenceId, RecordingRow>()
    var renderedOccurrences: List<PluginOccurrenceId> = emptyList()
      private set

    override fun specification(section: PluginSectionState, item: PluginItemState): PluginRowSpecification<Any> {
      return PluginRowSpecification(section.occurrenceId(item.pluginId), item, item.name.orEmpty())
    }

    override fun createRow(occurrenceId: PluginOccurrenceId, item: PluginItemState, renderKey: Any): PluginRow {
      return RecordingRow().also { activeRows[occurrenceId] = it }
    }

    override fun rowsRendered(bindings: List<PluginRowBinding<PluginRow>>) {
      renderedOccurrences = bindings.map(PluginRowBinding<PluginRow>::occurrenceId)
      val activeOccurrences = renderedOccurrences.toSet()
      activeRows.keys.removeIf { it !in activeOccurrences }
    }

    fun row(sectionId: PluginSectionId, item: PluginItemState): RecordingRow {
      return checkNotNull(activeRows[PluginOccurrenceId(sectionId, item.pluginId)])
    }
  }

  private class RecordingRow : PluginRow {
    override val component: JComponent = JPanel().apply {
      preferredSize = Dimension(200, ROW_HEIGHT)
      minimumSize = Dimension(0, 0)
    }
    var selected = false
      private set
    var closeCount = 0
      private set

    fun setHeight(height: Int) {
      component.preferredSize = Dimension(200, height)
      component.minimumSize = component.preferredSize
    }

    override fun renderSelection(selected: Boolean) {
      this.selected = selected
    }

    override fun close() {
      closeCount++
    }
  }

  private class RecordingDetailsPresenter : PluginDetailsPresenter {
    override val component: JComponent = JPanel()
    var mode: PluginDetailsMode? = null
      private set
    var selection: List<PluginDetailsSelection> = emptyList()
      private set
    val releasedOccurrences = ArrayList<PluginOccurrenceId>()
    val releaseCloseCounts = ArrayList<Int>()
    var closeCount = 0
      private set
    var selectedRowCloseCountAtClose: Int? = null
      private set
    private var closed = false

    override fun render(mode: PluginDetailsMode, selection: List<PluginDetailsSelection>) {
      this.mode = mode
      this.selection = selection
    }

    override fun beforeRowRelease(occurrenceId: PluginOccurrenceId, row: PluginRow) {
      if (closed) return
      check(row is RecordingRow)
      releasedOccurrences.add(occurrenceId)
      releaseCloseCounts.add(row.closeCount)
    }

    override fun close() {
      if (closed) return
      closed = true
      closeCount++
      selectedRowCloseCountAtClose = (selection.lastOrNull()?.row as? RecordingRow)?.closeCount
    }
  }

}
