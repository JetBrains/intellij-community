// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerConfigurableUtils
import com.intellij.ide.projectWizard.NewProjectWizardCollector.logInstallPluginDialogShowed
import com.intellij.ide.projectWizard.NewProjectWizardCollector.logInstallPluginPopupShowed
import com.intellij.ide.projectWizard.NewProjectWizardCollector.logSearchChanged
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.observable.util.whenTextChanged
import com.intellij.openapi.observable.util.whenTextChangedFromUi
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListItemDescriptor
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
import com.intellij.ui.SingleSelectionModel
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Graphics
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.AbstractListModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * The section of [ProjectTypeList] that holds an item. The list shows the sections in the order of this enum.
 */
@ApiStatus.Internal
enum class ProjectTypeListSection {
  /** The language generators and the other generator-based wizards. */
  NEW_PROJECT,

  /** The module builders and the project templates of the plugins. */
  GENERATORS,

  /** The project templates that the user saved. */
  TEMPLATES,
}

/**
 * An item of [ProjectTypeList].
 */
@ApiStatus.Internal
interface ProjectTypeListItem {
  /** The id that the list stores to restore the selection in the next wizard, or `null` when the item has no id. */
  val id: String?

  val name: @NlsSafe String

  val icon: Icon?

  /** A promo item shows a lock icon. */
  val isPromo: Boolean
    get() = false

  val section: ProjectTypeListSection
}

/**
 * The project type tray of the new project wizard: a search field, the item list and the link that suggests language plugins.
 *
 * The list sorts the items of each section with [comparator].
 */
@ApiStatus.Internal
class ProjectTypeList<T : ProjectTypeListItem>(
  private val context: WizardContext,
  comparator: Comparator<in T>,
) {
  private val searchTextField: SearchTextField
  private val list: JBList<T>
  private val model: ProjectTypeListModel<T> = ProjectTypeListModel(comparator)
  private val languagePluginFooterLink: JComponent

  val component: JComponent

  val items: List<T>
    get() = model.items

  fun getSelectedItem(): T? {
    return list.getSelectedValue()
  }

  fun setSelectedItem(name: String) {
    val item = model.items.find { it.name == name } ?: return
    list.setSelectedValue(item, true)
  }

  @TestOnly
  fun getAvailableItems(): String {
    return model.items.joinToString { it.name }
  }

  fun setItems(section: ProjectTypeListSection, items: List<T>) {
    LOG.debug("Project type items of $section: $items")
    model.setItems(section, items)
  }

  /**
   * Adds [item] to its section and selects it.
   */
  fun addItem(item: T) {
    LOG.debug("Project type item added: $item")
    list.selectedIndex = model.addItem(item)
  }

  fun removeItem(section: ProjectTypeListSection, name: String) {
    LOG.debug("Project type item removed: $name")
    model.removeItem(section, name)
    list.selectedIndex = 0
  }

  fun whenItemSelected(action: Consumer<in T>) {
    list.addListSelectionListener { event ->
      if (!event.valueIsAdjusting) {
        val item = getSelectedItem()
        if (item != null) {
          action.accept(item)
        }
      }
    }
  }

  /**
   * Selects the item that the user selected in the previous wizard, or the first item.
   * Then it stores each new selection for the next wizard.
   */
  fun restoreSelection() {
    whenItemSelected { item ->
      PropertiesComponent.getInstance().setValue(PROJECT_WIZARD_GROUP, item.id)
      LOG.debug("Stored selection groupId=${item.id}", Throwable())
    }
    val id = PropertiesComponent.getInstance().getValue(PROJECT_WIZARD_GROUP)
    LOG.debug("Restored selection groupId=$id")

    val item = model.items.find { it.id == id }
    if (item != null) {
      list.setSelectedValue(item, true)
    }
    else {
      list.selectedIndex = 0
    }
  }

  fun installFilteringListModel(namer: Function<in T, String>, showEmptyStatus: Runnable) {
    val speedSearch = SpeedSearch()
    val filteringListModel = NameFilteringListModel(list.model, namer::apply, speedSearch::shouldBeShowing, speedSearch::getFilter)

    val modelSize = list.model.size
    val selectedIndex = list.selectedIndex
    list.setModel(filteringListModel)
    if (list.model.size == modelSize) {
      list.selectedIndex = selectedIndex
    }

    searchTextField.whenTextChanged {
      speedSearch.updatePattern(searchTextField.text)
      filteringListModel.refilter()
      list.selectedIndex = 0
      if (filteringListModel.size == 0) {
        showEmptyStatus.run()
      }
    }
  }

  private fun showInstallPluginDialog(plugin: WizardPlugin) {
    logInstallPluginDialogShowed(context, plugin.name)
    installAndEnable(null, setOf(PluginId.getId(plugin.id)), true, true, null) { }
  }

  private fun showInstallPluginDialog() {
    logInstallPluginDialogShowed(context)
    PluginManagerConfigurableUtils.showInstallPluginDialog(component, searchTextField.text)
  }

  private fun showInstallPluginPopup() {
    logInstallPluginPopupShowed(context)
    val installedLanguagePlugins = PluginManager.getPlugins().map { it.pluginId.idString }.toSet()
    val additionalLanguagePlugins = getAdditionalWizardPlugins().filter { it.id !in installedLanguagePlugins }
    if (additionalLanguagePlugins.isEmpty()) {
      showInstallPluginDialog()
    }
    else {
      showInstallPluginPopup(additionalLanguagePlugins)
    }
  }

  private fun showInstallPluginPopup(additionalWizardPlugins: List<WizardPlugin>) {
    val link = ActionLink(UIBundle.message("newProjectWizard.ProjectTypeStep.InstallPluginAction.advertiser")) { showInstallPluginDialog() }
    link.toolTipText = null

    val languagePluginAdvertiserLink = JBPanel<JBPanel<*>>(BorderLayout())
    languagePluginAdvertiserLink.border = JBUI.CurrentTheme.Advertiser.border()
    languagePluginAdvertiserLink.foreground = JBUI.CurrentTheme.Advertiser.foreground()
    languagePluginAdvertiserLink.background = JBUI.CurrentTheme.Advertiser.background()
    languagePluginAdvertiserLink.withMinimumWidth(JBUI.scale(220))
    languagePluginAdvertiserLink.withPreferredWidth(JBUI.scale(220))
    languagePluginAdvertiserLink.add(link, BorderLayout.WEST)

    return JBPopupFactory.getInstance()
      .createPopupChooserBuilder(additionalWizardPlugins)
      .setRenderer(LanguagePluginRenderer())
      .setTitle(UIBundle.message("newProjectWizard.ProjectTypeStep.InstallPluginAction.title"))
      .setAutoselectOnMouseMove(true)
      .setNamerForFiltering { it.name }
      .setMovable(true)
      .setAdvertiser(languagePluginAdvertiserLink)
      .setResizable(false)
      .setRequestFocus(true)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setItemChosenCallback(::showInstallPluginDialog)
      .createPopup()
      .show(RelativePoint.getSouthWestOf(languagePluginFooterLink))
  }

  init {
    list = JBList(model)
    list.setSelectionModel(SingleSelectionModel())
    list.setCellRenderer(ProjectTypeListRenderer(context, model))
    list.accessibleContext.accessibleName = UIBundle.message("newProjectWizard.ProjectTypeStep.list.accessible.name")

    searchTextField = SearchTextField(false)
    searchTextField.textEditor.border = JBUI.Borders.empty(2, 5, 2, 0)
    searchTextField.whenTextChangedFromUi {
      logSearchChanged(context, searchTextField.text.length, list.model.size)
    }

    list.emptyText.setText(IdeBundle.message("plugins.configurable.nothing.found"))
    list.emptyText.appendSecondaryText(IdeBundle.message("plugins.configurable.search.in.marketplace"), LINK_PLAIN_ATTRIBUTES) {
      showInstallPluginDialog()
    }

    val scrollPane = JBScrollPane(list)
    scrollPane.border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0)

    val link = ActionLink(UIBundle.message("newProjectWizard.ProjectTypeStep.InstallPluginAction.name")) { showInstallPluginPopup() }
    link.toolTipText = UIBundle.message("newProjectWizard.ProjectTypeStep.InstallPluginAction.description")

    languagePluginFooterLink = JBPanel<JBPanel<*>>(BorderLayout())
    languagePluginFooterLink.border = JBUI.CurrentTheme.Advertiser.border()
    languagePluginFooterLink.withMinimumHeight(JBUI.scale(40))
    languagePluginFooterLink.withPreferredHeight(JBUI.scale(40))
    languagePluginFooterLink.add(link, BorderLayout.WEST)

    component = JPanel(BorderLayout())
    component.border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0)
    component.add(searchTextField, BorderLayout.NORTH)
    component.add(scrollPane, BorderLayout.CENTER)
    component.add(languagePluginFooterLink, BorderLayout.SOUTH)
  }

  private class ProjectTypeListModel<T : ProjectTypeListItem>(
    private val comparator: Comparator<in T>,
  ) : AbstractListModel<T>() {

    private val sections: Map<ProjectTypeListSection, MutableList<T>> = ProjectTypeListSection.entries.associateWith { ArrayList() }

    val items: List<T>
      get() = ProjectTypeListSection.entries.flatMap { sections.getValue(it) }

    override fun getSize(): Int {
      return items.size
    }

    override fun getElementAt(index: Int): T {
      return items[index]
    }

    fun setItems(section: ProjectTypeListSection, items: List<T>) {
      val collection = sections.getValue(section)
      val offset = getOffset(section)
      collection.clear()
      collection.addAll(items)
      collection.sortWith(comparator)
      fireContentsChanged(this, offset, offset + collection.size - 1)
    }

    fun addItem(item: T): Int {
      val collection = sections.getValue(item.section)
      val offset = getOffset(item.section)
      var index = collection.indexOfFirst { comparator.compare(it, item) >= 0 }
      if (index < 0) index = 0
      collection.add(index, item)
      fireIntervalAdded(this, offset + index, offset + index)
      return offset + index
    }

    fun removeItem(section: ProjectTypeListSection, name: String) {
      val collection = sections.getValue(section)
      val offset = getOffset(section)
      val index = collection.indexOfFirst { name == it.name }
      if (index >= 0) {
        collection.removeAt(index)
        fireIntervalRemoved(this, offset + index, offset + index)
      }
    }

    private fun getOffset(section: ProjectTypeListSection): Int {
      return ProjectTypeListSection.entries
        .takeWhile { it != section }
        .sumOf { sections.getValue(it).size }
    }
  }

  private class ProjectTypeListRenderer<T : ProjectTypeListItem>(
    context: WizardContext,
    model: ProjectTypeListModel<T>,
  ) : GroupedItemsListRenderer<T>(
    ProjectTypeListItemDescriptor(context, model)
  ) {

    override fun customizeComponent(
      list: JList<out T>,
      value: T,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean,
    ) {
      mySeparatorComponent.border = when (index == 0) {
        true -> JBUI.Borders.empty(5, 8, 5, 0)
        else -> JBUI.Borders.empty(20, 8, 5, 0)
      }
      mySeparatorComponent.setCaptionCentered(false)
      mySeparatorComponent.font = JBFont.label().deriveFont(JBUI.scale(12))

      myNextStepLabel.icon = when (value.isPromo) {
        true -> AllIcons.Ultimate.Lock
        else -> null
      }

      myTextLabel.border = JBUI.Borders.empty(5, 0)
    }

    @Suppress("DEPRECATION")
    override fun createSeparator(): SeparatorWithText {
      return object : SeparatorWithText() {
        override fun paintLinePart(g: Graphics, xMin: Int, xMax: Int, hGap: Int, y: Int) {}
      }
    }
  }

  private class ProjectTypeListItemDescriptor<T : ProjectTypeListItem>(
    private val context: WizardContext,
    private val model: ProjectTypeListModel<T>,
  ) : ListItemDescriptorAdapter<T>() {

    override fun getTextFor(value: T): String {
      return value.name
    }

    override fun getIconFor(value: T): Icon {
      return value.icon ?: EmptyIcon.ICON_16
    }

    override fun getCaptionAboveOf(value: T): String {
      return when (value.section) {
        ProjectTypeListSection.NEW_PROJECT -> UIBundle.message("list.caption.group.newProject", context.isCreatingNewProjectInt())
        ProjectTypeListSection.GENERATORS -> UIBundle.message("list.caption.group.generators")
        ProjectTypeListSection.TEMPLATES -> UIBundle.message("list.caption.group.templates")
      }
    }

    override fun hasSeparatorAboveOf(value: T): Boolean {
      val items = model.items
      val index = items.indexOf(value)
      if (index < 0) return false
      if (index == 0) return true
      return items[index - 1].section != value.section
    }
  }

  private class LanguagePluginRenderer : GroupedItemsListRenderer<WizardPlugin>(LanguagePluginRendererDescriptor()) {

    override fun customizeComponent(
      list: JList<out WizardPlugin>,
      value: WizardPlugin,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean,
    ) {
      myTextLabel.border = JBUI.Borders.empty(5, 0)
    }
  }

  private class LanguagePluginRendererDescriptor : ListItemDescriptor<WizardPlugin> {
    override fun getTextFor(value: WizardPlugin): String = value.name
    override fun getTooltipFor(value: WizardPlugin): String? = null
    override fun getIconFor(value: WizardPlugin): Icon = value.icon
    override fun hasSeparatorAboveOf(value: WizardPlugin): Boolean = false
    override fun getCaptionAboveOf(value: WizardPlugin): String? = null
  }
}

private class WizardPlugin(
  val id: String,
  val name: @NlsSafe String,
  val icon: Icon,
)

private const val PROJECT_WIZARD_GROUP = "project.wizard.group"
private val LOG = Logger.getInstance("com.intellij.ide.projectWizard.ProjectTypeStep")

private fun getAdditionalWizardPlugins(): List<WizardPlugin> = buildList {
  if (PlatformUtils.isIdeaCommunity()) {
    add(WizardPlugin("PythonCore", Language.PYTHON, AllIcons.Language.Python))
    add(WizardPlugin("DevKit", "Plugin DevKit", AllIcons.Nodes.Plugin))
    add(WizardPlugin("org.intellij.scala", Language.SCALA, AllIcons.Language.Scala))
  }
  else {
    add(WizardPlugin("org.jetbrains.plugins.go", Language.GO, AllIcons.Language.GO))
    add(WizardPlugin("com.jetbrains.php", Language.PHP, AllIcons.Language.Php))
    add(WizardPlugin("Pythonid", Language.PYTHON, AllIcons.Language.Python))
    add(WizardPlugin("DevKit", "Plugin DevKit", AllIcons.Nodes.Plugin))
    add(WizardPlugin("org.jetbrains.plugins.ruby", Language.RUBY, AllIcons.Language.Ruby))
    add(WizardPlugin("com.jetbrains.rust", Language.RUST, AllIcons.Language.Rust))
    add(WizardPlugin("org.intellij.scala", Language.SCALA, AllIcons.Language.Scala))
  }
}

@ApiStatus.Internal
@TestOnly
fun resetStoredProjectTypeSelectionForTests() {
  PropertiesComponent.getInstance().setValue(PROJECT_WIZARD_GROUP, null)
}
